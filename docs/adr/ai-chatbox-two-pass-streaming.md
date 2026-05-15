# ADR: Two-Pass AI Chatbox With Validated Snapshot And Streaming Reply

Status: Implemented in backend, frontend-compatible fallback added

Date: 2026-05-14

Related document: `docs/ai-chatbox-architecture.md`

## Context

The current AI chatbox already moved away from parsing product IDs from free text. Backend now uses RAG candidates, structured JSON, session state, and post-flight guards to prevent invalid product cards.

That approach is practical, but it still has one important limitation: the AI reply text and the UI action payload are produced in one pass. If backend guard removes or changes items after the AI has already written the reply, the UI can show a different product list than the text describes.

Example:

```text
AI reply: "Mình đã thêm xà lách vào danh sách."
Backend guard: xà lách vừa hết hàng, removed from proposedItems.
UI: no xà lách card.
```

This ADR proposes a v2 architecture that separates validated shopping actions from final natural-language generation.

## Decision

Use a Two-Pass design for AI chat responses that can create or modify shopping actions.

Pass 1 is synchronous and data-authoritative:

1. Save user message.
2. Retrieve candidates from Neo4j/JPA.
3. Ask AI for structured intent and candidate product choices.
4. Run backend guard.
5. Save a validated action snapshot.
6. Save an assistant placeholder message.
7. Return UI actions immediately.

Pass 2 is asynchronous and text-authoritative:

1. Run only after Pass 1 transaction commits.
2. Read the validated snapshot from PostgreSQL.
3. Ask AI to write the final reply based only on the validated snapshot.
4. Stream final reply to the frontend.
5. Persist final reply and status.

Pass 2 must not be submitted directly inside the transaction of `POST /chat`.

The trigger pattern is:

```text
POST /chat
  -> Transaction 1:
      save user message
      retrieve/validate candidates
      save assistant placeholder
      save validated action snapshot
      publish Pass2RequestedEvent
  -> COMMIT

AFTER_COMMIT listener:
  -> submit Pass2StreamJob to bounded executor

Pass2StreamJob:
  -> load snapshot from DB
  -> stream AI final reply through SSE
  -> Transaction 2:
      update finalReply/status
```

## Why This Decision

### Text And UI Stay In Sync

The final user-visible reply is generated from backend-validated data, not from unvalidated model output. If stock or active checks remove a product, the final reply can say that clearly.

### Backend Remains Source Of Truth

The LLM can suggest. It cannot directly create the final shopping action.

The final payload must come from:

```text
LLM proposed items
  -> candidate scope filter
  -> product active check
  -> variant active check
  -> stock check
  -> validated snapshot
```

### DB Connections Are Protected

OpenRouter calls and SSE streaming can take seconds. They must not run inside a database transaction.

Pass 2 uses short transactions only for status updates:

```text
PENDING_PASS2 -> STREAMING
STREAMING -> DONE
STREAMING -> FALLBACK
```

### Spring Transaction Boundaries Are Explicit

`@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` makes the intent explicit:

```text
Only start Pass 2 after Pass 1 data is committed.
```

This avoids a worker thread trying to read an assistant message or snapshot that has not been committed yet.

## Proposed Backend Components

### Pass2RequestedEvent

Event should carry only IDs, not JPA entities.

```java
public record Pass2RequestedEvent(
        Long aiMessageId,
        Long userId
) {}
```

Do not pass `ChatMessage` or other Hibernate entities into async code. Query fresh data in the worker thread.

### Pass2EventListener

Listener only submits the job. It does not call OpenRouter.

```java
@Component
@RequiredArgsConstructor
public class Pass2EventListener {

    private final AiPass2StreamService aiPass2StreamService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPass2Requested(Pass2RequestedEvent event) {
        aiPass2StreamService.submitPass2Job(event.aiMessageId(), event.userId());
    }
}
```

### AiPass2StreamService

Pass 2 runs in a bounded executor.

```java
@Service
@RequiredArgsConstructor
public class AiPass2StreamService {

    private final Executor aiPass2Executor;
    private final ChatMessageRepository chatMessageRepository;
    private final SseStreamRegistry streamRegistry;
    private final OpenRouterClient openRouterClient;
    private final TransactionTemplate transactionTemplate;

    public void submitPass2Job(Long aiMessageId, Long userId) {
        aiPass2Executor.execute(() -> runPass2(aiMessageId, userId));
    }

    private void runPass2(Long aiMessageId, Long userId) {
        ChatMessage message = chatMessageRepository
                .findByIdAndUserId(aiMessageId, userId)
                .orElseThrow();

        markStreaming(aiMessageId);

        StringBuilder finalReply = new StringBuilder();

        try {
            Pass2Payload payload = buildPayloadFromSnapshot(message);

            openRouterClient.streamPass2(payload, delta -> {
                finalReply.append(delta);
                streamRegistry.emitDelta(aiMessageId, delta);
            });

            markDone(aiMessageId, finalReply.toString());
            streamRegistry.emitDone(aiMessageId, finalReply.toString(), false);
        } catch (Exception ex) {
            String fallback = message.getFallbackReply();
            markFallback(aiMessageId, fallback, ex);
            streamRegistry.emitDelta(aiMessageId, fallback);
            streamRegistry.emitWarning(aiMessageId, "AI_REPLY_FALLBACK");
            streamRegistry.emitDone(aiMessageId, fallback, true);
        }
    }
}
```

