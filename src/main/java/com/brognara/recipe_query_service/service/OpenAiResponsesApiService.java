package com.brognara.recipe_query_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Log4j2
@Service
public class OpenAiResponsesApiService {

    @Value("${spring.ai.openai.model}")
    private String model;

    private final WebClient openAiWebClient;
    private final ObjectMapper objectMapper;
    private final OpenAiResponsesApiEventParser eventParser;

    @Autowired
    public OpenAiResponsesApiService(
            WebClient openAiWebClient, ObjectMapper objectMapper, OpenAiResponsesApiEventParser eventParser) {
        this.openAiWebClient = openAiWebClient;
        this.objectMapper = objectMapper;
        this.eventParser = eventParser;
    }

    public Flux<String> getOpenAiResponseWebSearch(final String appRequestId, final String userPrompt) {
        Object recipesOverviewJsonSchema;
        try {
            recipesOverviewJsonSchema = objectMapper.readValue(
                    new ClassPathResource("recipes-overview-response-schema.json").getInputStream(), Object.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final Map<String, Object> requestBody = Map.of(
                "model", model,
                "input", userPrompt,
                "text", Map.of("format", recipesOverviewJsonSchema),
                "tools", List.of(
                        Map.of("type", "web_search")
                ),
                "tool_choice", "required",
                "stream", true
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
                .bodyToFlux(String.class)
                .flatMap(rawResponse -> eventParser.parseEvent(appRequestId, rawResponse));
    }
}
