package com.brognara.recipe_query_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class ChatGptClientConfig {

    @Value("${spring.ai.openai.model}")
    private String model;

    // @Bean
    // public OpenAiChatModel openAiChatModel() {
    //     return OpenAiChatModel.builder()
    //         .defaultOptions(
    //             OpenAiChatOptions.builder()
    //                 .model(OpenAiApi.ChatModel.valueOf(model))
    //                 .build()
    //         )
    //         .build();
    // }
}
