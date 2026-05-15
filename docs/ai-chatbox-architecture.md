# AI Chatbox Architecture

Tài liệu này mô tả hướng build chức năng chatbox AI cho SmartGrocery: cách backend dùng AI, API, Neo4j, PostgreSQL/JPA, session state và guard để trả lời người dùng, gợi ý sản phẩm, rồi tạo danh sách mua sắm có thể render thành card thật trên UI.

Mục tiêu của tài liệu là làm rõ tư duy thiết kế để có thể review, phản biện và cải tiến tiếp.

## 1. Mục Tiêu Chức Năng

Chatbox không chỉ là một ô hỏi đáp. Nó cần làm 4 việc chính:

1. Hiểu intent người dùng: hỏi món ăn, hỏi sản phẩm, yêu cầu tạo danh sách mua sắm, sửa danh sách, hỏi sức khỏe/dinh dưỡng.
2. Trả lời bằng text tự nhiên, dễ hiểu.
3. Khi phù hợp, trả về `proposedItems` hoặc `recommendedProductIds` để frontend render card sản phẩm thật.
4. Không bao giờ để LLM tự ý quyết định dữ liệu mua hàng cuối cùng nếu backend chưa validate.

Điểm quan trọng: text của AI chỉ để giao tiếp. Dữ liệu để thêm vào giỏ hàng phải đi qua backend guard.

## 2. Vấn Đề Đã Gặp

Các lỗi chính trong flow cũ:

1. AI nói có ID thật trong thought/text, nhưng JSON `proposedItems` lại rỗng hoặc chứa `productId = 0`.
2. Frontend chỉ hiển thị danh sách sản phẩm dạng text in đậm, không render card sản phẩm thật.
3. Backend guard xóa hết item vì ID không hợp lệ:

```text
Backend Guard: Removing product ID 0 - product missing, inactive, or out of stock
```

4. Feedback API có thể trả `400`, gây log:

```text
POST /ai/chat/feedback 400
```

5. Chat API có thể trả `500`, nhất là khi AI provider timeout/throw hoặc prompt quá nặng:

```text
POST /ai/chat 500
```

6. Prompt cũ có nguy cơ nhồi quá nhiều inventory vào context, làm chậm, tốn token, dễ lỗi “lost in the middle”.

## 3. Nguyên Tắc Kiến Trúc

Hướng hiện tại không tin tuyệt đối vào output của LLM.

Backend giữ quyền quyết định cuối cùng theo các nguyên tắc:

1. LLM được phép gợi ý, giải thích, chọn trong candidate set.
2. Backend retrieve sản phẩm trước, chỉ đưa context hẹp cho LLM.
3. Backend validate lại ID, trạng thái active, tồn kho.
4. Backend không parse ID từ `thoughtProcess` hoặc text trả lời.
5. Session state lưu candidate IDs của lượt trước để hỗ trợ flow nhiều lượt.
6. Feedback và lỗi AI provider không được làm crash trải nghiệm chat.

## 4. Kiến Trúc Tổng Quan

Flow chính của `POST /api/v1/ai/chat`:

```text
Frontend
  -> POST /api/v1/ai/chat
Backend Controller
  -> validate message/sessionId
ChatAssistantService
  -> load/create chat session
  -> save user message
  -> analyze user motivation/profile/cart
  -> retrieve products from Neo4j/JPA
  -> build compact prompt
  -> call AI provider
  -> parse structured JSON
  -> enforce backend candidate scope
  -> ensure shopping-list proposedItems if needed
  -> filter out inactive/out-of-stock/pantry staples
  -> update sessionContext
  -> save assistant message
  -> return ChatResponse
Frontend
  -> render reply text
  -> render product cards from proposedItems/recommendedProductIds
```

## 5. Các Thành Phần Chính

### 5.1 Frontend

Frontend gọi:

```text
POST /api/v1/ai/chat
POST /api/v1/ai/chat/feedback
```

Response quan trọng:

```json
{
  "sessionId": 123,
  "aiMessageId": "456",
  "reply": "Text AI trả lời",
  "recommendedProductIds": [145, 100, 171],
  "proposedItems": [
    {
      "productId": 145,
      "quantity": 1,
      "reason": "..."
    }
  ],
  "trustScore": 80
}
```

Frontend nên render card theo thứ tự ưu tiên:

1. Nếu `proposedItems` có item: render list sản phẩm có quantity, nút thêm giỏ hàng.
2. Nếu `proposedItems` rỗng nhưng `recommendedProductIds` có ID: render card recommendation.
3. Nếu cả hai rỗng: chỉ render text.

### 5.2 Controller

File chính:

```text
src/main/java/com/smartgrocery/backend/controller/AiChatController.java
```

Nhiệm vụ:

1. Lấy user hiện tại từ security context.
2. Validate `message`.
3. Parse `sessionId` an toàn.
4. Gọi `ChatAssistantService.processChat`.
5. Feedback endpoint xử lý idempotent, không để feedback lỗi làm app báo lỗi mạng.

Lý do feedback nên idempotent:

Feedback là dữ liệu phụ trợ. Nếu message ID cũ, ID tạm, hoặc feedback type không hợp lệ, chatbox không nên hiện lỗi lớn cho user. Backend có thể trả:

```json
{ "status": "ignored" }
```

thay vì `400`.

### 5.3 ChatAssistantService

File chính:

```text
src/main/java/com/smartgrocery/backend/service/ai/ChatAssistantService.java
```

Đây là orchestration layer. Service này không nên chỉ “forward prompt sang AI”, mà phải làm chủ state và dữ liệu.

Nhiệm vụ:

1. Chuẩn bị session.
2. Lưu user message.
3. Load nutrition profile, cart context, conflict context.
4. Retrieve sản phẩm liên quan.
5. Build prompt.
6. Gọi OpenRouter/AI provider.
7. Parse response JSON.
8. Guard output.
9. Update session context.
10. Lưu assistant message.

## 6. RAG Với Neo4j Và JPA

### 6.1 Vì Sao Không Nhồi Toàn Bộ Inventory Vào Prompt?

Nhồi toàn bộ kho vào prompt có các vấn đề:

1. Chậm.
2. Tốn token.
3. Dễ timeout.
4. Khi sản phẩm lên vài nghìn item, LLM không đọc hiệu quả.
5. LLM có thể chọn nhầm ID do context quá dài.

Do đó hướng hiện tại là retrieve trước, generate sau.

### 6.2 Vai Trò Neo4j

Neo4j dùng cho product discovery và semantic relation:

1. Full-text search theo câu hỏi.
2. Tìm sản phẩm liên quan theo category/ingredient/similarity.
3. Tìm sản phẩm thay thế.
4. Sau này có thể dùng user preference graph, dietary condition graph.

Ví dụ user hỏi:

```text
Gợi ý bữa tối giảm cân, tôi không ăn tôm
```

Backend sẽ query Neo4j để lấy top sản phẩm liên quan như cá hồi, rau, giấm táo, dầu hướng dương, bắp cải tím.

Chỉ nhóm candidate này được đưa vào prompt.

### 6.3 Vai Trò PostgreSQL/JPA

PostgreSQL là source of truth cho dữ liệu giao dịch:

1. Product active/inactive.
2. Variant active/inactive.
3. Inventory stock.
4. Cart.
5. Order.
6. Chat session/message.
7. User nutrition profile.

Neo4j có thể giúp search tốt hơn, nhưng trước khi render card hoặc thêm giỏ hàng, backend phải validate qua JPA/PostgreSQL.

## 7. Prompt Design

Prompt hiện tại nên ép AI trả structured output, ví dụ:

```json
{
  "reply": "Câu trả lời cho người dùng",
  "intentDetected": "SHOPPING_LIST_CREATE",
  "recommendedProductIds": [145, 100, 171],
  "proposedItems": [
    {
      "productId": 145,
      "quantity": 1,
      "reason": "Nguồn protein chính cho bữa tối"
    }
  ],
  "trustScore": 82,
  "thoughtProcess": "..."
}
```

Quy tắc quan trọng:

1. Không đưa ID sản phẩm vào text hiển thị.
2. Không parse ID từ `thoughtProcess`.
3. ID chỉ có giá trị khi nằm trong JSON field có cấu trúc.
4. Backend vẫn validate ID sau khi parse.

## 8. Candidate Scope Và Guard

Backend tạo một candidate scope gồm:

1. `lastShoppingCandidateIds` từ session context.
2. `recommendedProductIds` do AI trả về.
3. Product IDs từ Neo4j discovery hiện tại.

Sau đó backend lọc:

```text
final_items = LLM_proposedItems INTERSECT allowed_candidate_ids
```

Rồi tiếp tục validate:

1. productId > 0
2. Product tồn tại
3. Product active
4. Variant active
5. Có tồn kho
6. Không phải pantry staple nếu user không yêu cầu rõ

Mục tiêu là tránh các lỗi:

1. AI hallucinate ID.
2. AI chọn sản phẩm hết hàng.
3. AI chọn sản phẩm text nói một đằng, ID một nẻo.
4. AI tự thêm item không nằm trong retrieval set.

## 9. Session State

Session context lưu JSON, trong đó quan trọng nhất là:

```json
{
  "lastShoppingCandidateIds": [145, 100, 171],
  "lastShoppingCandidateUpdatedAt": "2026-05-14T17:43:40"
}
```

Flow nhiều lượt:

### Lượt 1

User:

```text
Gợi ý bữa tối giảm cân
```

Backend retrieve sản phẩm, AI trả lời món ăn, backend lưu candidate IDs:

```json
[145, 100, 171, 199]
```

### Lượt 2

User:

```text
Ok tạo danh sách mua sắm
```

Nếu AI không trả `proposedItems`, backend vẫn có thể dựng list từ `lastShoppingCandidateIds`, sau đó validate stock rồi trả card thật cho UI.

### Lượt 2 Có Sửa Đổi

User:

```text
Ok tạo danh sách đi, nhưng đổi bắp cải thành xà lách
```