The implementation should use `TransactionTemplate` for the small `markStreaming`, `markDone`, and `markFallback` updates.

## Data Model Additions

`chat_messages` should store enough information to recover and replay a response.

Suggested columns:

```text
status                  VARCHAR(40)
fallback_reply          TEXT
final_reply             TEXT
validated_action_snapshot JSONB or TEXT
reply_started_at        TIMESTAMP
reply_completed_at      TIMESTAMP
reply_error_code        VARCHAR(80)
reply_error_message     TEXT
```

Suggested statuses:

```text
PENDING_PASS2
STREAMING
DONE
FALLBACK
FAILED
```

`validated_action_snapshot` should store the backend-approved payload, not raw model output.

Example:

```json
{
  "intent": "SHOPPING_LIST_CREATE",
  "items": [
    {
      "productId": 145,
      "quantity": 1,
      "productName": "Cá hồi",
      "reason": "Nguồn protein chính",
      "stockAtValidation": 12
    }
  ],
  "removedItems": [
    {
      "productId": 100,
      "reason": "OUT_OF_STOCK"
    }
  ],
  "validatedAt": "2026-05-14T17:43:40"
}
```

## API Contract

### POST `/api/v1/ai/chat`

Request:

```json
{
  "message": "Ok tạo danh sách mua sắm",
  "sessionId": 123
}
```

Response:

```json
{
  "sessionId": 123,
  "aiMessageId": "456",
  "replyStatus": "STREAMING",
  "fallbackReply": "Mình đã chuẩn bị xong danh sách bên dưới cho bạn nhé.",
  "streamUrl": "/api/v1/ai/chat/messages/456/stream",
  "uiActions": [
    {
      "type": "PROPOSED_ITEM",
      "productId": 145,
      "quantity": 1
    }
  ]
}
```

The UI can render product cards immediately from `uiActions`, while final reply text streams separately.

### GET `/api/v1/ai/chat/messages/{messageId}/stream`

SSE events:

```text
event: reply_delta
data: {"text":"Mình đã tạo danh sách..."}

event: warning
data: {"code":"AI_REPLY_FALLBACK"}

event: done
data: {"finalReply":"...", "fallback":false}
```

## Reconnect And Replay

If the client reconnects while status is `STREAMING`, the server should squash buffered deltas into one replay event.

Do not replay dozens of tiny delta events.

Preferred behavior:

```java
String bufferedText = String.join("", state.getDeltaBuffer());

if (!bufferedText.isBlank()) {
    send(emitter, "reply_delta", Map.of("text", bufferedText));
}
```

Then continue streaming new deltas normally.

This reduces React Native re-renders and SSE overhead.

If the in-memory stream state is gone, replay from DB:

1. If `DONE` or `FALLBACK`: emit final reply and complete.
2. If `PENDING_PASS2` or `STREAMING` but no active worker: return current fallback/status or enqueue recovery job.

## Bounded Executor Requirements

Pass 2 must run in a bounded executor, not an unbounded async pool.

Suggested config:

```text
corePoolSize = 4
maxPoolSize = 8
queueCapacity = 100
rejectionPolicy = mark message as FALLBACK or return BUSY status
```

Why:

1. Prevent too many OpenRouter calls in parallel.
2. Protect CPU and memory.
3. Avoid exhausting DB connection pool through status updates.
4. Make overload behavior explicit.

## Spring Pitfalls To Avoid

### No Broad Class-Level Transaction

Avoid:

```java
@Service
@Transactional
public class ChatAssistantService {
}
```

Prefer method-level transactions only where needed.

### No Self-Invocation For Async Or Transactional Methods

Avoid:

```java
this.runPass2Async();
```

If the method relies on Spring proxy behavior, place it in another bean.

### No JPA Entity In Async Event

Avoid:

```java
new Pass2RequestedEvent(chatMessage)
```

Prefer:

```java
new Pass2RequestedEvent(chatMessage.getId(), user.getId())
```

## Shopping Intent Guard Rule

Specific product requests override the meal-quality denylist. Meal/diet guards are only applied when the user asks for meal planning, healthy eating, weight loss, or ingredient-based cooking. Direct product shopping requests are validated by stock and product status, not by meal suitability.

Expected matrix:

