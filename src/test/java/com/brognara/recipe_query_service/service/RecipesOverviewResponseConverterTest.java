package com.brognara.recipe_query_service.service;

import org.junit.jupiter.api.Test;
import com.brognara.recipe_query_service.TestDataUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class RecipesOverviewResponseConverterTest {

    private RecipesOverviewResponseConverter responseParser = new RecipesOverviewResponseConverter();
    private OpenAiStreamResponseParser openAiStreamResponseParser = new OpenAiStreamResponseParser(new ObjectMapper());

    @Test
    public void testParse() {
        final String testRequestId = "testRequestId";
        Flux<String> result = TestDataUtils.getRecipeDetailsResponseTokenStream(openAiStreamResponseParser)
            .flatMap(token -> responseParser.parse(testRequestId, token));

        // StepVerifier.create(result)
        //     .verifyComplete();
    }
}
