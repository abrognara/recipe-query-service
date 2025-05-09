package com.brognara.recipe_query_service.controller;

import com.brognara.recipe_query_service.model.RecipeQueryRequest;
import com.brognara.recipe_query_service.service.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.http.MediaType;

import java.util.UUID;

import reactor.util.function.Tuple2;

@Log4j2
@RestController
@RequestMapping("/api")
public class QueryController {

    private final RequestValidatorService validatorService;
    private final PantryService pantryService;
    private final OpenAiStreamingChatService openAiStreamingChatService;
    private final RecipeDetailsResponseConverter recipeDetailsResponseConverter;
    private final RecipesOverviewResponseConverter recipesOverviewResponseConverter;
    private final OpenAiResponsesApiService openAiResponsesApiService;

    @Autowired
    public QueryController(RequestValidatorService validatorService,
                           PantryService pantryService, OpenAiStreamingChatService openAiStreamingChatService,
                           RecipeDetailsResponseConverter recipeDetailsResponseConverter,
                           RecipesOverviewResponseConverter recipesOverviewResponseConverter, OpenAiResponsesApiService openAiResponsesApiService) {
        this.validatorService = validatorService;
        this.pantryService = pantryService;
        this.openAiStreamingChatService = openAiStreamingChatService;
        this.recipeDetailsResponseConverter = recipeDetailsResponseConverter;
        this.recipesOverviewResponseConverter = recipesOverviewResponseConverter;
        this.openAiResponsesApiService = openAiResponsesApiService;
    }

    @PostMapping(value = "/query/details", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> queryStream(@RequestBody final RecipeQueryRequest request) {
        final String requestId = UUID.randomUUID().toString();
        return openAiStreamingChatService.streamDetailsChatCompletion(request)
            .flatMap(token -> recipeDetailsResponseConverter.parse(requestId, token));
    }

    @PostMapping(value = "/query/overview", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> queryRecipesOverview(@RequestBody final RecipeQueryRequest request) {
        // TODO maybe do tiered validation
        // first check if basic request is valid, then check if pantry is valid and chat is valid in parallel
        final String requestId = UUID.randomUUID().toString();
        return validatorService.validateRequest(request) // validate first
                .flatMap(validatedRequest ->
                        Mono.zip(
                                Mono.just(validatedRequest),
                                pantryService.enhanceRequestWithPantryInfo(validatedRequest)
                        )
                )
                .map(Tuple2::getT2) // Use the enhanced request
                .flatMapMany(enhancedRequest ->
                        openAiStreamingChatService.streamOverviewChatCompletion(enhancedRequest)
                                .flatMap(token -> recipesOverviewResponseConverter.parse(requestId, token))
                );
    }

    @PostMapping("/query/web-search-test")
    public Flux<String> webSearchTest(@RequestBody final String userPrompt) {
        final String requestId = UUID.randomUUID().toString();
        return openAiResponsesApiService.getOpenAiResponseWebSearch(requestId, userPrompt)
                .doOnNext(response -> log.info("Web search response: {}", response));
    }
} 