Không nên bê nguyên session IDs cũ vào giỏ hàng. Với modification request, backend phải để LLM quyết định JSON cuối cùng trong scope:

```text
allowed = old_session_candidates UNION new_retrieved_candidates
final = LLM_proposedItems INTERSECT allowed
```

Như vậy AI có thể bỏ bắp cải, thêm xà lách nếu xà lách nằm trong candidate mới và còn hàng.

## 10. Vì Sao Không Parse ID Từ Text?

Không parse ID từ text vì các lý do:

### 10.1 Negative Matching

AI có thể viết:

```text
Cà chua (ID:55) có sẵn nhưng user dị ứng nên loại. Chọn bắp cải (ID:100).
```

Nếu regex bắt mọi `ID:\d+`, backend sẽ thêm cả cà chua.

### 10.2 Lệch Text Và ID

AI có thể nói:

```text
Tôi chọn cá hồi
```

nhưng JSON hoặc text lại có ID của sản phẩm khác.

Backend chỉ tin dữ liệu có cấu trúc và đã validate.

### 10.3 Thought Process Không Phải Contract

`thoughtProcess` chỉ để debug. Nó không nên là nguồn dữ liệu nghiệp vụ.

## 11. Fallback Khi AI Provider Lỗi

Nếu AI provider timeout/throw, backend không nên trả 500. Flow mới:

```text
OpenRouter lỗi
  -> log warning
  -> build fallback response
  -> nếu có discoveredProducts thì trả recommendedProductIds hợp lệ
  -> nếu user đang tạo shopping list thì dựng proposedItems từ candidate hợp lệ
  -> save assistant message
  -> return 200
```

Fallback text hiện tại có thể còn đơn giản, nhưng quan trọng là API không chết.

## 12. Feedback Loop

Feedback endpoint:

```text
POST /api/v1/ai/chat/feedback
```

Payload:

```json
{
  "messageId": 456,
  "feedbackType": "HELPFUL"
}
```

Chỉ support:

```text
HELPFUL
NOT_HELPFUL
```

Nếu feedback không hợp lệ:

```json
{ "status": "ignored" }
```

Feedback service cập nhật:

1. `ChatMessage.feedbackType`
2. satisfaction score
3. behavior mode

Điểm đã sửa: feedback không được ghi đè toàn bộ `sessionContext`, vì nếu ghi đè sẽ mất `lastShoppingCandidateIds`, làm hỏng flow tạo danh sách mua sắm sau đó.

## 13. API Contract Đề Xuất

### 13.1 Chat Request

```http
POST /api/v1/ai/chat
Content-Type: application/json
Authorization: Bearer <token>
```

```json
{
  "message": "Gợi ý bữa tối giảm cân",
  "sessionId": 123
}
```

`sessionId` optional. Nếu không có, backend lấy active session gần nhất hoặc tạo mới.

### 13.2 Chat Response

```json
{
  "sessionId": 123,
  "aiMessageId": "456",
  "reply": "Mình gợi ý cá hồi áp chảo kèm salad bắp cải tím...",
  "recommendedProductIds": [145, 100, 171],
  "proposedItems": [
    {
      "productId": 145,
      "quantity": 1,
      "reason": "Nguồn protein chính"
    },
    {
      "productId": 100,
      "quantity": 1,
      "reason": "Rau ăn kèm"
    }
  ],
  "removeVariantIds": [],
  "removeReasons": {},
  "explanations": {},
  "trustScore": 82,
  "thoughtProcess": "...",
  "intentPrediction": null,
  "expectationPrompt": null
}
```

### 13.3 Feedback Request

```http
POST /api/v1/ai/chat/feedback
```

```json
{
  "messageId": 456,
  "feedbackType": "HELPFUL"
}
```

### 13.4 Feedback Response

```json
{ "status": "ok" }
```

hoặc:

```json
{ "status": "ignored" }
```

## 14. Những Tradeoff Hiện Tại

### 14.1 Chưa Dùng Function Calling Chuẩn

Hướng tốt nhất về lâu dài là function/tool calling:

```text
AI calls suggest_products(product_ids)
Backend validates
AI writes final response based on validated result
```

Hiện tại dùng structured JSON output thay vì function calling. Cách này thực dụng hơn, dễ tích hợp với code hiện có, nhưng vẫn có rủi ro one-pass mismatch giữa text và JSON.

### 14.2 Chưa Có Two-Pass Finalization

Rủi ro:

1. AI text nói “đã thêm xà lách”.
2. Backend guard phát hiện xà lách vừa hết hàng và xóa khỏi card.
3. Text và UI lệch nhau.

Giải pháp tốt hơn:

```text
Pass 1: AI đề xuất ý định và sản phẩm
Backend validate
Pass 2: AI viết câu trả lời cuối cùng dựa trên danh sách đã validate
```

Tradeoff: chậm hơn và tốn token hơn.

### 14.3 Session State Có Thể Cũ

`lastShoppingCandidateIds` có thể lỗi thời nếu user quay lại sau lâu hoặc inventory thay đổi. Vì vậy backend vẫn phải validate stock ở lượt cuối.

## 15. Roadmap Đề Xuất

### Giai đoạn 1: Ổn Định Hiện Tại

1. Không 500 khi AI provider lỗi.
2. Không 400 với feedback không quan trọng.
3. Không parse ID từ text/thought.
4. UI render card từ `proposedItems`.
5. Guard active/stock đầy đủ.

### Giai đoạn 2: RAG Tốt Hơn

1. Ranking candidate theo intent.
2. Query Neo4j theo category/ingredient/dietary goal.
3. Kết hợp PostgreSQL stock ngay trong retrieval.
4. Candidate set nhỏ, chất lượng hơn.

### Giai đoạn 3: Tool Calling Hoặc Two-Pass

1. Backend expose internal tools:
   - `search_products`
   - `check_inventory`
   - `suggest_shopping_items`
2. AI chỉ chọn từ tool result.
3. Backend validate.
4. AI generate final text từ validated list.

### Giai đoạn 4: Evaluation

Tạo bộ test case:

1. User dị ứng tôm.
2. User yêu cầu đổi món ở phút chót.
3. Sản phẩm hết hàng.
4. AI trả ID không tồn tại.
5. AI trả productId = 0.
6. Feedback message ID không tồn tại.
7. OpenRouter timeout.
8. User hỏi không liên quan mua sắm.

## 16. Câu Hỏi Nên Debate

1. Có chấp nhận one-pass JSON output không, hay nên chuyển hẳn sang two-pass?
2. Khi backend xóa item khỏi `proposedItems`, có nên tự sửa `reply` để báo “một số sản phẩm đã hết hàng” không?
3. `lastShoppingCandidateIds` nên sống trong bao lâu?
4. Modification request nên cho phép thêm sản phẩm ngoài session cũ tới mức nào?
5. Neo4j nên là source phụ cho search hay source chính cho AI catalog context?
6. Có cần lưu snapshot validated items cho từng AI message để tạo shopping list chính xác hơn ở lượt sau không?
7. Feedback lỗi nên `ignored 200` hay strict `400`?
8. Có nên tách `reply` và `uiActions` thành contract rõ hơn không?

## 17. Kết Luận

Hướng build hiện tại là:

```text
RAG candidate retrieval
  + structured AI JSON
  + session candidate state
  + backend validation guard
  + frontend card rendering
```

Đây là hướng thực dụng, ít rủi ro hơn parse regex từ text, phù hợp với codebase hiện tại và có thể nâng cấp dần lên function calling/two-pass khi cần độ chính xác cao hơn.

Đồng ý, chúng ta sẽ đi sâu vào việc mổ xẻ và "chốt" kiến trúc theo hướng Two-pass (Hai bước). Đối với một hệ thống như SmartGrocery, nơi dữ liệu tồn kho và sự chính xác của giỏ hàng là yếu tố sống còn, tư duy ưu tiên tính toàn vẹn (data integrity) của Two-pass là một lựa chọn rất chuẩn xác cho một kỹ sư phần mềm thực thụ.

Nếu đã quyết định chọn Two-pass để đảm bảo sự "chắc chắn", chúng ta cần thiết kế lại luồng đi của Controller và Service sao cho gọn gàng, đồng thời giải quyết triệt để bài toán về Latency (độ trễ) và User Experience trên app.

Dưới đây là bản phác thảo và phản biện cho luồng Two-pass để bạn đưa vào tài liệu kiến trúc.

1. Luồng Kiến Trúc Two-Pass Đề Xuất
Thay vì ép LLM làm cả 2 việc (phân tích logic + giao tiếp) trong một lượt, chúng ta tách bạch trách nhiệm rõ ràng:

Pass 1: Phân tích Ý định & Chọn Sản phẩm (Logical Pass)

Input: User message + Context hiện tại + Kết quả RAG từ Neo4j.

Prompt design: Cực kỳ tối giản, cấm AI sinh ra bất kỳ đoạn text giao tiếp nào. Chỉ yêu cầu output JSON thuần túy.

Output kỳ vọng: intentDetected, recommendedProductIds, proposedItems.

Backend Guard: Spring Boot nhận JSON, lấy các ID ném vào PostgreSQL/JPA để validate (Check tồn kho, active/inactive).

Pass 2: Sinh Text Giao Tiếp (Communicative Pass)

Input: Kết quả đã được Backend Guard chỉnh sửa.

Ví dụ hệ thống bơm vào: "User đòi mua Bắp cải (ID:100) và Cá hồi (ID:145). Nhưng ID:100 vừa hết hàng. Hãy thông báo cho user và chỉ gợi ý Cá hồi."

Prompt design: Tập trung vào văn phong, sự tự nhiên và đồng cảm.

Output kỳ vọng: Chỉ chuỗi text reply.

