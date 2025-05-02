package com.brognara.recipe_query_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import com.brognara.recipe_query_service.TestDataUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

public class RecipesOverviewResponseConverterTest {

    private final RecipesOverviewResponseConverter responseConverter = new RecipesOverviewResponseConverter();
    private final OpenAiStreamResponseParser openAiStreamResponseParser
            = new OpenAiStreamResponseParser(new ObjectMapper());

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testParse() {
        final String testRequestId = "testRequestId";
//        Flux<String> overviewJsonStrFlux =
                TestDataUtils.getRecipeOverviewResponseTokenStream(openAiStreamResponseParser)
                .flatMap(token -> responseConverter.parse(testRequestId, token))
                .doOnNext(jsonStr -> {
                    try {
                        JsonNode tree = objectMapper.readTree(jsonStr);
                        String name = tree.get("name").asText();
                        String cookTime = tree.get("cook_time").asText();
                        System.out.println("name = " + name + " cookTime = " + cookTime);
                        tree.get("user_missing_ingredients")
                                .forEach(n -> System.out.println("missing = " + n.asText()));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .blockLast();
    }
}
