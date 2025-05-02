package com.brognara.recipe_query_service.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Log4j2
@Service
public class OpenAiResponsesApiService {

    @Value("${spring.ai.openai.model}")
    private String model;

    private final WebClient openAiWebClient;

    @Autowired
    public OpenAiResponsesApiService(WebClient openAiWebClient) {
        this.openAiWebClient = openAiWebClient;
    }

    public Mono<String> getOpenAiResponseWebSearch(final String userPrompt) {
        final Map<String, Object> requestBody = Map.of(
                "model", model,
                "input", userPrompt,
                "tools", List.of(
                        Map.of("type", "web_search")
                ),
                "tool_choice", "required"
        );

        return openAiWebClient.post()
                .uri("/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .flatMap(err -> {
                                    log.info("OpenAi request failed: {}", err);
                                    return Mono.error(new RuntimeException("OpenAi request failed: " + err));
                                })
                )
                .bodyToMono(String.class);
    }
}