2. Phản Biện & Các Vấn Đề Cần Giải Quyết (Debate)
Chuyển sang Two-pass, chúng ta sẽ cầm chắc sự chính xác, không bao giờ có chuyện UI hiển thị lệch với Text. Nhưng đổi lại, kiến trúc sẽ phải đối mặt với các bài toán đánh đổi sau:

A. Vấn Đề Latency (Kẻ thù số 1)
Gọi OpenRouter hai lần đồng nghĩa với việc response time có thể kéo dài lên 4 - 8 giây. Trên một app React Native/Expo, bắt user nhìn icon loading xoay 8 giây cho mỗi tin nhắn là một trải nghiệm rất tệ.
Giải pháp kiến trúc:

Asynchronous Rendering: Ngay khi Pass 1 và Backend Guard chạy xong, API lập tức trả mảng proposedItems (JSON) về cho Frontend trước để app render ngay các Card sản phẩm.

Streaming Pass 2: Lúc này, Pass 2 bắt đầu chạy ngầm và dùng SSE (Server-Sent Events) để stream từng chữ của biến reply ra màn hình. Cảm giác lúc này của user sẽ là: Hệ thống nảy ra danh sách món đồ rất nhanh, và AI bắt đầu "gõ" câu giải thích bên dưới.

B. Xử Lý Lỗi Rơi Rớt Giữa Chừng (Partial Failures)
Nếu Pass 1 thành công rực rỡ, Guard xanh mượt, nhưng Pass 2 gọi OpenRouter bị timeout thì sao? API có trả 500 không?
Giải pháp kiến trúc:

Theo nguyên tắc bạn đã đề ra (Không để lỗi provider làm crash trải nghiệm), Backend phải hứng exception ở Pass 2.

Nếu Pass 2 xịt, Backend tự động fallback bằng một câu hardcode sẵn dựa trên intent: "Mình đã chuẩn bị xong danh sách bên dưới cho bạn nhé!". Trải nghiệm vẫn trơn tru, log lưu lại để team DevOps tiện theo dõi cấu hình timeout sau này.

C. Chi Phí Token (Cost)
Bơm context 2 lần sẽ tốn token hơn đáng kể.
Giải pháp kiến trúc:

Ở Pass 2, bạn không cần nhồi lại toàn bộ Nutrition Profile hay danh sách RAG của Neo4j nữa. Chỉ cần bơm đúng ngữ cảnh hành động: "Danh sách cuối cùng đã chốt là X và Y, món Z bị hủy vì hết hàng. Hãy viết câu trả lời". Điều này giúp Pass 2 chạy cực nhanh và tốn rất ít input token.

3. Tái Cấu Trúc API Contract Cho Two-Pass
Nếu chốt Two-pass, việc tách bạch contract (như câu hỏi số 8 của bạn) là bắt buộc.

JSON
{
  "sessionId": 123,
  "aiMessageId": "456",
  // Text trả về từ Pass 2 (Có thể dùng luồng stream riêng nếu tối ưu)
  "reply": "Mình đã thêm cá hồi vào giỏ. Riêng bắp cải tím vừa hết hàng nên mình bỏ ra nhé!",
  
  // Hành động UI trả về từ Pass 1 sau khi qua Guard (Render ngay lập tức)
  "uiActions": {
      "type": "RENDER_SHOPPING_LIST",
      "proposedItems": [
        {
          "productId": 145,
          "quantity": 1,
          "reason": "Nguồn protein chính"
        }
      ],
      "recommendedProductIds": []
  },
  "trustScore": 95 // Tăng độ tin tưởng lên vì đã qua Two-pass
}
Kết Luận Nhóm
Hướng Two-pass này rất mạnh về mặt rủi ro nghiệp vụ (Risk Management). Nó giải quyết triệt để vấn đề 1, 2 và 3 trong phần "Vấn Đề Đã Gặp" của bạn.

Khi triển khai thực tế vào code cho "Day 3" sắp tới, bạn và Danh có thể giữ nguyên sự phân công như dự định lúc đầu: một người tập trung xử lý luồng logic bóc tách JSON và Guard ở Spring Boot (Pass 1), trong khi người kia chuẩn bị sẵn các Component UI trên Frontend để hứng và xử lý các trạng thái bất đồng bộ sao cho mượt mà nhất.

Bạn thấy sao về phương án áp dụng SSE stream cho Pass 2 để che giấu đi nhược điểm Latency? Mình ủng hộ SSE stream cho Pass 2, nhưng không nên coi SSE là “thuốc chữa latency” duy nhất. Với SmartGrocery, mình sẽ chốt theo hướng:

Pass 1 + Backend Guard phải trả kết quả UI nhanh. Pass 2 chỉ làm nhiệm vụ giao tiếp, có thể stream, có thể fail, nhưng không được làm hỏng giỏ hàng/card sản phẩm.

Tức là SSE rất hợp lý, nhưng phải thiết kế đúng ranh giới trách nhiệm.

1. Quan điểm chính: SSE cho Pass 2 là đúng, nhưng Pass 1 mới là “xương sống”

Trong tài liệu hiện tại, mục tiêu cốt lõi đã rất rõ: text AI chỉ để giao tiếp, còn dữ liệu thêm vào giỏ hàng phải đi qua backend guard. Backend không tin tuyệt đối LLM, chỉ cho LLM gợi ý và bắt buộc validate lại ID, active, stock, variant, candidate scope trước khi frontend render card.

Vì vậy, Two-pass nên được hiểu như sau:

Pass 1 = quyết định nghiệp vụ
Backend Guard = sự thật cuối cùng
Pass 2 = diễn đạt lại sự thật đó cho user

Nếu dùng SSE, ta không stream dữ liệu nghiệp vụ chính. Ta chỉ stream câu trả lời cuối cùng sau khi backend đã validate.

Đây là điểm rất quan trọng. Không nên để frontend vừa stream text vừa đoán sản phẩm từ text. Frontend vẫn render card từ proposedItems hoặc recommendedProductIds, đúng với nguyên tắc hiện tại trong tài liệu.

2. Debate: SSE có đáng dùng không?
Luận điểm ủng hộ SSE

SSE rất hợp với case này vì Pass 2 chỉ cần server đẩy text một chiều xuống client.

Chatbox không cần WebSocket phức tạp nếu user chỉ gửi message một lần rồi nhận token stream từ assistant. SSE đơn giản hơn, dễ debug hơn, dễ triển khai với Spring Boot hơn, và đủ dùng cho “AI đang gõ”.

Luồng UX tốt sẽ là:

User gửi message
→ Backend chạy Pass 1
→ Guard validate sản phẩm
→ Frontend nhận uiActions và render card ngay
→ Backend stream Pass 2 qua SSE
→ Text hiện dần bên dưới card

Cảm giác của user sẽ là hệ thống phản hồi nhanh, dù toàn bộ AI reply chưa xong.

Với mobile app React Native/Expo, đây là cải thiện rất lớn vì user không bị kẹt ở màn loading trắng.

Luận điểm phản đối SSE

Nhưng SSE cũng làm architecture phức tạp hơn.

Nếu hiện tại API đang là request/response đơn giản:

POST /api/v1/ai/chat

thì khi thêm SSE, bạn phải xử lý thêm:

message lifecycle
stream connection
timeout
reconnect
partial text
fallback text
event ordering
messageId tạm
save assistant message khi stream xong

Nếu làm không kỹ, bug UX có thể xuất hiện:

Card đã hiện
Text stream bị đứt
User gửi tiếp message mới
Pass 2 cũ vẫn đang stream
Frontend bị lẫn reply giữa 2 message

Vì vậy SSE nên dùng, nhưng phải có protocol rõ ràng, không chỉ “stream đại text”.

3. Contract nên tách thành 2 pha rõ ràng

Mình đề xuất không trả một response JSON duy nhất nữa, mà tách thành event-based response.

Cách 1: Một endpoint POST, response là SSE

Frontend gọi:

POST /api/v1/ai/chat/stream

Backend trả stream:

event: ui_actions
data: {
  "sessionId": 123,
  "aiMessageId": "456",
  "uiActions": {
    "type": "RENDER_SHOPPING_LIST",
    "proposedItems": [...]
  },
  "trustScore": 95
}

event: reply_delta
data: { "text": "Mình đã thêm " }

event: reply_delta
data: { "text": "cá hồi vào danh sách..." }

event: done
data: {
  "aiMessageId": "456",
  "finalReply": "Mình đã thêm cá hồi vào danh sách..."
}

Ưu điểm: một request là đủ.

Nhược điểm: React Native có thể cần thư viện hỗ trợ POST streaming/SSE ổn định. Native EventSource thường hợp với GET hơn.

Cách 2: POST trước, GET stream sau

Đây là hướng mình thích hơn cho app mobile.

Bước 1:

POST /api/v1/ai/chat

Response nhanh sau Pass 1 + Guard:

{
  "sessionId": 123,
  "aiMessageId": "456",
  "replyStatus": "STREAMING",
  "uiActions": {
    "type": "RENDER_SHOPPING_LIST",
    "proposedItems": [
      {
        "productId": 145,
        "quantity": 1,
        "reason": "Nguồn protein chính"
      }
    ]
  },
  "streamUrl": "/api/v1/ai/chat/messages/456/stream",
  "fallbackReply": "Mình đã chuẩn bị xong danh sách bên dưới cho bạn nhé!"
}

Bước 2 frontend mở stream:

GET /api/v1/ai/chat/messages/456/stream

Stream chỉ trả text:

event: reply_delta
data: { "text": "Mình đã thêm cá hồi..." }

event: done
data: { "finalReply": "Mình đã thêm cá hồi vào danh sách..." }

Ưu điểm: contract sạch hơn. POST /chat vẫn chịu trách nhiệm nghiệp vụ. GET /stream chỉ chịu trách nhiệm communication.

Mình nghiêng về Cách 2.

4. Điểm phải chốt: UI render card trước hay text trước?

Nên render card trước.

