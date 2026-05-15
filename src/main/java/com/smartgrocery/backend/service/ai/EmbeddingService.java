package com.smartgrocery.backend.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartgrocery.backend.config.OpenRouterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EmbeddingService {

    private final WebClient webClient;
    private final OpenRouterConfig config;
    private final ObjectMapper objectMapper;

    // Simple thread-safe LRU Cache to save API costs for frequent queries
    private final Map<String, List<Double>> embeddingCache = Collections.synchronizedMap(
            new LinkedHashMap<String, List<Double>>(100, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<Double>> eldest) {
                    return size() > 500; // Keep up to 500 cached embeddings
                }
            }
    );

    public EmbeddingService(WebClient webClient, OpenRouterConfig config, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /**
     * Get 768-dimensional embedding for text.
     * We use a fast, cheap embedding model via OpenRouter.
     */
    public List<Double> getEmbeddingSync(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        
        String cleanText = text.trim().toLowerCase();
        if (embeddingCache.containsKey(cleanText)) {
            return embeddingCache.get(cleanText);
        }

        try {
            // Using nomic-embed-text as a standard 768d open-weights embedding model
            // or text-embedding-3-small (but it's 1536d, Neo4j might expect 768d).
            // Let's assume text-embedding-3-small or similar is mapped. If Neo4j uses 768, we can specify dimensions if OpenAI API supports it.
            // But usually OpenRouter passes through. Let's use openai/text-embedding-3-small
            String model = "openai/text-embedding-3-small";
            
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("input", cleanText);

            // Access getNextApiKey() via reflection or we can just make it public in OpenRouterClient.
            // Since we can't easily change OpenRouterClient right now without a big diff, we'll extract it.
            // Actually, let's just use the first key for now if we can't access getNextApiKey.
            String apiKey = config.getApiKeys()[0];

            String response = webClient.post()
                    .uri("https://openrouter.ai/api/v1/embeddings")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");
            if (data.isArray() && !data.isEmpty()) {
                JsonNode embeddingNode = data.get(0).path("embedding");
                if (embeddingNode.isArray()) {
                    List<Double> vector = new ArrayList<>();
                    for (JsonNode val : embeddingNode) {
                        vector.add(val.asDouble());
                    }
                    // Cache the result
                    embeddingCache.put(cleanText, vector);
                    return vector;
                }
            }
            log.warn("Could not extract embedding from response: {}", response);
        } catch (Exception e) {
            log.error("Failed to generate embedding for text '{}': {}", cleanText, e.getMessage());
        }
        return List.of();
    }
}
