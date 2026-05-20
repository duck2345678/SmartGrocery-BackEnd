package com.smartgrocery.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import io.netty.resolver.DefaultAddressResolverGroup;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class OpenRouterConfig {

    @Value("${openrouter.api-keys:}")
    private String apiKeys;

    @Value("${openrouter.model:deepseek-v4-flash}")
    private String model;

    @Value("${openrouter.pass1-model:deepseek-v4-flash}")
    private String pass1Model;

    @Value("${ai.provider:openrouter}")
    private String provider;

    @Value("${deepseek.api-key:}")
    private String deepseekApiKey;

    @Bean
    public WebClient openRouterWebClient() {
        HttpClient httpClient = HttpClient.create()
                .resolver(DefaultAddressResolverGroup.INSTANCE)
                .responseTimeout(Duration.ofSeconds(45));

        String baseUrl;
        if ("deepseek".equalsIgnoreCase(provider)) {
            baseUrl = "https://api.deepseek.com/";
        } else if ("gemini".equalsIgnoreCase(provider) || (apiKeys != null && apiKeys.trim().startsWith("AIzaSy"))) {
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/";
        } else {
            baseUrl = "https://openrouter.ai/api/v1/";
        }

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("HTTP-Referer", "https://smartgrocery.app")
                .defaultHeader("X-Title", "SmartGrocery AI Assistant")
                .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    /**
     * Returns the API key(s) to use.
     * When provider is "deepseek", returns the single DeepSeek API key.
     * Otherwise returns the comma-separated OpenRouter/Gemini keys.
     */
    public String[] getApiKeys() {
        if ("deepseek".equalsIgnoreCase(provider)) {
            if (deepseekApiKey != null && !deepseekApiKey.isBlank()) {
                return new String[]{deepseekApiKey};
            }
            return new String[0];
        }
        if (apiKeys == null || apiKeys.isBlank()) return new String[0];
        return apiKeys.split(",");
    }

    public String getModel() {
        if ("gemini".equalsIgnoreCase(provider) || (apiKeys != null && apiKeys.trim().startsWith("AIzaSy"))) {
            if (model != null && model.contains("/")) {
                return model.substring(model.lastIndexOf("/") + 1).replace(":free", "");
            }
        }
        return model;
    }

    public String getPass1Model() {
        String m = (pass1Model != null && !pass1Model.isBlank()) ? pass1Model : model;
        if ("gemini".equalsIgnoreCase(provider) || (apiKeys != null && apiKeys.trim().startsWith("AIzaSy"))) {
            if (m != null && m.contains("/")) {
                return m.substring(m.lastIndexOf("/") + 1).replace(":free", "");
            }
        }
        return m;
    }

    public String getProvider() {
        return provider;
    }

    public String getDeepseekApiKey() {
        return deepseekApiKey;
    }
}