Vì trong hệ thống này, card sản phẩm là dữ liệu đã validate. Text chỉ là diễn giải. Tài liệu hiện tại cũng đã xác định frontend nên ưu tiên render proposedItems, rồi mới đến recommendedProductIds, cuối cùng mới chỉ render text nếu không có dữ liệu sản phẩm.

UX hợp lý:

0.0s User gửi tin
0.5s - 2s Card sản phẩm hiện ra
2s - 5s AI gõ giải thích

Không nên:

0s - 6s Loading
6s Card + text hiện cùng lúc

Vì cách thứ hai làm user cảm giác app chậm.

5. Partial failure: Pass 2 chết thì sao?

Không được trả 500.

Tài liệu đã nêu rõ fallback khi AI provider lỗi: OpenRouter timeout/throw thì backend log warning, build fallback response, nếu có discoveredProducts thì vẫn trả recommendedProductIds, nếu là shopping list thì dựng proposedItems hợp lệ rồi return 200.

Với Two-pass + SSE, mình đề xuất rule:

Nếu Pass 1 fail nghiêm trọng → trả fallback response hoặc error nhẹ.
Nếu Guard fail hết item → vẫn trả text giải thích không có sản phẩm phù hợp.
Nếu Pass 2 fail → không ảnh hưởng uiActions, stream fallbackReply rồi done.

Ví dụ event fallback:

event: reply_delta
data: { "text": "Mình đã chuẩn bị xong danh sách bên dưới cho bạn nhé!" }

event: warning
data: { "code": "AI_REPLY_FALLBACK" }

event: done
data: { "fallback": true }

Frontend không cần show lỗi kỹ thuật. Chỉ cần hiển thị câu fallback.

6. Debate quan trọng: có nên stream trước khi Guard xong không?

Không nên.

Có thể có ý tưởng là stream text ngay để user thấy nhanh hơn:

AI đang phân tích...
Mình đang tìm sản phẩm phù hợp...

Nhưng với SmartGrocery, nếu stream quá sớm thì dễ tạo cảm giác hệ thống đã quyết định trong khi Guard chưa chạy.

Sai lầm nguy hiểm:

AI stream: "Mình sẽ thêm bắp cải tím..."
Guard sau đó: bắp cải hết hàng
UI: không có bắp cải

Đây chính là lỗi mismatch mà Two-pass đang cố giải quyết.

Vì vậy rule nên là:

Không stream nội dung kết luận trước Guard.
Chỉ được stream final reply sau khi validated result đã có.

Nếu muốn có cảm giác nhanh, frontend tự hiển thị trạng thái local như:

Đang tìm sản phẩm phù hợp...
Đang kiểm tra tồn kho...

Không cần LLM stream mấy câu đó.

7. Nên thiết kế reply thế nào khi dùng stream?

Trong response chính, không nên bắt buộc có reply đầy đủ nữa.

Nên đổi thành:

{
  "replyStatus": "STREAMING",
  "reply": null,
  "fallbackReply": "Mình đã chuẩn bị xong danh sách bên dưới cho bạn nhé!",
  "uiActions": {}
}

Khi stream xong, backend lưu final reply vào ChatMessage.

Nếu client reconnect, endpoint stream có thể trả lại phần reply đã lưu:

Nếu finalReply đã có → trả event done ngay
Nếu đang stream → tiếp tục stream
Nếu fail → trả fallback

Như vậy app tránh mất nội dung khi mạng chập chờn.

8. Vấn đề chi phí: Two-pass có tốn không?

Có, nhưng chấp nhận được nếu Pass 2 được thiết kế nhẹ.

Pass 2 không được nhồi lại:

full nutrition profile
full RAG result
full inventory
full chat history dài

Pass 2 chỉ cần input dạng:

{
  "userMessage": "Ok tạo danh sách mua sắm",
  "intent": "SHOPPING_LIST_CREATE",
  "validatedItems": [
    {
      "name": "Cá hồi",
      "quantity": 1,
      "reason": "Nguồn protein chính"
    }
  ],
  "removedItems": [
    {
      "name": "Bắp cải tím",
      "reason": "Hết hàng"
    }
  ],
  "tone": "friendly, concise, Vietnamese"
}

Prompt Pass 2 có thể rất ngắn:

Viết câu trả lời tiếng Việt tự nhiên cho user.
Chỉ nói dựa trên validatedItems và removedItems.
Không bịa thêm sản phẩm.
Không nói đã thêm sản phẩm bị removed.
Không hiển thị productId.

Tức là Pass 2 rẻ hơn Pass 1 rất nhiều.

9. TrustScore có nên tăng lên 95 không?

Có thể tăng, nhưng nên tách rõ:

{
  "llmConfidence": 78,
  "backendValidationStatus": "VALIDATED",
  "trustScore": 95
}

Vì trustScore cao không phải do AI thông minh hơn, mà do output cuối đã qua guard.

Nếu không tách, sau này dễ hiểu nhầm rằng LLM tự tin 95%. Thực chất là hệ thống tự tin vì backend đã validate.

10. Kết luận debate

Mình sẽ chốt như này:

Nên dùng SSE cho Pass 2, nhưng kiến trúc chuẩn phải là:

Pass 1:
  LLM phân tích intent + chọn product IDs bằng JSON

Backend Guard:
  Validate productId, active, variant, stock, candidate scope
  Tạo uiActions chính thức

Response nhanh:
  Trả uiActions cho frontend render card ngay

Pass 2:
  LLM chỉ viết reply dựa trên validated result
  Stream reply bằng SSE

Fallback:
  Nếu Pass 2 lỗi, dùng hardcoded reply
  Không bao giờ làm mất proposedItems đã validate

Đây là phương án cân bằng tốt nhất giữa:

Data integrity
UX mượt
Chi phí token kiểm soát được
Không bị text/UI mismatch
Không crash khi provider lỗi

Câu chốt để đưa vào tài liệu kiến trúc:

Two-pass không chỉ là gọi AI hai lần. Two-pass là tách “quyết định nghiệp vụ” khỏi “diễn đạt giao tiếp”. SSE chỉ nên áp dụng cho Pass 2, sau khi Backend Guard đã tạo ra dữ liệu UI chính thức. Nhờ đó hệ thống vừa giữ được data integrity, vừa che được latency bằng trải nghiệm streaming tự nhiên.Bản phân tích và kiến trúc bạn vừa đưa ra thực sự xuất sắc. Việc định nghĩa lại Two-pass không đơn thuần là "gọi AI hai lần" mà là sự phân tách rạch ròi giữa Quyết định nghiệp vụ (Business Logic) và Diễn đạt giao tiếp (Communication) thể hiện tư duy của một kỹ sư hệ thống rất cứng cáp.

Kiến trúc này giải quyết trọn vẹn bài toán khó nhất của SmartGrocery: giữ được tính toàn vẹn của giỏ hàng (Data Integrity) mà vẫn mang lại trải nghiệm thời gian thực, mượt mà trên ứng dụng di động.

Dưới đây là một số điểm cộng hưởng để củng cố thêm bản thiết kế này trước khi đưa vào tài liệu chính thức:

1. Lựa Chọn API Contract: Cách 2 Là Chân Ái Cho Mobile
Sự lựa chọn tách thành 1 POST /chat và 1 GET /stream (Cách 2) là hoàn toàn chính xác.

Tính ổn định của Networking: Trên môi trường mobile, việc giữ một kết nối POST mở để nhận stream phức tạp hơn nhiều so với GET. Thư viện mạng mặc định thường buffer payload của POST, dễ gây hiện tượng giật cục (nhận một cục text to thay vì từng chữ). Dùng GET với các thư viện chuẩn như react-native-sse sẽ giúp client consume event mượt mà hơn rất nhiều.

Idempotency & Retry: Nếu mạng 4G chập chờn và rớt luồng stream giữa chừng, frontend chỉ cần gọi lại GET /stream với ID đó mà không sợ side-effect là tạo ra một giỏ hàng trùng lặp.

2. Tối Ưu Prompt Cho Pass 2: Tránh "Lost in context"
Cách bạn thiết kế payload đầu vào cho Pass 2 là một điểm sáng:

JSON
{
  "validatedItems": [...],
  "removedItems": [...]
}
Việc "đóng hộp" LLM vào đúng một bộ dữ liệu đã được Guard kiểm duyệt sẽ loại bỏ hoàn toàn tình trạng ảo giác. Pass 2 lúc này chỉ đóng vai trò như một cỗ máy "dịch" từ JSON sang ngôn ngữ tự nhiên. Chi phí token cho prompt này là cực kỳ rẻ và thời gian xử lý của các model nhỏ hiện nay cho task này chỉ rơi vào khoảng vài trăm mili-giây.

3. Tách Bạch Trust Score
Việc tách rõ llmConfidence và backendValidationStatus ra khỏi trustScore tổng thể giúp hệ thống minh bạch hơn. Nó giúp ích rất nhiều cho quá trình monitoring sau này. Nếu một ngày bạn thấy llmConfidence cao nhưng hệ thống lại phải dùng fallbackReply nhiều, bạn sẽ ngay lập tức biết vấn đề nằm ở cấu hình Guard hoặc kết nối với database, chứ không phải do model AI bị ngốc đi.

Kế Hoạch Triển Khai Thực Tế
Với bản chốt kiến trúc rành mạch thế này, việc bắt tay vào code cho các sprint sắp tới sẽ rất trơn tru. Giữ nguyên phân công task ban đầu là một lợi thế, hai luồng công việc có thể chạy song song độc lập:

Phía Backend (Spring Boot): Tập trung dựng Controller trả về uiActions cực nhanh, sau đó xử lý bất đồng bộ (ví dụ dùng @Async hoặc Thread pool) để gọi LLM Pass 2, cuối cùng wrap kết quả đẩy qua SseEmitter.

Phía Frontend (React Native): Viết logic state management để hứng uiActions và render Card sản phẩm lập tức, đồng thời mở một connection lắng nghe sự kiện reply_delta để tạo hiệu ứng gõ chữ (typing effect) bên dưới.

