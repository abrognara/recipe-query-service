package com.brognara.recipe_query_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class UpstashVectorConfig {

    @Value("${upstash.vector.url}")
    private String upstashVectorUrl;

    @Value("${upstash.vector.token}")
    private String upstashVectorToken;

    @Bean
    public WebClient upstashVectorClient() {
        return WebClient.builder()
                .baseUrl(upstashVectorUrl)
                .defaultHeader("Authorization", "Bearer " + upstashVectorToken)
                .build();
    }

}