```text
"Tạo danh sách mua sắm cho cà phê và sữa hạnh nhân"
  -> DIRECT_PRODUCT_SHOPPING
  -> return coffee and almond milk product cards if active and in stock

"Tạo danh sách mua sắm cho dầu ăn và hạt nêm"
  -> DIRECT_PRODUCT_SHOPPING
  -> do not block with meal-quality denylist

"Tạo danh sách mua sắm cho nước giặt"
  -> DIRECT_PRODUCT_SHOPPING
  -> direct product shopping is not constrained to food categories

"Gợi ý món ăn với cà phê"
  -> not DIRECT_PRODUCT_SHOPPING
  -> route through meal/AI flow; meal context may still reject coffee

"Tạo danh sách nguyên liệu cho món gà kho"
  -> not DIRECT_PRODUCT_SHOPPING
  -> route through ingredient/meal shopping flow because it contains ingredient/meal cues

"Tạo danh sách mua sắm cho món salad healthy"
  -> not DIRECT_PRODUCT_SHOPPING
  -> route through meal flow because it contains meal and healthy cues
```

Hybrid requests such as `Tạo danh sách mua sắm cho món salad, thêm cà phê nữa` are intentionally not split in this version. A future `MEAL_SHOPPING_WITH_EXTRA_PRODUCTS` intent can apply meal guards to meal ingredients while validating explicit extra products only by active status and stock.

### Meal Option Ingredient Mapping Matrix

When the user selects a numbered meal option, the backend must map only the frozen ingredients stored in `lastMealOptions` for that option. It must not re-infer the shopping list from broad meal candidates or from AI text.

```text
Previous options:
1. Ức gà áp chảo + măng tây + khoai lang
2. Đậu hũ sốt nấm + su hào luộc + yến mạch mặn
3. Trứng luộc + salad rau xanh + táo

"số 1"
  -> read lastMealOptions optionNo = 1
  -> map only ức gà / măng tây / khoai lang if active and in stock
  -> must not include cá diêu hồng
  -> must not include đậu cove

"chọn số 2"
  -> read lastMealOptions optionNo = 2
  -> map only đậu hũ / nấm / su hào / yến mạch if active and in stock
  -> must not include ingredients from option 1

Brand-heavy product:
  Name: "CP Fresh 500g"
  Category: "Thịt gà"
  Description: "Ức gà phi lê"
  Ingredient: "ức gà"
  -> must match because product search text includes category and description

"Tạo danh sách mua sắm cho ức gà măng tây khoai lang"
  -> DIRECT_MEAL_SHOPPING_LIST
  -> map exactly the explicit meal ingredients

"Tạo danh sách mua sắm cho cà phê và sữa hạnh nhân"
  -> DIRECT_PRODUCT_SHOPPING
  -> do not apply meal-quality denylist
```

Expected assertions:

```text
Assert proposedItems.productIds correspond only to selected.ingredients.
Assert no product outside selected.ingredients can be added even if it has high mealCandidateScore.
```

## Consequences

### Pros

1. Stronger text/UI consistency.
2. Backend owns validated shopping actions.
3. No long DB transaction during AI calls.
4. Better resilience to AI provider latency/failure.
5. Clear retry/replay path.
6. Cleaner Spring transaction boundaries.

### Cons

1. More moving parts than current one-pass flow.
2. Requires SSE or another streaming channel.
3. Requires new message statuses and snapshot persistence.
4. Frontend must handle `replyStatus`, `fallbackReply`, and stream lifecycle.
5. Multi-instance deployment needs shared stream state or Redis Pub/Sub.

## Multi-Instance Note

The first implementation can keep stream state in memory if the backend runs as one instance.

For multi-instance deployment, use one of:

1. Sticky sessions at load balancer.
2. Redis Pub/Sub for stream events.
3. Persisted polling fallback from DB.

Without this, a reconnect may hit another instance that does not have the in-memory `SseEmitter` state.

## Implementation Checklist

1. Add chat message status fields and validated snapshot fields.
2. Add `Pass2RequestedEvent`.
3. Add `Pass2EventListener` with `@TransactionalEventListener(AFTER_COMMIT)`.
4. Add bounded `aiPass2Executor`.
5. Add `AiPass2StreamService`.
6. Add `SseStreamRegistry`.
7. Add stream endpoint.
8. Change `POST /chat` response contract for streaming messages.
9. Update frontend to render UI actions immediately and subscribe to stream.
10. Add replay/reconnect behavior.
11. Add fallback behavior when executor rejects or AI provider fails.
12. Add tests for transaction boundary, fallback, reconnect, and stock-change cases.

## Open Questions

1. Should all chat responses use Pass 2, or only shopping-action responses?
2. Should `POST /chat` still return a full non-streaming reply for simple questions?
3. How long should stream buffers live in memory?
4. Should validated snapshot store product names/prices at validation time?
5. Should frontend poll final message state if SSE is unavailable?
6. What overload response should be returned when the Pass 2 executor queue is full?
7. How should cancellation work if the user leaves the chat screen?

---

## Final Implementation Notes

The Two-pass streaming architecture has been hardened with ownership validation, bounded async execution, post-commit Pass 2 triggering, SSE lifecycle cleanup, replay-buffer squashing, fallback-on-overload behavior, and PostgreSQL migration support. The stream endpoint always validates `messageId` against the authenticated `userId`, and SSE state is cleaned both by emitter lifecycle callbacks and scheduled cleanup (with a 10-minute TTL for stuck states) to prevent memory leaks.