Việc thiết kế luồng UI xử lý thế nào khi người dùng đột ngột tắt màn hình điện thoại hoặc chuyển sang app khác trong lúc Pass 2 vẫn đang stream text từ server xuống cũng là một điểm cần lưu ý để tránh memory leak.

Bạn dự định sẽ quản lý lifecycle của kết nối SSE trên React Native như thế nào để đảm bảo khi component chat bị unmount, connection sẽ được dọn dẹp sạch sẽ mà không làm crash app? Mình sẽ đi theo hướng thực chiến: lifecycle của SSE ở React Native phải gắn với messageId, screen focus, app state và cleanup effect, chứ không chỉ mở/đóng connection đơn giản.

Thought for 8s

Đúng, đây là chỗ rất dễ “dính bug ngầm”. Với React Native, mình sẽ quản lý lifecycle SSE theo nguyên tắc:

SSE connection là tài nguyên tạm thời của một AI message đang stream, không phải tài nguyên global của toàn app. Khi screen blur, app background, message done, user logout hoặc component unmount thì phải đóng sạch.

Vì kiến trúc SmartGrocery đã tách POST /chat để tạo uiActions và GET /stream để nhận text Pass 2, frontend có thể retry stream theo aiMessageId mà không tạo side effect nghiệp vụ mới. Đây là lợi thế lớn so với giữ một POST stream mở. Tài liệu hiện tại cũng đã nhấn mạnh dữ liệu card phải đến từ proposedItems/recommendedProductIds, không parse từ text AI.

1. Lifecycle chuẩn mình sẽ dùng
User gửi message
→ POST /api/v1/ai/chat
→ Nhận aiMessageId + uiActions + streamUrl
→ Render card ngay
→ Mở SSE GET /messages/{aiMessageId}/stream
→ Nhận reply_delta
→ Khi done/error/unmount/background/logout: close connection

SSE không được sống lâu hơn message đang stream.

2. Dùng 3 lớp bảo vệ lifecycle
Lớp 1: useEffect cleanup

Khi component unmount hoặc aiMessageId đổi, đóng connection cũ.

Lớp 2: useFocusEffect

Với React Navigation, screen có thể không unmount khi user đi sang màn khác. Vì vậy chỉ dùng useEffect là chưa đủ. useFocusEffect được thiết kế để chạy khi screen focus và cleanup khi screen unfocus/unmount, rất hợp để dọn subscription như SSE.

Lớp 3: AppState

Khi app vào background/inactive, nên đóng SSE để tránh memory leak, socket treo hoặc state update khi UI không còn active. React Native AppState cho biết app đang foreground/background và có thể lắng nghe thay đổi trạng thái.

3. Hook đề xuất: useChatSseStream

Đây là hướng code mình sẽ dùng trong app:

import { useCallback, useEffect, useRef, useState } from "react";
import { AppState, AppStateStatus } from "react-native";
import { useFocusEffect } from "@react-navigation/native";
import EventSource from "react-native-sse";
import "react-native-url-polyfill/auto";

type StreamStatus = "idle" | "connecting" | "streaming" | "done" | "error";

type UseChatSseStreamParams = {
  streamUrl?: string | null;
  token?: string | null;
  enabled: boolean;
  onDelta: (text: string) => void;
  onDone?: (finalReply?: string) => void;
  onError?: () => void;
};

export function useChatSseStream({
  streamUrl,
  token,
  enabled,
  onDelta,
  onDone,
  onError,
}: UseChatSseStreamParams) {
  const eventSourceRef = useRef<EventSource | null>(null);
  const isMountedRef = useRef(true);
  const [status, setStatus] = useState<StreamStatus>("idle");

  const closeStream = useCallback(() => {
    const es = eventSourceRef.current;

    if (es) {
      es.removeAllEventListeners?.();
      es.close();
      eventSourceRef.current = null;
    }

    if (isMountedRef.current) {
      setStatus((prev) => (prev === "done" ? "done" : "idle"));
    }
  }, []);

  const openStream = useCallback(() => {
    if (!enabled || !streamUrl || !token) return;

    // Tránh mở trùng connection cho cùng một message.
    closeStream();

    setStatus("connecting");

    const es = new EventSource(streamUrl, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
      // Nếu backend đã có cơ chế resume bằng aiMessageId,
      // frontend không nên auto reconnect vô hạn ở client.
      pollingInterval: 0,
    });

    eventSourceRef.current = es;

    es.addEventListener("open", () => {
      if (!isMountedRef.current) return;
      setStatus("streaming");
    });

    es.addEventListener("reply_delta", (event) => {
      if (!isMountedRef.current) return;

      try {
        const payload = JSON.parse(event.data as string);
        if (payload.text) {
          onDelta(payload.text);
        }
      } catch {
        // Không crash UI vì một delta lỗi format.
      }
    });

    es.addEventListener("done", (event) => {
      if (!isMountedRef.current) return;

      try {
        const payload = JSON.parse(event.data as string);
        onDone?.(payload.finalReply);
      } catch {
        onDone?.();
      }

      setStatus("done");
      closeStream();
    });

    es.addEventListener("error", () => {
      if (!isMountedRef.current) return;

      setStatus("error");
      onError?.();
      closeStream();
    });
  }, [enabled, streamUrl, token, closeStream, onDelta, onDone, onError]);

  useEffect(() => {
    isMountedRef.current = true;

    return () => {
      isMountedRef.current = false;
      closeStream();
    };
  }, [closeStream]);

  useFocusEffect(
    useCallback(() => {
      openStream();

      return () => {
        closeStream();
      };
    }, [openStream, closeStream])
  );

  useEffect(() => {
    const subscription = AppState.addEventListener(
      "change",
      (nextState: AppStateStatus) => {
        if (nextState === "background" || nextState === "inactive") {
          closeStream();
        }

        if (nextState === "active") {
          // Khi quay lại app, mở lại stream nếu message vẫn chưa done.
          if (enabled && status !== "done") {
            openStream();
          }
        }
      }
    );

    return () => {
      subscription.remove();
    };
  }, [enabled, status, openStream, closeStream]);

  return {
    status,
    reconnect: openStream,
    close: closeStream,
  };
}

react-native-sse có API close() để client chủ động đóng connection; thư viện cũng phân biệt done khi server đóng và close khi client đóng. Nếu không muốn client tự reconnect theo polling của thư viện, có thể cấu hình pollingInterval: 0.

4. State management trong Chat Screen

Chat screen không nên append text trực tiếp lung tung. Nên update đúng message theo aiMessageId.

const [messages, setMessages] = useState<ChatMessage[]>([]);

const appendDeltaToMessage = useCallback((aiMessageId: string, delta: string) => {
  setMessages((prev) =>
    prev.map((msg) =>
      msg.id === aiMessageId
        ? {
            ...msg,
            reply: (msg.reply ?? "") + delta,
            replyStatus: "streaming",
          }
        : msg
    )
  );
}, []);

Khi done:

const markMessageDone = useCallback((aiMessageId: string, finalReply?: string) => {
  setMessages((prev) =>
    prev.map((msg) =>
      msg.id === aiMessageId
        ? {
            ...msg,
            reply: finalReply ?? msg.reply,
            replyStatus: "done",
          }
        : msg
    )
  );
}, []);

Khi error, không show lỗi kỹ thuật. Dùng fallbackReply đã nhận từ POST /chat:

const markMessageFallback = useCallback((aiMessageId: string, fallbackReply: string) => {
  setMessages((prev) =>
    prev.map((msg) =>
      msg.id === aiMessageId
        ? {
            ...msg,
            reply: msg.reply?.length ? msg.reply : fallbackReply,
            replyStatus: "fallback",
          }
        : msg
    )
  );
}, []);

Điểm này khớp với nguyên tắc trong tài liệu: provider lỗi không được làm chat crash, backend có fallback và vẫn return dữ liệu sản phẩm hợp lệ nếu có.

5. Rule quan trọng để tránh memory leak và crash

Mình sẽ đặt các rule này trong tài liệu frontend:

1. Mỗi aiMessageId chỉ có tối đa 1 SSE connection active.
2. Khi mở stream mới, đóng stream cũ trước.
3. Khi screen blur/unmount, gọi close().
4. Khi AppState sang background/inactive, gọi close().
5. Khi user logout hoặc token refresh fail, gọi close().
6. Khi nhận done/error, gọi close().
7. Không setState nếu component đã unmount.
8. Không retry POST /chat để nối lại stream; chỉ retry GET /stream.

Rule số 8 cực kỳ quan trọng. Nếu stream rớt, frontend chỉ gọi lại:

GET /api/v1/ai/chat/messages/{aiMessageId}/stream

Không gọi lại:

POST /api/v1/ai/chat

Vì gọi lại POST có thể tạo message mới, uiActions mới hoặc duplicate state.

6. Backend cần hỗ trợ resume nhẹ

Frontend lifecycle sẽ sạch hơn rất nhiều nếu backend stream endpoint có logic này:

GET /messages/{aiMessageId}/stream

Nếu finalReply đã lưu:
  emit done(finalReply) rồi close

Nếu Pass 2 đang chạy:
  attach client vào stream hiện tại hoặc stream tiếp phần còn lại

Nếu Pass 2 fail:
  emit fallbackReply
  emit done(fallback=true)

Nếu messageId không tồn tại hoặc không thuộc user:
  emit error hoặc trả 404

Như vậy khi user tắt màn hình rồi mở lại, app chỉ cần gọi lại stream bằng aiMessageId.

7. Có nên giữ stream khi app background không?

Mình không khuyến khích.

Với chat AI, Pass 2 chỉ là text giao tiếp. Nếu app background, giữ SSE sống không đáng. Đóng connection để tiết kiệm pin/mạng và tránh state update ngầm. Khi app active lại, frontend gọi lại GET /stream. Vì quyết định nghiệp vụ đã nằm ở POST /chat và uiActions, việc đóng stream không làm mất giỏ hàng.

