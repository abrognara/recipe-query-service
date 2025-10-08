package com.brognara.recipe_query_service.controller;

import com.brognara.recipe_query_service.model.Conversation;
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
import java.util.UUID;

@Log4j2
@RestController
@RequestMapping("/api/v1")
public class QueryController {

    private static final String OPENAI_REQUEST_ID = "OPENAI_REQUEST_ID";

    private final OpenAiResponsesApiService openAiResponsesApiService;
    private final RecipeQuerySessionService recipeQuerySessionService;
    private final ConversationSessionService conversationSessionService;

    @Autowired
    public QueryController(
            RequestValidatorService validatorService, PantryService pantryService,
            OpenAiResponsesApiService openAiResponsesApiService, RecipeQuerySessionService recipeQuerySessionService, ConversationSessionService conversationSessionService
    ) {
        this.openAiResponsesApiService = openAiResponsesApiService;
        this.recipeQuerySessionService = recipeQuerySessionService;
        this.conversationSessionService = conversationSessionService;
    }

    @PostMapping(value = "/query/standard-test", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> standardTest(
            @RequestBody final String userPrompt,
            @RequestHeader("X-User-Id") final String userId,
            @RequestHeader("X-User-Roles") final String userRoles
    ) {
        final String requestId = UUID.randomUUID().toString();
        return recipeQuerySessionService.createSession(userId, userPrompt)
                .flatMapMany(sessionId -> {
                    log.info("REQUEST_ID={} ; SESSION_ID={} ; Created session for user {}",
                            requestId, sessionId, userId);
                    return openAiResponsesApiService.getOpenAiResponseStandard(requestId, userPrompt);
                });
    }

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

                    // TODO enhance new user query if needed

                    final Flux<String> eventStream =
                            openAiResponsesApiService.getOpenAiResponseWebSearch(requestId, userPrompt);
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
                            openAiResponsesApiService.getOpenAiResponseWebSearchNextResults(
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