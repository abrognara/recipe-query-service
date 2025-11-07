package com.brognara.recipe_query_service.controller;

import com.brognara.recipe_query_service.model.Conversation;
import com.brognara.recipe_query_service.model.RecipeResearchResponse;
import com.brognara.recipe_query_service.service.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

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

    @Autowired
    public QueryController(
            OpenAiResponsesService openAiResponsesService, ConversationSessionService conversationSessionService, PreProcessingService preProcessingService, OpenAiEmbeddingService openAiEmbeddingService, RecipeResearchService recipeResearchService, VectorDbService vectorDbService, ResultsRerankService resultsRerankService
    ) {
        this.openAiResponsesService = openAiResponsesService;
        this.conversationSessionService = conversationSessionService;
        this.preProcessingService = preProcessingService;
        this.openAiEmbeddingService = openAiEmbeddingService;
        this.recipeResearchService = recipeResearchService;
        this.vectorDbService = vectorDbService;
        this.resultsRerankService = resultsRerankService;
    }

    // ##################### TEST METHODS #####################

    @GetMapping("/test-embed")
    public Mono<String> testEmbedPreprocessedQuery(@RequestBody final String userPrompt) {
        final String requestId = "1234";
        return preProcessingService.preProcessQuery(requestId, userPrompt)
                .flatMap(preProcessedQuery -> openAiEmbeddingService.createVectorEmbedding(requestId, preProcessedQuery))
                .map(embedding -> "success");
    }

    @GetMapping(value = "/test-find-recipes-summarize", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> testFindRecipesSummarize(@RequestBody final String userPrompt) {
        final String requestId = "1234";
        final String enhancedPrompt = "Find 20 recipes for this recipe: " + userPrompt; // TODO simulate preprocessed prompt
        return openAiResponsesService.getOpenAiResponseWebSearch(requestId, enhancedPrompt)
                .flatMap(recipeData -> preProcessingService.preProcessRecipeDataEnhanced(requestId, recipeData));
    }

    @PostMapping("/test-research")
    public Mono<ResponseEntity<Object>> testResearchRecipes(@RequestBody final String userPrompt) {
        final String requestId = "1234";
        return Mono.zip(
                openAiEmbeddingService.createVectorEmbedding(requestId, userPrompt),
                recipeResearchService.researchRecipes(requestId, userPrompt)
        )
                .flatMap(responses -> {
                    final List<Double> userQueryEmbedding = responses.getT1();
                    final RecipeResearchResponse recipeResearchResponse = responses.getT2();
                    return Flux.fromIterable(recipeResearchResponse.getRecipes())
                            .flatMap(recipe ->
                                    openAiEmbeddingService.createVectorEmbedding(requestId, recipe.getDescription())
                                            .flatMap(recipeEmbedding -> vectorDbService.upsert(recipe, recipeEmbedding))
                            )
                            .then(
                                    // defer() waits for all items to be processed in the flux
                                    Mono.defer(() -> vectorDbService.query(userQueryEmbedding))
                            )
                            .flatMap(resultsRerankService::rerankResults);
                })
                .map(ResponseEntity::ok);
    }

    // ##################### END TEST METHODS #####################



    @PostMapping(value = "/query/web-search-test", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Mono<ResponseEntity<Flux<String>>> webSearchTest(
            @RequestBody final String userPrompt,
            @RequestHeader("X-User-Id") final String userId,
            @RequestHeader("X-User-Roles") final String userRoles
    ) {
        final String requestId = UUID.randomUUID().toString();
        log.info("[{}] POST /query/web-search-test ; userId={} ; userRoles={}",
                requestId, userId, userRoles);
        return conversationSessionService.createConversation(userId, userPrompt)
                .map(sessionId -> {
                    log.info("REQUEST_ID={} ; SESSION_ID={} ; Created session for user {}",
                            requestId, sessionId, userId);

                    preProcessingService.preProcessQuery(requestId, userPrompt)
                            .flatMap(preProcessedQuery -> openAiEmbeddingService.createVectorEmbedding(requestId, preProcessedQuery));

                    final Flux<String> eventStream =
                            openAiResponsesService.getOpenAiResponseWebSearch(requestId, userPrompt);
//                            mockOpenAiResponseWebSearch();

                    // Share the flux between "stream to client" and "aggregate for saving"
                    Flux<String> sharedStream = eventStream.publish().autoConnect(2);

                    Mono<String> saveToRedis = sharedStream
                            .collectList()
                            .flatMap(chunks -> {
                                // get the openai request id
//                                final String openAiReqIdToken = chunks.stream()
//                                        .filter(chunk -> chunk.startsWith(OPENAI_REQUEST_ID))
//                                        .findFirst()
//                                        .orElseThrow(() -> new IllegalStateException("Missing in response: " + OPENAI_REQUEST_ID));
                                final String openAiReqIdToken = "OPENAI_REQUEST_ID TEMP012345";

                                final String openAiRequestId = openAiReqIdToken.split(" ")[1];
                                log.info("openAiRequestId={}", openAiRequestId);

                                // get openai web search response without the openai request id token
                                final String fullResponse = String.join("",
                                        chunks.stream().filter(chunk -> !chunk.startsWith(OPENAI_REQUEST_ID)).toList());

                                // TODO: can parse OpenAI response JSON here
                                final Conversation.Message msg = new Conversation.Message(
                                        "response",
                                        fullResponse, // or parsed JSON object
                                        Instant.now().toEpochMilli()
                                );

                                return conversationSessionService.addFirstResponseMessage(
                                        userId, sessionId, openAiRequestId, msg
                                );
                            });

                    saveToRedis.subscribe();

                    return ResponseEntity.ok()
                            .header("X-Session-Id", sessionId)
                            .contentType(MediaType.TEXT_EVENT_STREAM)
                            .body(sharedStream);
                });
    }

    @PutMapping(value = "/query/web-search-test/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Mono<ResponseEntity<Flux<String>>> recipeQuerySessionNextPrompt(
            @RequestBody final String userPrompt,
            @RequestHeader("X-User-Id") final String userId,
            @RequestHeader("X-User-Roles") final String userRoles,
            @PathVariable final String sessionId
    ) {
        final String requestId = UUID.randomUUID().toString();
        log.info("[{}] PUT /query/web-search-test/{} ; userId={} ; userRoles={}",
                requestId, sessionId, userId, userRoles);
        return conversationSessionService.getConversation(userId, sessionId)
                .map(conversation -> {
                    log.info("REQUEST_ID={} ; SESSION_ID={} ; Created session for user {}",
                            requestId, sessionId, userId);

                    // add new user prompt
                    conversation.getConversation().add(
                            new Conversation.Message("prompt", userPrompt, Instant.now().toEpochMilli())
                    );

                    // TODO enhance new user query if needed

                    final Flux<String> eventStream =
                            openAiResponsesService.getOpenAiResponseWebSearchNextResults(
                            requestId, conversation.getOpenAiRequestId(), userPrompt);
//                            mockOpenAiResponseWebSearch();

                    // Share the flux between "stream to client" and "aggregate for saving"
                    Flux<String> sharedStream = eventStream.publish().autoConnect(2);

                    Mono<String> saveToRedis = sharedStream
                            .filter(chunk -> !chunk.startsWith(OPENAI_REQUEST_ID))
                            .collectList()
                            .flatMap(chunks -> {
                                String fullResponse = String.join("", chunks);
                                // TODO: parse OpenAI response JSON here
                                conversation.getConversation().add(
                                        new Conversation.Message(
                                                "response",
                                                fullResponse, // or parsed JSON object
                                                Instant.now().toEpochMilli()
                                        )
                                );

                                return conversationSessionService.writeConversation(userId, sessionId, conversation);
                            });

                    saveToRedis.subscribe();

                    return ResponseEntity.ok()
                            .header("X-Session-Id", sessionId)
                            .contentType(MediaType.TEXT_EVENT_STREAM)
                            .body(
                                    sharedStream
                            );
                });
    }

    private Flux<String> mockOpenAiResponseWebSearch() {
        return Flux.just("Hello", "world", "test", "done")
                .delayElements(Duration.of(1000, ChronoUnit.MILLIS));
    }

} 