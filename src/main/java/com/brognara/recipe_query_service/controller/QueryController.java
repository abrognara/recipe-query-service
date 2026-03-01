package com.brognara.recipe_query_service.controller;

import com.brognara.recipe_query_service.model.Conversation;
import com.brognara.recipe_query_service.model.RecipeFilters;
import com.brognara.recipe_query_service.model.RecipeQueryResponse;
import com.brognara.recipe_query_service.model.RecipeResearchResponse;
import com.brognara.recipe_query_service.service.*;
import jakarta.annotation.Nullable;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Log4j2
@RestController
@RequestMapping("/api/v1")
public class QueryController {

    private static final String OPENAI_REQUEST_ID = "OPENAI_REQUEST_ID";

    private final OpenAiResponsesService openAiResponsesService;
    private final ConversationSessionService conversationSessionService;
    private final PreProcessingService preProcessingService;
    private final OpenAiEmbeddingService openAiEmbeddingService;
    private final RecipeResearchService recipeResearchService;
    private final VectorDbService vectorDbService;
    private final ResultsRerankService resultsRerankService;
    private final InputSanitizationService inputSanitizationService;

    @Autowired
    public QueryController(
            OpenAiResponsesService openAiResponsesService, ConversationSessionService conversationSessionService, PreProcessingService preProcessingService, OpenAiEmbeddingService openAiEmbeddingService, RecipeResearchService recipeResearchService, VectorDbService vectorDbService, ResultsRerankService resultsRerankService, InputSanitizationService inputSanitizationService
    ) {
        this.openAiResponsesService = openAiResponsesService;
        this.conversationSessionService = conversationSessionService;
        this.preProcessingService = preProcessingService;
        this.openAiEmbeddingService = openAiEmbeddingService;
        this.recipeResearchService = recipeResearchService;
        this.vectorDbService = vectorDbService;
        this.resultsRerankService = resultsRerankService;
        this.inputSanitizationService = inputSanitizationService;
    }

    // ##################### TEST METHODS #####################

    @GetMapping("/test-embed")
    public Mono<String> testEmbedPreprocessedQuery(@RequestBody final String userPrompt) {
        final String requestId = "1234";
        return preProcessingService.preProcessQuerySemanticMeaning(requestId, userPrompt)
                .flatMap(preProcessedQuery -> openAiEmbeddingService.createVectorEmbedding(requestId, preProcessedQuery))
                .map(embedding -> "success");
    }

    @GetMapping("/test-gen-filters")
    public Mono<RecipeFilters> testGenFilters(@RequestBody final String userPrompt) {
        final String requestId = "1234";
        return preProcessingService.preProcessQueryGenerateFilters(requestId, userPrompt);
    }

//    @GetMapping(value = "/test-find-recipes-summarize", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public Flux<String> testFindRecipesSummarize(@RequestBody final String userPrompt) {
//        final String requestId = "1234";
//        final String enhancedPrompt = "Find 20 recipes for this recipe: " + userPrompt; // TODO simulate preprocessed prompt
//        return openAiResponsesService.getOpenAiResponseWebSearch(requestId, enhancedPrompt)
//                .flatMap(recipeData -> preProcessingService.preProcessRecipeDataEnhanced(requestId, recipeData));
//    }

    @PostMapping("/test-research")
    public Mono<ResponseEntity<Object>> testResearchRecipes(@RequestBody final String userPrompt) {
        final String requestId = "1234";
        return Mono.zip(
                preProcessingService.preProcessQuerySemanticMeaning(requestId, userPrompt),
                preProcessingService.preProcessQueryGenerateFilters(requestId, userPrompt),
                recipeResearchService.researchRecipes(requestId, userPrompt)
        )
                .flatMap(responses -> {
                    final String userQuerySemanticMeaning = responses.getT1();
                    final RecipeFilters userQueryFilters = responses.getT2();
                    final RecipeResearchResponse recipeResearchResponse = responses.getT3();
                    return openAiEmbeddingService.createVectorEmbedding(requestId, userQuerySemanticMeaning)
                                    .flatMap(userQueryEmbedding -> forEachRecipeCreateEmbeddingOfDescAndUpsert(requestId, recipeResearchResponse)
                                            .then(
                                                    // defer() waits for all items to be processed in the flux
                                                    Mono.defer(() -> vectorDbService.query(userQueryEmbedding, userQueryFilters))
                                            )
                                            .flatMap(vectorResults ->
                                                    resultsRerankService.rerankResults(vectorResults, userQueryFilters))
                                    );
                })
                .map(ResponseEntity::ok);
    }

