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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Log4j2
@Service
public class OpenAiResponsesApiService {

    private static final String SYSTEM_TEXT_FOR_PROMPT = "You are a helpful assistant that can find recipes based on the user's prompt. Return 5 recipes per request. When searching for recipes on the web, try to include unique and less-common sources where possible. All responses must satisfy the request and must be within the user's constraints.";

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

    public Flux<String> getOpenAiResponseStandard(final String appRequestId, final String userPrompt) {
        final Map<String, Object> requestBody = new HashMap<>(createStandardRequestBody(userPrompt,
                "recipes-overview-response-schema-standard.json"));
        requestBody.remove("tools");
        requestBody.remove("tool_choice");
        return getOpenAiResponseWebSearch(appRequestId, requestBody);
    }

    public Flux<String> getOpenAiResponseWebSearch(final String appRequestId, final String userPrompt) {
        final Map<String, Object> requestBody = createStandardRequestBody(
                userPrompt, "recipes-overview-response-schema-web-search.json");
        return getOpenAiResponseWebSearch(appRequestId, requestBody);
    }

    public Flux<String> getOpenAiResponseWebSearchNextResults(
            final String appRequestId, final String prevOpenAiResponseId, final String userPrompt) {
        final Map<String, Object> requestBody = new HashMap<>(createStandardRequestBody(
                userPrompt, "recipes-overview-response-schema-web-search.json"));
        requestBody.put("previous_response_id", prevOpenAiResponseId);
        return getOpenAiResponseWebSearch(appRequestId, requestBody);
    }

    private Flux<String> getOpenAiResponseWebSearch(final String appRequestId, final Map<String, Object> requestBody) {
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

    private Map<String, Object> createStandardRequestBody(final String userPrompt, final String jsonSchemaFilename) {
        Object recipesOverviewJsonSchema;
        try {
            // TODO read once on startup and reuse the object
            recipesOverviewJsonSchema = objectMapper.readValue(
                    new ClassPathResource(jsonSchemaFilename).getInputStream(), Object.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return Map.of(
                "model", model,
                "input", List.of(
                        Map.of(
                                "role", "system",
                                "content", List.of(
                                        Map.of(
                                                "type", "input_text",
                                                "text", SYSTEM_TEXT_FOR_PROMPT
                                        )
                                )
                        ),
                        Map.of(
                                "role", "user",
                                "content", List.of(
                                        Map.of(
                                                "type", "input_text",
                                                "text", userPrompt
                                        )
                                )
                        )
                ),
                "text", Map.of("format", recipesOverviewJsonSchema),
                "tools", List.of(
                        Map.of("type", "web_search")
                ),
                "tool_choice", "required",
                "stream", true
        );
    }
}
