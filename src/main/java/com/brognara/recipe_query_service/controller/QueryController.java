package com.brognara.recipe_query_service.controller;

import com.brognara.recipe_query_service.model.RecipeQuerySession;
import com.brognara.recipe_query_service.model.RecipeQuerySessionRequest;
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
    private final RecipeQuerySessionService recipeQuerySessionService;

    @Autowired
    public QueryController(RequestValidatorService validatorService,
                           PantryService pantryService, OpenAiStreamingChatService openAiStreamingChatService,
                           RecipeDetailsResponseConverter recipeDetailsResponseConverter,
                           RecipesOverviewResponseConverter recipesOverviewResponseConverter, OpenAiResponsesApiService openAiResponsesApiService, RecipeQuerySessionService recipeQuerySessionService) {
        this.validatorService = validatorService;
        this.pantryService = pantryService;
        this.openAiStreamingChatService = openAiStreamingChatService;
        this.recipeDetailsResponseConverter = recipeDetailsResponseConverter;
        this.recipesOverviewResponseConverter = recipesOverviewResponseConverter;
        this.openAiResponsesApiService = openAiResponsesApiService;
        this.recipeQuerySessionService = recipeQuerySessionService;
    }

    @PostMapping(value = "/query/details", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> queryStream(@RequestBody final RecipeQuerySessionRequest request) {
        final String requestId = UUID.randomUUID().toString();
        return openAiStreamingChatService.streamDetailsChatCompletion(request)
            .flatMap(token -> recipeDetailsResponseConverter.parse(requestId, token));
    }

    @PostMapping(value = "/query/overview", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> queryRecipesOverview(@RequestBody final RecipeQuerySessionRequest request) {
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

    @PostMapping(value = "/query/standard-test", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> standardTest(@RequestBody final String userPrompt) {
        final String requestId = UUID.randomUUID().toString();
        recipeQuerySessionService.createSession("user-123", userPrompt);
        return openAiResponsesApiService.getOpenAiResponseStandard(requestId, userPrompt);
    }

    @PostMapping(value = "/query/web-search-test", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> webSearchTest(@RequestBody final String userPrompt) {
        final String requestId = UUID.randomUUID().toString();
        recipeQuerySessionService.createSession("user-123", userPrompt);
        return openAiResponsesApiService.getOpenAiResponseWebSearch(requestId, userPrompt);
    }

    @GetMapping(value = "/query/web-search-test/{prevId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> recipeQuerySessionNextResults(@PathVariable final String prevId) {
        final String requestId = UUID.randomUUID().toString();
        final RecipeQuerySession session = recipeQuerySessionService.getSession("user-123");
        final String userQuery = "Give me 3 new recipes for the query: " + session.getUserQuery();
        return openAiResponsesApiService.getOpenAiResponseWebSearchNextResults(requestId, prevId, userQuery);
    }

    private String createPromptForLoadNextResultsRequest() {
        return "";
    }
} 