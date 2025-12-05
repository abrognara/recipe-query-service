package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.OpenAiResponsesRequest;
import com.brognara.recipe_query_service.model.RecipeFilters;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Log4j2
@Service
public class PreProcessingService {

    private static final String PREPROCESS_QUERY_SYSTEM_PROMPT = "Convert the provided recipe query into a concise, lowercase version. Correct any spelling errors, ensure all relevant details about the recipe are preserved, and accurately capture its semantic meaning. After transforming the query, quickly verify that your output maintains key recipe information and semantic accuracy before finalizing your response. Only return the final response.\n" +
            "\n" +
            "For example, the following query \"give me a mediterranean-inspired pasta dish with chicken, but is also dairy free and high in protein\" would be converted into the following: \"mediterranean high-protein dairy-free pasta with chicken\".";

    private final OpenAiResponsesService openAiResponsesService;
    private final Map<String, Object> recipeQueryParseJsonSchema;
    private final ObjectMapper objectMapper;

    @Autowired
    public PreProcessingService(
            final OpenAiResponsesService openAiResponsesService,
            final Map<String, Object> recipeQueryParseJsonSchema,
            final ObjectMapper objectMapper
    ) {
        this.openAiResponsesService = openAiResponsesService;
        this.recipeQueryParseJsonSchema = recipeQueryParseJsonSchema;
        this.objectMapper = objectMapper;
    }

    public Mono<String> preProcessQuerySemanticMeaning(final String appRequestId, final String rawUserQuery) {
        log.info("Parse semantic meaning from query");
        final OpenAiResponsesRequest queryParseRequest = OpenAiResponsesRequest.builder()
                .model("gpt-4.1")
                .inputList(
                        List.of(
                                OpenAiResponsesRequest.Input.builder()
                                        .inputRole(OpenAiResponsesRequest.InputRole.SYSTEM)
                                        .inputContent(new OpenAiResponsesRequest.InputContent(PREPROCESS_QUERY_SYSTEM_PROMPT))
                                        .build(),
                                OpenAiResponsesRequest.Input.builder()
                                        .inputRole(OpenAiResponsesRequest.InputRole.USER)
                                        .inputContent(new OpenAiResponsesRequest.InputContent(rawUserQuery))
                                        .build()
                        )
                )
                .stream(false)
                .build();

        return openAiResponsesService.callOpenAiResponsesReturnsString(appRequestId, queryParseRequest)
                .doOnNext(response -> log.info("[{}] Successfully parsed semantic meaning query response", appRequestId))
                .map(this::parseSemanticMeaningStrFromResponse)
                .doOnNext(querySemanticMeaning -> log.info("Query semantic meaning: {}", querySemanticMeaning));
    }

    // TODO this is very similar to parsePreProcessQueryResponse()
    private String parseSemanticMeaningStrFromResponse(final String openAiResponseJson) {
        log.info("Parsing the semantic meaning str from the response json");
        try {
            JsonNode root = objectMapper.readTree(openAiResponseJson);

            // output → array
            JsonNode outputArray = root.path("output");
            if (!outputArray.isArray() || outputArray.isEmpty()) {
                throw new IllegalStateException("Missing or empty 'output' array");
            }

            // output[0] → message
            JsonNode message = outputArray.get(0);

            // content → array
            JsonNode contentArray = message.path("content");
            if (!contentArray.isArray() || contentArray.isEmpty()) {
                throw new IllegalStateException("Missing or empty 'output[0].content' array");
            }

            // Find the first node with type = output_text
            JsonNode textNode = null;
            for (JsonNode c : contentArray) {
                if ("output_text".equals(c.path("type").asText())) {
                    textNode = c.path("text");
                    break;
                }
            }

            if (textNode == null || textNode.isMissingNode()) {
                throw new IllegalStateException("No content item with type 'output_text' found");
            }

            String jsonText = textNode.asText();
            if (jsonText == null || jsonText.isEmpty()) {
                throw new IllegalStateException("'text' field is empty");
            }

            return jsonText;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse RecipeFilters from OpenAI response", e);
        }
    }

    // 'enhanced' preprocess returns metadata filters in a json object
    public Mono<RecipeFilters> preProcessQueryGenerateFilters(final String appRequestId, final String userPrompt) {
        log.info("Generate metadata filters from query");
        // TODO enhance system prompt
        final String systemPrompt = "Your only job is to interpret user queries about recipes and transform them into structured json based on the schema.";
        final OpenAiResponsesRequest queryParseRequest = OpenAiResponsesRequest.builder()
                .model("gpt-4.1")
                .inputList(
                        List.of(
                                OpenAiResponsesRequest.Input.builder()
                                        .inputRole(OpenAiResponsesRequest.InputRole.SYSTEM)
                                        .inputContent(new OpenAiResponsesRequest.InputContent(systemPrompt))
                                        .build(),
                                OpenAiResponsesRequest.Input.builder()
                                        .inputRole(OpenAiResponsesRequest.InputRole.USER)
                                        .inputContent(new OpenAiResponsesRequest.InputContent(userPrompt))
                                        .build()
                        )
                )
                .text(
                        OpenAiResponsesRequest.Text.builder()
                                .format(recipeQueryParseJsonSchema)
                                .build()
                )
                .stream(false)
                .build();

        return openAiResponsesService.callOpenAiResponsesReturnsString(appRequestId, queryParseRequest)
                .doOnNext(responseJson ->
                        log.info("Successful response from responses api for gen filters")
                )
                .map(this::parsePreProcessQueryResponse)
                .doOnNext(recipeFilters ->
                        log.info("Successfully parsed response object: {}", recipeFilters)
                );
    }

    private RecipeFilters parsePreProcessQueryResponse(final String openAiResponseJson) {
        log.info("Parsing response json into object");
        try {
            JsonNode root = objectMapper.readTree(openAiResponseJson);

            // output → array
            JsonNode outputArray = root.path("output");
            if (!outputArray.isArray() || outputArray.isEmpty()) {
                throw new IllegalStateException("Missing or empty 'output' array");
            }

            // output[0] → message
            JsonNode message = outputArray.get(0);

            // content → array
            JsonNode contentArray = message.path("content");
            if (!contentArray.isArray() || contentArray.isEmpty()) {
                throw new IllegalStateException("Missing or empty 'output[0].content' array");
            }

            // Find the first node with type = output_text
            JsonNode textNode = null;
            for (JsonNode c : contentArray) {
                if ("output_text".equals(c.path("type").asText())) {
                    textNode = c.path("text");
                    break;
                }
            }

            if (textNode == null || textNode.isMissingNode()) {
                throw new IllegalStateException("No content item with type 'output_text' found");
            }

            String jsonText = textNode.asText();
            if (jsonText == null || jsonText.isEmpty()) {
                throw new IllegalStateException("'text' field is empty");
            }

            // Now parse the inner JSON (the schema output)
            return objectMapper.readValue(jsonText, RecipeFilters.class);

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse RecipeFilters from OpenAI response", e);
        }
    }
}
