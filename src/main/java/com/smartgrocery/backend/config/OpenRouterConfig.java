package com.smartgrocery.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class OpenRouterConfig {

    @Value("${openrouter.api-keys:}")
    private String apiKeys;

    @Value("${openrouter.model:google/gemini-2.0-flash-001}")
    private String model;

    @Value("${openrouter.pass1-model:google/gemini-2.0-flash-001}")
    private String pass1Model;

    @Value("${ai.provider:openrouter}")
    private String provider;


    @Value("${deepseek.api-key:}")
    private String deepseekApiKey;

    @Bean
    public WebClient openRouterWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(45));

        return WebClient.builder()
                .baseUrl("https://openrouter.ai/api/v1")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("HTTP-Referer", "https://smartgrocery.app")
                .defaultHeader("X-Title", "SmartGrocery AI Assistant")
                .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }


    public String[] getApiKeys() {
        if (apiKeys == null || apiKeys.isBlank()) return new String[0];
        return apiKeys.split(",");
    }

    public String getModel() {
        return model;
    }

    public String getPass1Model() {
        return (pass1Model != null && !pass1Model.isBlank()) ? pass1Model : model;
    }


    public String getDeepseekApiKey() {
        return deepseekApiKey;
    }
}
