package com.brognara.recipe_query_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import static com.brognara.recipe_query_service.model.ResponsesApiEventType.RESPONSE_CREATED;
import static com.brognara.recipe_query_service.model.ResponsesApiEventType.RESPONSE_OUTPUT_TEXT_DELTA;

@Log4j2
@Service
public class OpenAiResponsesApiEventParser {

    private final ResponseContextService responseContextService;
    private final ObjectMapper objectMapper;
    private final RecipesOverviewResponseConverter responseConverter;

    @Autowired
    public OpenAiResponsesApiEventParser(
            final ResponseContextService responseContextService, final ObjectMapper objectMapper,
            final RecipesOverviewResponseConverter responseConverter) {
        this.responseContextService = responseContextService;
        this.objectMapper = objectMapper;
        this.responseConverter = responseConverter;
    }

    public Flux<String> parseEvent(final String appRequestId, final String jsonResponse) {
        JsonNode jsonRoot;
        try {
            jsonRoot = objectMapper.readTree(jsonResponse);
        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON: {}", e.getMessage());
            throw new RuntimeException(e);
        }

        final String responseType = jsonRoot.get("type").asText();
        // TODO parse annotations for web links
        switch (responseType) {
            case RESPONSE_CREATED:
                final String openAiRequestId = jsonRoot.get("response").get("id").asText();
                responseContextService.initContext(appRequestId, openAiRequestId);
                return Flux.empty();
            case RESPONSE_OUTPUT_TEXT_DELTA:
                final String outputTextDelta = jsonRoot.get("delta").asText();
                return responseConverter.parse(appRequestId, outputTextDelta);
            default:
                log.info("Unmatched event: {}", responseType);
                return Flux.empty();
        }
    }

}