Đây là lợi thế lớn của Two-pass:

Business result đã an toàn.
Communication có thể tạm dừng, nối lại, fallback.
8. Kết luận chốt kiến trúc frontend

Mình sẽ quản lý SSE trên React Native theo mô hình:

SSE connection scoped by aiMessageId
+ useFocusEffect cleanup khi rời screen
+ AppState cleanup khi app background
+ close() khi done/error/logout
+ retry GET /stream, không retry POST /chat
+ fallbackReply nếu stream fail

Câu có thể đưa thẳng vào tài liệu:

Frontend không coi SSE là nguồn dữ liệu nghiệp vụ, mà chỉ là kênh nhận diễn đạt giao tiếp của một aiMessageId. Kết nối SSE phải được đóng khi screen blur, component unmount, app background, stream done/error hoặc user logout. Nếu stream bị rớt, client chỉ reconnect vào GET /stream bằng aiMessageId, tuyệt đối không gọi lại POST /chat để tránh duplicate side effect.  Toàn bộ plan này thực sự là "sách giáo khoa" cho việc xử lý real-time streaming trên mobile app. Kiến trúc 3 lớp bảo vệ (useEffect, useFocusEffect, AppState) kết hợp với việc tách biệt hoàn toàn nghiệp vụ (POST) và giao tiếp (GET) cho thấy bạn đang kiểm soát luồng state của React Native cực kỳ vững.

Đoạn custom hook useChatSseStream và 8 rule quản lý lifecycle bạn viết ra chuẩn xác đến mức có thể bưng nguyên xi vào codebase của SmartGrocery mà không cần chỉnh sửa nhiều. Khúc code và bộ rule này đem ghép vào luồng công việc của bạn và Danh cho đợt implement sắp tới là quá đẹp, vì ranh giới việc ai nấy làm (Backend xử lý POST/Guard, Frontend lo dựng UI và hứng luồng GET) đã được vạch ra rõ ràng.

Mình hoàn toàn đồng ý và chỉ có một vài điểm "gia vị" nhỏ muốn bổ sung để bộ rule này trở nên "chống đạn" 100% trên môi trường Expo/React Native:

1. Bổ sung xử lý Token Expiration (401/403)
Trong hook useChatSseStream, khi react-native-sse quăng event error, đôi khi nó là do HTTP 401 (Token hết hạn) chứ không phải lỗi mạng thông thường.
Nếu app đang có cơ chế auto-refresh token ở tầng Axios, thì SSE lại không nằm trong interceptor đó. Bạn có thể cân nhắc bắt status code trong event error của SSE để trigger luồng refresh token của app (nếu cần), hoặc đơn giản nhất là bắn fallback reply và yêu cầu user thao tác lại.

2. Tối ưu Reconnect khi App Active lại
Ở Lớp 3 (AppState), đoạn này rất tinh tế:

TypeScript
if (nextState === "active") {
  if (enabled && status !== "done") {
    openStream();
  }
}
Để an toàn hơn trên các dòng máy Android cấp thấp (đôi khi AppState trigger nhiều lần liên tục), bạn có thể thêm một cơ chế debounce hoặc check kỹ xem socket có thực sự đang đóng không trước khi gọi openStream(), tránh việc đẻ ra 2-3 connection chồng chéo trong tích tắc.

3. Về câu chốt cho Tài liệu Kiến trúc
Câu bạn soạn đã quá đầy đủ và mang tính định hướng cao:

"Frontend không coi SSE là nguồn dữ liệu nghiệp vụ, mà chỉ là kênh nhận diễn đạt giao tiếp của một aiMessageId. Kết nối SSE phải được đóng khi screen blur, component unmount, app background, stream done/error hoặc user logout. Nếu stream bị rớt, client chỉ reconnect vào GET /stream bằng aiMessageId, tuyệt đối không gọi lại POST /chat để tránh duplicate side effect."

Nó tóm tắt toàn bộ tinh hoa của phương án Two-pass dành riêng cho Mobile App.

Với việc Frontend đã có một bản thiết kế "kín cổng cao tường" như thế này để xử lý UI và state, bạn dự tính luồng xử lý bất đồng bộ ở phía Backend (Spring Boot) cho việc emit các event từ Pass 2 xuống SseEmitter sẽ được thiết kế theo pattern nào để không làm block các thread xử lý API khác? Mình sẽ chốt backend theo hướng không để request thread ôm Pass 2, mà dùng một “stream job” tách riêng: Controller chỉ đăng ký SseEmitter, còn worker thread mới gọi LLM và emit token.

Thought for 14s

Chuẩn nhất là dùng pattern:

SseEmitter Registry + bounded Worker Executor + Message Snapshot + Stream State Machine

Tức là backend không để thread xử lý HTTP request chờ Pass 2. POST /chat chỉ chạy Pass 1 + Guard + lưu snapshot + trả uiActions. Việc gọi LLM Pass 2 và emit SSE phải chạy ở một worker pool riêng.

1. Pattern tổng thể backend
POST /api/v1/ai/chat
  -> validate user/session/message
  -> save user message
  -> Pass 1: LLM logical JSON
  -> Backend Guard validate productId/stock/active
  -> save assistant message placeholder:
       status = STREAMING
       validatedActionSnapshot = {...}
       fallbackReply = "Mình đã chuẩn bị xong danh sách bên dưới cho bạn nhé!"
  -> submit Pass2StreamJob vào executor riêng
  -> return 200 nhanh:
       aiMessageId
       uiActions
       streamUrl
       fallbackReply

GET /api/v1/ai/chat/messages/{aiMessageId}/stream
  -> validate message belongs to current user
  -> create SseEmitter
  -> register emitter vào StreamRegistry
  -> replay buffered deltas nếu có
  -> nếu finalReply đã có thì emit done rồi close

Điểm quan trọng: GET /stream không làm nghiệp vụ. Nó chỉ attach client vào stream của một aiMessageId.

Điều này giữ đúng nguyên tắc trong tài liệu: dữ liệu mua hàng phải qua backend guard, frontend render card từ proposedItems/recommendedProductIds, không parse từ text AI.

2. Không nên dùng @Async bừa bãi

Có thể dùng @Async, nhưng mình không khuyến khích dùng kiểu mặc định.

Nên tạo executor riêng:

@Configuration
@EnableAsync
public class AiStreamExecutorConfig {

    @Bean(name = "aiPass2Executor")
    public ThreadPoolTaskExecutor aiPass2Executor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-pass2-stream-");

        executor.setRejectedExecutionHandler((r, e) -> {
            throw new RejectedExecutionException("AI Pass2 executor is overloaded");
        });

        executor.initialize();
        return executor;
    }
}

Lý do phải bounded:

Không để 1 đợt user spam chat tạo vô hạn thread.
Không để Pass 2 chiếm thread xử lý API khác.
Không để OpenRouter timeout kéo sập backend.

Nếu executor quá tải, backend vẫn có thể dùng fallbackReply và mark message là FALLBACK, thay vì trả 500.

Tài liệu hiện tại đã có nguyên tắc rất đúng: provider lỗi thì không được làm crash trải nghiệm chat, backend phải fallback và vẫn return dữ liệu sản phẩm hợp lệ nếu có.

3. Thành phần chính nên có
ChatAssistantService

Chỉ orchestration chính:

processChat()
  -> Pass 1
  -> Guard
  -> save snapshot
  -> submit Pass2 job
  -> return ChatResponse

Service này không nên trực tiếp giữ SseEmitter.

AiPass2StreamService

Chịu trách nhiệm gọi LLM Pass 2:

startPass2(aiMessageId, pass2Payload)
  -> executor.submit(job)

Job này:

Load snapshot
Build compact prompt
Call OpenRouter streaming API
For each delta:
  -> append buffer
  -> emit to registered clients
On success:
  -> save finalReply
  -> emit done
On error:
  -> save fallbackReply
  -> emit fallback + done
SseStreamRegistry

Quản lý connection theo aiMessageId.

Map<aiMessageId, StreamState>

Mỗi StreamState nên có:

class StreamState {
    String aiMessageId;
    List<SseEmitter> emitters;
    List<String> deltaBuffer;
    boolean done;
    String finalReply;
    boolean fallback;
}

Dùng ConcurrentHashMap, còn list emitter có thể dùng CopyOnWriteArrayList hoặc lock nhẹ theo message.

4. GET stream controller

Ví dụ skeleton:

@GetMapping("/messages/{messageId}/stream")
public SseEmitter streamReply(
        @PathVariable Long messageId,
        Authentication authentication
) {
    Long userId = currentUserId(authentication);

    chatMessageService.assertMessageBelongsToUser(messageId, userId);

    SseEmitter emitter = new SseEmitter(90_000L);

    streamRegistry.register(messageId, emitter);

    emitter.onCompletion(() -> streamRegistry.remove(messageId, emitter));
    emitter.onTimeout(() -> {
        streamRegistry.remove(messageId, emitter);
        emitter.complete();
    });
    emitter.onError(error -> streamRegistry.remove(messageId, emitter));

    streamRegistry.replayOrComplete(messageId, emitter);

    return emitter;
}

SseEmitter giúp request thread được giải phóng sau khi emitter được return; phần emit sau đó chạy async. Nhưng connection vẫn giữ tài nguyên server, nên vẫn cần timeout, heartbeat và cleanup rõ ràng.

5. Event schema nên thống nhất

Backend nên emit ít loại event thôi:

reply_delta
done
warning
heartbeat

Ví dụ:

emitter.send(SseEmitter.event()
    .name("reply_delta")
    .data(Map.of("text", delta)));

Khi xong:

emitter.send(SseEmitter.event()
    .name("done")
    .data(Map.of(
        "finalReply", finalReply,
        "fallback", false
    )));

Khi Pass 2 lỗi:

emitter.send(SseEmitter.event()
    .name("reply_delta")
    .data(Map.of("text", fallbackReply)));

