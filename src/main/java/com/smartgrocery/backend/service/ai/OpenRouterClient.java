package com.smartgrocery.backend.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartgrocery.backend.config.OpenRouterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe OpenRouter API client with:
 * - AtomicInteger key rotation (race-condition safe)
 * - Exponential backoff retry (3 attempts)
 * - Token usage tracking
 * - Strict timeout support for Pass 1
 */
@Slf4j
@Service
public class OpenRouterClient {

    private final WebClient webClient;
    private final OpenRouterConfig config;
    private final ObjectMapper objectMapper;
    private final AtomicInteger keyIndex = new AtomicInteger(0);

    public OpenRouterClient(WebClient openRouterWebClient, OpenRouterConfig config, ObjectMapper objectMapper) {
        this.webClient = openRouterWebClient;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /**
     * Gửi chat completion request đến OpenRouter với model mặc định và timeout mặc định (60s).
     */
    public Mono<AiCompletionResult> chatCompletion(String systemPrompt, List<Map<String, String>> messages) {
        return chatCompletion(systemPrompt, messages, config.getModel(), Duration.ofSeconds(60));
    }

    /**
     * Gửi chat completion request với tùy chọn model và timeout.
     * Hữu ích cho Pass 1 (cần nhanh, model rẻ) và Pass 2 (cần chất lượng, có thể lâu).
     *
     * @param systemPrompt System prompt cho AI
     * @param messages     Danh sách tin nhắn [{role, content}]
     * @param modelName    Tên model OpenRouter (vd: google/gemini-2.0-flash-exp:free)
     * @param timeout      Thời gian chờ tối đa
     * @return AiCompletionResult chứa reply text, tokens used, model used
     */
    public Mono<AiCompletionResult> chatCompletion(String systemPrompt, List<Map<String, String>> messages, String modelName, Duration timeout) {
        String finalModel = (modelName != null && !modelName.isBlank()) ? modelName : config.getModel();
        
        return Mono.defer(() -> {
            String apiKey = getNextApiKey();
            ObjectNode body = buildRequestBody(systemPrompt, messages, finalModel);

            return webClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(timeout) // Thêm timeout cứng cho request
                    .map(this::parseResponse)
                    .doOnError(WebClientResponseException.class, e -> {
                        if (e.getStatusCode().value() == 429) {
                            log.warn("Rate limited on key index {}, rotating...", keyIndex.get());
                            rotateKey();
                        }
                    });
        }).retryWhen(Retry.backoff(2, Duration.ofSeconds(1)) // Giảm số lần retry cho Pass 1 để tiết kiệm thời gian
                .filter(e -> e instanceof WebClientResponseException &&
                        ((WebClientResponseException) e).getStatusCode().value() == 429)
                .doBeforeRetry(signal -> log.info("Retrying AI request ({}), attempt {}", finalModel, signal.totalRetries() + 1))
        ).onErrorResume(e -> {
            if (e instanceof java.util.concurrent.TimeoutException || e.getCause() instanceof java.util.concurrent.TimeoutException) {
                log.warn("AI request timed out after {}ms (Model: {}). Using deterministic fallback.", timeout.toMillis(), finalModel);
            } else {
                log.error("AI request failed (Model: {}, Error: {})", finalModel, e.getMessage());
            }
            return Mono.just(AiCompletionResult.builder()
                    .reply("Xin lỗi, hệ thống AI đang bận hoặc quá tải. Vui lòng thử lại sau.")
                    .tokensUsed(0)
                    .success(false)
                    .build());
        });
    }


    private ObjectNode buildRequestBody(String systemPrompt, List<Map<String, String>> messages, String modelName) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", modelName);
        body.put("temperature", 0.3); // Set low temperature for strict instruction following
        body.put("max_tokens", 2048);
        
        // Force JSON output
        ObjectNode responseFormat = objectMapper.createObjectNode();
        responseFormat.put("type", "json_object");
        body.set("response_format", responseFormat);

        ArrayNode messagesArray = body.putArray("messages");

        // System prompt
        ObjectNode sysMsg = objectMapper.createObjectNode();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messagesArray.add(sysMsg);

        // Conversation history
        for (Map<String, String> msg : messages) {
            ObjectNode m = objectMapper.createObjectNode();
            m.put("role", msg.get("role"));
            m.put("content", msg.get("content"));
            messagesArray.add(m);
        }

        return body;
    }


    private AiCompletionResult parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.get("choices");

            String reply = "";
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                reply = choices.get(0).path("message").path("content").asText("");
            }

            int tokensUsed = 0;
            JsonNode usage = root.get("usage");
            if (usage != null) {
                tokensUsed = usage.path("total_tokens").asInt(0);
            }

            String model = root.path("model").asText(config.getModel());

            return AiCompletionResult.builder()
                    .reply(reply)
                    .tokensUsed(tokensUsed)
                    .modelUsed(model)
                    .success(true)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", e.getMessage());
            return AiCompletionResult.builder()
                    .reply("Lỗi xử lý phản hồi AI.")
                    .tokensUsed(0)
                    .success(false)
                    .build();
        }
    }


    /**
     * Thread-safe key rotation bằng AtomicInteger.
     */
    private String getNextApiKey() {
        String[] keys = config.getApiKeys();
        if (keys.length == 0) {
            throw new IllegalStateException("No OpenRouter API keys configured");
        }
        int idx = keyIndex.get() % keys.length;
        return keys[idx];
    }


    private void rotateKey() {
        keyIndex.incrementAndGet();
    }


    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AiCompletionResult {
        private String reply;
        private int tokensUsed;
        private String modelUsed;
        private boolean success;
    }
}