    // ##################### END TEST METHODS #####################

    private Mono<Void> forEachRecipeCreateEmbeddingOfDescAndUpsert(
            final String requestId,
            final RecipeResearchResponse recipeResearchResponse
    ) {
        return Flux.fromIterable(recipeResearchResponse.getRecipes())
                .flatMap(recipe ->
                        openAiEmbeddingService.createVectorEmbedding(requestId, recipe.getDescription())
                                .flatMap(recipeEmbedding ->
                                        vectorDbService.upsert(recipe, recipeEmbedding, recipe.getFilters())
                                )
                )
                .then();
    }

    @PostMapping("/recipe-query")
    public Mono<ResponseEntity<RecipeQueryResponse>> recipeQuery(
            @RequestBody final String userPrompt,
            @RequestHeader("X-User-Id") final String userId,
            @RequestHeader("X-User-Roles") final String userRoles
    ) {
        return handleRecipeQuery(userPrompt, userId, null);
    }

    @PostMapping("/recipe-query/{chatId}")
    public Mono<ResponseEntity<RecipeQueryResponse>> recipeQueryWithPrevChat(
            @RequestBody final String userPrompt,
            @RequestHeader("X-User-Id") final String userId,
            @RequestHeader("X-User-Roles") final String userRoles,
            @PathVariable final String chatId
    ) {
        return handleRecipeQuery(userPrompt, userId, chatId);
    }

    private Mono<ResponseEntity<RecipeQueryResponse>> handleRecipeQuery(
            final String userPrompt,
            final String userId,
            @Nullable final String requestChatId
    ) {
        final String requestId = UUID.randomUUID().toString();
        final boolean fallbackToWebSearch = true;

        // need the convoId later to save recipe response
        final AtomicReference<String> conversationId = new AtomicReference<>();

        return inputSanitizationService.validate(requestId, userPrompt)
                .flatMap(sanitizedUserPrompt -> conversationSessionService.createNewOrAddToExistingConvo(userId, requestChatId, sanitizedUserPrompt)
                        .flatMap(convoId ->
                                Mono.zip(
                                        Mono.just(convoId),
                                        preProcessingService.preProcessQuerySemanticMeaning(requestId, sanitizedUserPrompt),
                                        preProcessingService.preProcessQueryGenerateFilters(requestId, sanitizedUserPrompt)
                                )
                        )
                        .flatMap(responses -> {
                            final String convoId = responses.getT1();
                            final String userQuerySemanticMeaning = responses.getT2();
                            final RecipeFilters userQueryMetadataFilters = responses.getT3();

                            conversationId.set(convoId);
                            log.info("REQUEST_ID={} ; SESSION_ID={} ; Created session for user {}",
                                    requestId, convoId, userId);

                            return openAiEmbeddingService.createVectorEmbedding(requestId, userQuerySemanticMeaning)
                                    .flatMap(userQueryEmbedding ->
                                            vectorDbService.query(userQueryEmbedding, userQueryMetadataFilters))
                                    .flatMap(vectorResults ->
                                            resultsRerankService.rerankResults(vectorResults, userQueryMetadataFilters));
                        })
                        .flatMap(rankedResults -> {
                            // TODO check scores of results
                            // check scores of results, if scores not good enough (below threshold)
                            // then fallback to web search

                            if (!fallbackToWebSearch) {
                                return Mono.just(rankedResults);
                            }

                            return recipeResearchService.researchRecipes(requestId, sanitizedUserPrompt)
                                    // upsert the new recipes into the db async and don't wait for response
                                    .doOnNext(resp ->
                                            forEachRecipeCreateEmbeddingOfDescAndUpsert(requestId, resp)
                                                    .subscribeOn(Schedulers.boundedElastic())
                                                    .subscribe()
                                    )
                                    .map(resp -> {
                                        List<RecipeResearchResponse.Recipe> combined = new ArrayList<>(resp.getRecipes());
                                        combined.addAll(rankedResults);
                                        return combined;
                                    });
                        })
                        .map(recipes -> new Conversation.Message(
                                "response",
                                recipes,
                                Instant.now().toEpochMilli()
                        ))
                        // add the query response object to the db async and don't wait for response
                        .doOnNext(message -> conversationSessionService.addMessage(
                                        userId,
                                        conversationId.get(),
                                        message
                                )
                                .subscribeOn(Schedulers.boundedElastic())
                                .subscribe())
                        .map(message -> ResponseEntity.ok(new RecipeQueryResponse(conversationId.get(), message)))
                );
    }

} 