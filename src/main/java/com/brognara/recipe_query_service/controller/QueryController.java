package com.brognara.recipe_query_service.controller;

import com.brognara.recipe_query_service.model.RecipeQuerySession;
import com.brognara.recipe_query_service.service.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Log4j2
@RestController
@RequestMapping("/api")
public class QueryController {

    private final UserValidationService userValidationService;
    private final OpenAiResponsesApiService openAiResponsesApiService;
    private final RecipeQuerySessionService recipeQuerySessionService;

    @Autowired
    public QueryController(
            RequestValidatorService validatorService, PantryService pantryService, UserValidationService userValidationService,
            OpenAiResponsesApiService openAiResponsesApiService, RecipeQuerySessionService recipeQuerySessionService
    ) {
        this.userValidationService = userValidationService;
        this.openAiResponsesApiService = openAiResponsesApiService;
        this.recipeQuerySessionService = recipeQuerySessionService;
    }

    @PostMapping(value = "/query/standard-test", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> standardTest(@RequestBody final String userPrompt) {
        final String requestId = UUID.randomUUID().toString();
        return userValidationService.validateUser()
                .flatMap(userId ->
                        Mono.zip(
                                Mono.just(userId),
                                recipeQuerySessionService.createSession(userId, userPrompt)
                        )
                )
                .flatMapMany(userAndSessionId -> {
                    final String userId = userAndSessionId.getT1();
                    final String sessionId = userAndSessionId.getT2();
                    log.info("REQUEST_ID={} ; SESSION_ID={} ; Created session for user {}",
                            requestId, sessionId, userId);
                    return openAiResponsesApiService.getOpenAiResponseStandard(requestId, sessionId, userPrompt);
                });
    }

    @PostMapping(value = "/query/web-search-test", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> webSearchTest(@RequestBody final String userPrompt) {
        final String requestId = UUID.randomUUID().toString();
        return userValidationService.validateUser()
                .flatMap(userId ->
                        Mono.zip(
                                Mono.just(userId),
                                recipeQuerySessionService.createSession(userId, userPrompt)
                        )
                )
                .flatMapMany(userAndSessionId -> {
                    final String userId = userAndSessionId.getT1();
                    final String sessionId = userAndSessionId.getT2();
                    log.info("REQUEST_ID={} ; SESSION_ID={} ; Created session for user {}",
                            requestId, sessionId, userId);
                    return openAiResponsesApiService.getOpenAiResponseWebSearch(requestId, sessionId, userPrompt);
                });
    }

    @GetMapping(value = "/query/web-search-test/next", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> recipeQuerySessionNextResults(@RequestHeader("X-Session-Id") final String sessionId) {
        final String requestId = UUID.randomUUID().toString();
        return userValidationService.validateUser()
                .flatMap(userId ->
                        Mono.zip(
                                Mono.just(userId),
                                recipeQuerySessionService.getSession(sessionId)
                        )
                )
                .flatMapMany(userAndSessionId -> {
                    final String userId = userAndSessionId.getT1();
                    final RecipeQuerySession session = userAndSessionId.getT2();
                    log.info("REQUEST_ID={} ; SESSION_ID={} ; Created session for user {}",
                            requestId, sessionId, userId);
                    final String userQuery = "Give me 3 unique recipes that you haven't given yet for my query: "
                            + session.getUserQuery();
                    return openAiResponsesApiService.getOpenAiResponseWebSearchNextResults(
                            requestId, session.getSessionId(), session.getPrevOpenAiResponseId(), userQuery);
                });
    }

    private String createPromptForLoadNextResultsRequest() {
        return "";
    }
} 