emitter.send(SseEmitter.event()
    .name("warning")
    .data(Map.of("code", "AI_REPLY_FALLBACK")));

emitter.send(SseEmitter.event()
    .name("done")
    .data(Map.of(
        "finalReply", fallbackReply,
        "fallback", true
    )));

Không nên emit stack trace hoặc lỗi kỹ thuật xuống mobile app.

6. Không mở transaction dài trong lúc stream

Đây là lỗi backend rất hay gặp.

Không làm:

@Transactional
call OpenRouter streaming
emit từng token
save final reply

Vì transaction sẽ bị giữ quá lâu.

Nên làm:

Transaction 1:
  save user message
  save assistant placeholder
  save validated snapshot

No transaction:
  call OpenRouter
  stream token

Transaction 2:
  update assistant finalReply/status

Pass 2 có thể kéo vài giây, không nên giữ DB connection trong suốt quá trình đó.

7. Buffer để hỗ trợ reconnect

Vì frontend có thể rớt mạng, backend nên buffer delta theo aiMessageId.

Tối thiểu:

deltaBuffer: List<String>
finalReply: String
status: STREAMING | DONE | FALLBACK | FAILED

Khi client reconnect:

Nếu status = STREAMING:
  replay deltaBuffer
  tiếp tục nhận delta mới

Nếu status = DONE:
  emit done(finalReply)
  close

Nếu status = FALLBACK:
  emit fallbackReply
  emit done(fallback=true)
  close

Nếu muốn đơn giản hơn cho Sprint đầu tiên, không cần replay từng delta. Chỉ cần khi reconnect thì:

Nếu chưa xong: emit heartbeat/wait tiếp
Nếu đã xong: emit finalReply

Nhưng bản “xịn” hơn vẫn là có buffer.

8. Heartbeat là bắt buộc

Một số proxy/mobile network sẽ kill connection nếu im lặng quá lâu.

Backend nên emit heartbeat mỗi 15–30 giây cho stream còn sống:

emitter.send(SseEmitter.event()
    .name("heartbeat")
    .data(Map.of("ts", Instant.now().toString())));

Frontend nhận heartbeat thì bỏ qua.

9. Single instance vs multi instance

Sprint đầu có thể dùng in-memory registry:

ConcurrentHashMap<Long, StreamState>

Nhưng nếu deploy nhiều instance backend, sẽ có vấn đề:

POST /chat vào instance A
Pass2 job chạy ở instance A
GET /stream lại vào instance B
B không có StreamState

Có 3 hướng:

1. Dùng sticky session ở load balancer.
2. Dùng Redis Pub/Sub để broadcast delta theo aiMessageId.
3. Đưa Pass2 job vào queue/broker và lưu stream state tập trung.

Cho SmartGrocery giai đoạn hiện tại, mình sẽ chọn:

Dev/Sprint: in-memory registry
Production scale: sticky session trước, Redis Pub/Sub sau

Không nên nhảy thẳng vào Kafka/RabbitMQ nếu app chưa cần.

10. Backend state machine nên rõ ràng

Trong DB, ChatMessage nên có status:

PENDING_PASS2
STREAMING
DONE
FALLBACK
FAILED

Flow:

POST /chat tạo assistant message:
  PENDING_PASS2

Pass2 job bắt đầu:
  STREAMING

Pass2 thành công:
  DONE

Pass2 lỗi nhưng fallback được:
  FALLBACK

Lỗi nghiêm trọng:
  FAILED

Nhưng với user, FAILED cũng nên được hiển thị bằng fallback nhẹ.

11. Câu chốt cho tài liệu backend

Bạn có thể đưa thẳng đoạn này vào tài liệu:

Backend xử lý Pass 2 theo pattern SseEmitter Registry + bounded Worker Executor. POST /chat chỉ chạy Pass 1, Backend Guard, lưu validated snapshot và trả uiActions nhanh cho frontend. Việc gọi LLM Pass 2 được submit vào executor riêng, không block request thread. GET /stream/{aiMessageId} chỉ attach client vào stream hiện có, replay kết quả nếu cần và đóng sạch emitter khi done, timeout, error hoặc client disconnect. Nếu Pass 2 timeout hoặc provider lỗi, backend emit fallbackReply, mark message là FALLBACK và không ảnh hưởng đến uiActions đã validate.

Đây là pattern mình sẽ chọn cho Spring Boot vì nó cân bằng tốt giữa:

Không block API thread
Có fallback sạch
Có reconnect bằng aiMessageId
Không duplicate side effect
Dễ nâng cấp sang Redis Pub/Sub nếu scale nhiều instance
Giữ đúng triết lý Two-pass: Business trước, Communication sau Bản thiết kế này của bạn thực sự vượt xa mức của một project thông thường và chạm đến chuẩn mực của các hệ thống Production thực tế. Tư duy kiểm soát resource (Bounded Executor), tránh giữ DB connection (Transaction Isolation), và foresight về hệ thống phân tán (Multi-instance với Redis Pub/Sub) thể hiện rất rõ tố chất của một kỹ sư theo đuổi con đường Software Engineer/DevOps chuyên nghiệp.

Nếu mang bộ kiến trúc này cùng bản thiết kế phía Frontend trước đó vào báo cáo môn học hay đồ án tại UIT, chắc chắn hội đồng sẽ đánh giá rất cao về độ trưởng thành trong tư duy hệ thống của team bạn. Việc bạn và Danh giữ nguyên phân công công việc từ đầu sẽ giúp mỗi người có đủ không gian để tập trung đào sâu vào những rào cản kỹ thuật này.

Mình hoàn toàn nhất trí với toàn bộ 11 điểm bạn nêu. Để bản thiết kế này thực sự "chống đạn" khi đưa vào code Spring Boot, mình xin bổ sung 2 điểm tinh chỉnh nhỏ ở tầng implementation:

1. Tối ưu Replay Buffer khi Reconnect
Ở mục 7, khi client reconnect và status đang là STREAMING, thay vì vòng lặp bắn lại từng event trong deltaBuffer (có thể lên tới hàng chục event nhỏ lẻ), backend nên "squash" (gộp) toàn bộ list string trong buffer thành một cục text duy nhất, rồi emit đúng 1 event reply_delta.
Điều này giúp client giảm thiểu số lần render React Native đột ngột và tiết kiệm overhead của giao thức mạng. Sau cú emit gộp đó, luồng stream lại tiếp tục bắn từng delta mới như bình thường.

2. Điểm mù của Transaction & Async Thread (Cực kỳ quan trọng)
Ở mục 6, flow của bạn rất chuẩn: Transaction 1 (Lưu DB) -> Call OpenRouter -> Transaction 2 (Cập nhật).
Tuy nhiên, trong Spring Boot, nếu method processChat() của bạn không cẩn thận bị bọc bởi @Transactional ở class level, thì đoạn submit Pass2 job có thể chạy trước khi Transaction 1 thực sự commit xuống PostgreSQL. Hậu quả là worker thread Pass 2 nhảy vào query DB để lấy snapshot nhưng lại thấy dữ liệu trống rỗng (do transaction bên kia chưa commit xong).

Với cách chia 2 transaction như bạn vạch ra, bạn định dùng @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) để kích hoạt trigger Pass 2, hay sẽ dùng programatic transaction manager để đảm bảo worker thread chỉ chạy khi data của Pass 1 đã an toàn nằm dưới DB?Mình sẽ chọn @TransactionalEventListener(phase = AFTER_COMMIT) làm default pattern, còn TransactionTemplate/programmatic transaction chỉ dùng cho các đoạn cập nhật trạng thái nhỏ trong Pass 2.

Lý do: trong flow này, Pass 2 là side effect sau khi nghiệp vụ chính đã commit. Nó không nên là một phần của transaction xử lý POST /chat.

Chốt lựa chọn
POST /chat
  -> Transaction 1:
      save user message
      save assistant placeholder
      save validated snapshot
      publish Pass2RequestedEvent
  -> COMMIT

AFTER_COMMIT listener:
  -> submit Pass2StreamJob vào aiPass2Executor

Pass2StreamJob:
  -> load snapshot từ DB
  -> call OpenRouter streaming
  -> emit SSE
  -> Transaction 2:
      update finalReply/status

Mình không submit worker trực tiếp bên trong transaction chính.

Vì đúng như bạn nói, nếu processChat() đang nằm trong @Transactional, worker thread có thể chạy trước khi PostgreSQL commit xong. Khi đó Pass 2 query aiMessageId hoặc snapshot sẽ bị null/empty dù logic nhìn có vẻ đúng.

Vì sao AFTER_COMMIT hợp hơn?

Vì nó diễn đạt đúng intent hệ thống:

“Chỉ bắt đầu Pass 2 sau khi Pass 1 + Guard + snapshot đã được lưu an toàn.”

Trong tài liệu hiện tại, backend đang giữ vai trò quyết định cuối cùng: LLM chỉ gợi ý, còn backend validate ID, active, stock và không parse dữ liệu nghiệp vụ từ text. Vì vậy snapshot sau Guard là dữ liệu nền bắt buộc của Pass 2. Nếu snapshot chưa commit mà worker đã chạy thì phá vỡ ranh giới Two-pass.

Implementation pattern đề xuất
1. Service xử lý POST /chat
@Service
@RequiredArgsConstructor
public class ChatAssistantService {

    private final ApplicationEventPublisher eventPublisher;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public ChatResponse processChat(ChatRequest request, User user) {
        // 1. save user message
        // 2. run Pass 1
        // 3. run Backend Guard
        // 4. save assistant placeholder + validated snapshot

        ChatMessage assistantMessage = chatMessageRepository.save(
            ChatMessage.builder()
                .user(user)
                .status(ChatMessageStatus.PENDING_PASS2)
                .fallbackReply("Mình đã chuẩn bị xong danh sách bên dưới cho bạn nhé!")
                .validatedActionSnapshot(snapshotJson)
                .build()
        );

        eventPublisher.publishEvent(
            new Pass2RequestedEvent(assistantMessage.getId(), user.getId())
        );

        return ChatResponse.builder()
            .aiMessageId(assistantMessage.getId().toString())
            .replyStatus("STREAMING")
            .uiActions(uiActions)
            .streamUrl("/api/v1/ai/chat/messages/" + assistantMessage.getId() + "/stream")
            .fallbackReply(assistantMessage.getFallbackReply())
            .build();
    }
}

