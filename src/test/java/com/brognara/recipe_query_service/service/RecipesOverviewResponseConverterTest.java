package com.brognara.recipe_query_service.service;

import org.junit.jupiter.api.Test;
import com.brognara.recipe_query_service.TestDataUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RecipesOverviewResponseConverterTest {

    private final RecipesOverviewResponseConverter responseConverter = new RecipesOverviewResponseConverter();
    private final OpenAiStreamResponseParser openAiStreamResponseParser
            = new OpenAiStreamResponseParser(new ObjectMapper());

    @Test
    public void testParse() {
        final String testRequestId = "testRequestId";
        TestDataUtils.getRecipeOverviewResponseTokenStream(openAiStreamResponseParser)
                .flatMap(token -> responseConverter.parse(testRequestId, token))
                .blockLast();
    }
}
