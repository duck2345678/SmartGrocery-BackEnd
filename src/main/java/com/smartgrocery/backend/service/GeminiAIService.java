package com.smartgrocery.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct; // Import PostConstruct
import org.springframework.beans.factory.annotation.Autowired; // Import Autowired
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.smartgrocery.backend.entity.ChatMessage;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class GeminiAIService {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.url}")
    private String geminiFullUrl; // Äá»•i tÃªn cho rÃµ nghÄ©a

    @Autowired
    private WebClient.Builder webClientBuilder; // Inject WebClient.Builder

    @Autowired
    private ObjectMapper objectMapper; // Inject ObjectMapper

    private WebClient webClient; // KhÃ´ng cÃ²n lÃ  final

    @PostConstruct // Khá»Ÿi táº¡o WebClient sau khi cÃ¡c @Value vÃ  @Autowired Ä‘Ã£ Ä‘Æ°á»£c inject
    public void init() {
        // Khá»Ÿi táº¡o WebClient vá»›i URL Ä‘áº§y Ä‘á»§ tá»« properties
        this.webClient = webClientBuilder.baseUrl(geminiFullUrl).build();
    }

    public Mono<String> getGeminiResponse(String prompt, List<ChatMessage> history) {
        try {
            List<Map<String, Object>> contents = new ArrayList<>();
            
            // Add history
            if (history != null) {
                for (ChatMessage msg : history) {
                    contents.add(Map.of(
                        "role", msg.getRole().equalsIgnoreCase("USER") ? "user" : "model",
                        "parts", List.of(Map.of("text", msg.getContent()))
                    ));
                }
            }
            
            // Add current prompt
            contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", prompt))
            ));

            Map<String, Object> requestMap = Map.of("contents", contents);
            
            return webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("key", geminiApiKey)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestMap)
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(this::extractTextFromGeminiResponse)
                    .onErrorResume(WebClientResponseException.class, e -> {
                        System.err.println("WebClient error: " + e.getResponseBodyAsString());
                        return Mono.just("Lá»—i: " + e.getResponseBodyAsString());
                    });
                    
        } catch (Exception e) {
            return Mono.just("Lá»—i cáº¥u trÃºc yÃªu cáº§u: " + e.getMessage());
        }
    }

    // Overload for backward compatibility
    public Mono<String> getGeminiResponse(String prompt) {
        return getGeminiResponse(prompt, null);
    }

    private String extractTextFromGeminiResponse(String geminiResponse) {
        try {
            JsonNode root = objectMapper.readTree(geminiResponse);
            JsonNode textNode = root.path("candidates").get(0).path("content").path("parts").get(0).path("text");
            return textNode.asText();
        } catch (Exception e) {
            // Log lá»—i hoáº·c tráº£ vá» thÃ´ng bÃ¡o lá»—i phÃ¹ há»£p
            System.err.println("Error parsing Gemini response: " + e.getMessage());
            return "Xin lá»—i, tÃ´i khÃ´ng thá»ƒ xá»­ lÃ½ yÃªu cáº§u cá»§a báº¡n lÃºc nÃ y.";
        }
    }
}
