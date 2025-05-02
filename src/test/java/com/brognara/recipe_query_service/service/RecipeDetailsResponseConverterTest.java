package com.brognara.recipe_query_service.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import java.time.Duration;

import com.brognara.recipe_query_service.TestDataUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ExtendWith(MockitoExtension.class)
public class RecipeDetailsResponseConverterTest {

    private RecipeDetailsResponseConverter responseConverter = new RecipeDetailsResponseConverter();
    private ObjectMapper objectMapper = new ObjectMapper();
    private OpenAiStreamResponseParser openAiStreamResponseParser = new OpenAiStreamResponseParser(objectMapper);

    @Test
    void testParse() {
        final String testRequestId = "testRequestId";
        Flux<String> result = TestDataUtils.getRecipeDetailsResponseTokenStream(openAiStreamResponseParser)
            .flatMap(token -> responseConverter.parse(testRequestId, token));

        // StepVerifier.create(result)
        //     .expectNextCount(1)
        //     .verifyComplete();
    }
}
