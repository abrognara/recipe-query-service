package com.brognara.recipe_query_service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import com.brognara.recipe_query_service.service.OpenAiStreamResponseParser;
import lombok.extern.log4j.Log4j2;

import reactor.core.publisher.Flux;

@Log4j2
public class TestDataUtils {

    private static final String RECIPE_DETAILS_RESPONSE_FILE_PATH = "src/test/resources/recipe-details-sample-response.txt";
    private static final String RECIPE_OVERVIEW_RESPONSE_FILE_PATH = "src/test/resources/recipes-overview-sample-response.txt";
    
    public static Flux<String> generateRecipeDetailsResponseStream() {
        return generateMockResponseStream(RECIPE_DETAILS_RESPONSE_FILE_PATH);
    }

    public static Flux<String> generateRecipeOverviewResponseStream() {
        return generateMockResponseStream(RECIPE_OVERVIEW_RESPONSE_FILE_PATH);
    }

    private static Flux<String> generateMockResponseStream(final String filePath) {
        try {
            return Flux.fromStream(
                Files.lines(Paths.get(filePath))
                    .filter(line -> line.contains("Received line:"))
                    .map(line -> line.substring(line.indexOf("Received line:") + "Received line:".length()).trim())
            );
        } catch (IOException e) {
            log.error("Error reading sample response file: {}", e.toString());
            return Flux.empty();
        }
    }

    public static Flux<String> getRecipeDetailsResponseTokenStream(final OpenAiStreamResponseParser openAiStreamResponseParser) {
        return generateRecipeDetailsResponseStream()
            .delayElements(Duration.ofMillis(5))
            .flatMap(body -> openAiStreamResponseParser.parse(body));
    }

    public static Flux<String> getRecipeOverviewResponseTokenStream(final OpenAiStreamResponseParser openAiStreamResponseParser) {
        return generateRecipeOverviewResponseStream()
            .delayElements(Duration.ofMillis(5))
            .flatMap(body -> openAiStreamResponseParser.parse(body));
    }

}
