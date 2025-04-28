package com.brognara.recipe_query_service.service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import reactor.core.publisher.Flux;
import java.util.Map;
import java.util.List;
import org.springframework.http.MediaType;
import lombok.extern.log4j.Log4j2;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.brognara.recipe_query_service.model.RecipeQueryRequest;
import com.brognara.recipe_query_service.service.OverviewPromptBuilderService;

@Log4j2
@Service
public class OpenAiStreamingChatService {

    @Value("${spring.ai.openai.model}")
    private String model;

    private final WebClient openAiWebClient;
    private final ObjectMapper objectMapper;
    private final PromptBuilderService promptBuilderService;
    private final OverviewPromptBuilderService overviewPromptBuilderService;

    @Autowired
    public OpenAiStreamingChatService(WebClient openAiWebClient, ObjectMapper objectMapper, 
        PromptBuilderService promptBuilderService, OverviewPromptBuilderService overviewPromptBuilderService) {
        this.openAiWebClient = openAiWebClient;
        this.objectMapper = objectMapper;
        this.promptBuilderService = promptBuilderService;
        this.overviewPromptBuilderService = overviewPromptBuilderService;
    }

    public Flux<String> streamOverviewChatCompletion(final RecipeQueryRequest request) {
        final String prompt = overviewPromptBuilderService.buildPrompt(request);
        return streamChatCompletion(prompt);
    }

    public Flux<String> streamDetailsChatCompletion(final RecipeQueryRequest request) {
        final String prompt = promptBuilderService.buildPrompt(request);
        return streamChatCompletion(prompt);
    }
    
    public Flux<String> streamChatCompletion(final String prompt) {
        Map<String, Object> requestBody = Map.of(
            "model", model,
            "stream", true,
            "messages", List.of(
                Map.of("role", "user", "content", prompt)
            )
        );

        return openAiWebClient.post()
            .uri("/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToFlux(String.class)
            .flatMap(line -> {
                log.info("Received line: {}", line);
                if (line.equals("[DONE]")) return Flux.empty();
                try {
                    JsonNode root = objectMapper.readTree(line);
                    String content = root
                        .path("choices").get(0)
                        .path("delta")
                        .path("content").asText("");
                    return Flux.just(content);
                } catch (Exception e) {
                    log.error("Error parsing JSON: {}", e.getMessage());
                    return Flux.empty();
                }
            });
            // TODO add intermediate step to parse the json output in sections and return as flux
            // TODO parenthesis stack to handle nested parenthesis
    }
    
}