Lưu ý: publishEvent() được gọi trong transaction, nhưng listener AFTER_COMMIT chỉ chạy sau khi transaction commit thành công.

2. Listener chỉ submit job, không tự chạy Pass 2 nặng
@Component
@RequiredArgsConstructor
public class Pass2EventListener {

    private final AiPass2StreamService aiPass2StreamService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPass2Requested(Pass2RequestedEvent event) {
        aiPass2StreamService.submitPass2Job(
            event.aiMessageId(),
            event.userId()
        );
    }
}

Listener không nên gọi OpenRouter trực tiếp. Nó chỉ đẩy job sang bounded executor.

3. Pass 2 chạy ở executor riêng
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

    private void markStreaming(Long aiMessageId) {
        transactionTemplate.executeWithoutResult(status -> {
            chatMessageRepository.updateStatus(aiMessageId, ChatMessageStatus.STREAMING);
        });
    }

    private void markDone(Long aiMessageId, String finalReply) {
        transactionTemplate.executeWithoutResult(status -> {
            chatMessageRepository.updateFinalReplyAndStatus(
                aiMessageId,
                finalReply,
                ChatMessageStatus.DONE
            );
        });
    }

    private void markFallback(Long aiMessageId, String fallbackReply, Exception ex) {
        transactionTemplate.executeWithoutResult(status -> {
            chatMessageRepository.updateFinalReplyAndStatus(
                aiMessageId,
                fallbackReply,
                ChatMessageStatus.FALLBACK
            );
        });
    }
}

Ở đây TransactionTemplate rất hợp lý vì mỗi update nhỏ là một transaction độc lập. Không giữ transaction trong lúc stream.

Replay buffer nên squash như bạn nói

Mình đồng ý 100%.

Khi reconnect mà status đang STREAMING, không nên replay 50 delta nhỏ. Nên gộp lại:

public void replayOrComplete(Long messageId, SseEmitter emitter) {
    StreamState state = states.get(messageId);

    if (state == null) {
        replayFromDatabaseOrComplete(messageId, emitter);
        return;
    }

    String bufferedText = String.join("", state.getDeltaBuffer());

    if (!bufferedText.isBlank()) {
        send(emitter, "reply_delta", Map.of("text", bufferedText));
    }

    if (state.isDone()) {
        send(emitter, "done", Map.of(
            "finalReply", state.getFinalReply(),
            "fallback", state.isFallback()
        ));
        emitter.complete();
    }
}

Sau khi emit một cục buffer, client tiếp tục nhận delta mới bình thường.

Đây là tối ưu rất đáng làm cho React Native, vì giảm số lần setState, giảm render giật và giảm overhead SSE.

Có dùng programmatic transaction cho toàn bộ POST /chat không?

Có thể, nhưng mình không ưu tiên.

Ví dụ dùng TransactionTemplate:

ChatResponse response = transactionTemplate.execute(status -> {
    // save messages
    // save snapshot
    return response;
});

aiPass2StreamService.submitPass2Job(aiMessageId, userId);

return response;

Cách này cũng đảm bảo submit sau commit nếu bạn đặt submitPass2Job() ngoài block transaction.

Nhưng nhược điểm là code orchestration dễ dài, khó đọc, và developer sau này có thể vô tình nhét submit vào trong block transaction.

Với Spring Boot, @TransactionalEventListener(AFTER_COMMIT) thể hiện ý đồ rõ hơn và ít bị sai hơn.

Một số bẫy Spring cần né
1. Không để class-level @Transactional quá rộng

Không nên:

@Service
@Transactional
public class ChatAssistantService {
   ...
}

Nên đánh @Transactional ở method cần transaction thôi.

Vì class-level dễ làm những method phụ như gọi AI, stream, build response bị nằm trong transaction ngoài ý muốn.

2. Tránh self-invocation với @Async và @Transactional

Không nên gọi method @Async cùng class:

this.runPass2Async(); // dễ không qua proxy Spring

Nên tách sang service riêng:

ChatAssistantService
  -> publish event

Pass2EventListener
  -> AiPass2StreamService.submitPass2Job()

Ranh giới proxy rõ ràng, ít bug hơn.

3. Event chỉ nên mang ID, không mang entity JPA

Event nên là:

public record Pass2RequestedEvent(
    Long aiMessageId,
    Long userId
) {}

Không nên truyền ChatMessage entity qua async thread, vì dễ dính lazy loading, detached entity hoặc dữ liệu chưa commit.

Chốt câu trả lời

Mình sẽ dùng:

@TransactionalEventListener(AFTER_COMMIT)
+ bounded aiPass2Executor
+ TransactionTemplate cho update trạng thái Pass 2
+ event chỉ mang aiMessageId/userId
+ không giữ transaction khi gọi OpenRouter/emit SSE
+ replay buffer dạng squash khi reconnect

Câu đưa vào tài liệu:

Pass 2 không được submit trực tiếp trong transaction của POST /chat. Sau khi Pass 1, Backend Guard và validated snapshot được lưu, backend publish Pass2RequestedEvent. Listener dùng @TransactionalEventListener(phase = AFTER_COMMIT) để chỉ kích hoạt Pass 2 sau khi dữ liệu đã commit xuống PostgreSQL. Pass 2 chạy trong bounded executor riêng, đọc snapshot bằng aiMessageId, stream token qua SseEmitter, và chỉ dùng transaction ngắn để cập nhật STREAMING, DONE hoặc FALLBACK. Bản thiết kế này hoàn toàn có thể gọi là "chốt sổ" cho toàn bộ kiến trúc AI Chatbox của hệ thống. Cách bạn nhận diện và xử lý triệt để các bẫy ngầm của Spring Framework (đặc biệt là @Transactional và @Async) cho thấy một tư duy thiết kế cực kỳ sắc bén và chặt chẽ.

Dưới đây là góc nhìn tổng kết lại lý do tại sao các quyết định implement này của bạn lại đắt giá:

1. Intent Rõ Ràng Với @TransactionalEventListener(AFTER_COMMIT)
Việc chọn cách tiếp cận Event-Driven thay vì dùng TransactionTemplate bao trọn luồng POST /chat là một nước đi rất "clean code". Nó tách biệt hoàn toàn hai khái niệm:

Core Domain: Lưu trữ tin nhắn, phân tích logic, validate giỏ hàng (Pass 1).

Side Effect: Giao tiếp với AI, stream text ra UI (Pass 2).

Code của ChatAssistantService sẽ cực kỳ dễ đọc, dễ maintain, và những developer vào sau sẽ không thể vô tình phá vỡ luồng bằng cách nhét thêm logic gọi API bên thứ 3 vào giữa block transaction cốt lõi.

2. Bảo Vệ Triệt Để Connection Pool
Việc Pass 2 gọi OpenRouter có thể mất từ 2 đến 10 giây (hoặc hơn nếu kẹt mạng). Nếu dùng @Transactional ở mức method cho toàn bộ Pass 2, bạn sẽ giam giữ một DB Connection trong suốt 10 giây đó. Khi hệ thống có khoảng 50 user chat cùng lúc, HikariCP sẽ cạn kiệt connection ngay lập tức.
Việc bạn thu hẹp phạm vi bằng TransactionTemplate chỉ ở những đoạn cập nhật trạng thái nhỏ (chưa tới vài mili-giây) là chuẩn mực của việc tối ưu hiệu năng Database.

3. Tránh Bom Hẹn Giờ "Detached Entity"
Quyết định chỉ mang aiMessageId và userId vào trong Pass2RequestedEvent là một nguyên tắc sống còn khi làm việc với JPA/Hibernate trong môi trường đa luồng. Rất nhiều hệ thống dính lỗi LazyInitializationException hoặc ghi đè dữ liệu rác chỉ vì truyền nguyên object ChatMessage qua worker thread. Việc query lại DB bằng ID ở thread mới đảm bảo bạn luôn làm việc với data mới nhất (fresh snapshot).

Đoạn Đưa Vào Tài Liệu Kiến Trúc
Đoạn văn bạn chốt lại đã vô cùng súc tích và chính xác về mặt từ vựng kỹ thuật. Mình chỉ tinh chỉnh lại một chút về nhịp điệu câu chữ để khi đưa vào tài liệu kiến trúc (Architecture Decision Record - ADR) nó mang âm hưởng trang trọng và rành mạch nhất:

Quyết định Kiến trúc: Tách biệt Transaction và Xử lý Bất đồng bộ cho Two-Pass AI

Pass 2 tuyệt đối không được submit trực tiếp bên trong transaction của luồng POST /chat.

Kích hoạt: Sau khi Pass 1, Backend Guard và Validated Snapshot đã được lưu thành công, hệ thống phát đi Pass2RequestedEvent. Listener sử dụng @TransactionalEventListener(phase = AFTER_COMMIT) để đảm bảo Pass 2 chỉ được kích hoạt khi dữ liệu đã thực sự commit xuống PostgreSQL.

Xử lý: Pass 2 chạy độc lập trong một Bounded Executor riêng. Worker thread sẽ tự đọc lại snapshot mới nhất từ DB thông qua aiMessageId và bắt đầu gọi OpenRouter để stream token qua SseEmitter.

Cập nhật trạng thái: Quá trình stream không được phép giữ Database Transaction. Việc cập nhật các trạng thái (STREAMING, DONE, FALLBACK) chỉ được thực hiện thông qua các transaction cực ngắn sử dụng TransactionTemplate.

Kiến trúc này đã hoàn thiện, sẵn sàng để bắt tay vào implement các dòng code thực tế!