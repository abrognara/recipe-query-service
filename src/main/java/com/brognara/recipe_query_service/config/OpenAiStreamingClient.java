package com.brognara.recipe_query_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAiStreamingClient {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    private static final String BASE_URL = "https://api.openai.com/v1";

    @Bean
    public WebClient openAiWebClient() {
        return WebClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

}
