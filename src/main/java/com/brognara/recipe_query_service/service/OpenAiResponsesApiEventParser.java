package com.brognara.recipe_query_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import static com.brognara.recipe_query_service.model.ResponsesApiEventType.*;

@Log4j2
@Service
public class OpenAiResponsesApiEventParser {

    private final RecipeQuerySessionService recipeQuerySessionService;
    private final ObjectMapper objectMapper;

    @Autowired
    public OpenAiResponsesApiEventParser(
            RecipeQuerySessionService recipeQuerySessionService, final ObjectMapper objectMapper) {
        this.recipeQuerySessionService = recipeQuerySessionService;
        this.objectMapper = objectMapper;
    }

    public Flux<String> parseEvent(final String appRequestId, final String jsonResponse) {
        JsonNode jsonRoot;
        try {
//            log.info("Parsing response: {}", jsonResponse);
            jsonRoot = objectMapper.readTree(jsonResponse);
        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON: {}", e.getMessage());
            throw new RuntimeException(e);
        }

        final String responseType = jsonRoot.get("type").asText();
        // TODO parse annotations for web links
        //  should be 'response.output_text.annotation.added' but its not showing up in responses
        switch (responseType) {
            case RESPONSE_CREATED:
                final String openAiRequestId = jsonRoot.get("response").get("id").asText();
                log.info("openAiRequestId={}", openAiRequestId);
                return Flux.just(
                        String.format("OPENAI_REQUEST_ID %s", openAiRequestId)
                );
//            case RESPONSE_OUTPUT_TEXT_DELTA:
//                final String outputTextDelta = jsonRoot.get("delta").asText();
            case RESPONSE_CONTENT_PART_DONE:
                final String outputText = jsonRoot.get("part").get("text").asText();
                log.info("{}", outputText);
                return Flux.just(outputText);
            default:
//                log.info("Unmatched event: {}", responseType);
                return Flux.empty();
        }
    }

}
