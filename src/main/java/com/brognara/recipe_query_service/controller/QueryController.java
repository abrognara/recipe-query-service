package com.brognara.recipe_query_service.controller;

import com.brognara.recipe_query_service.service.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Log4j2
@RestController
@RequestMapping("/api")
public class QueryController {

    private final OpenAiResponsesApiService openAiResponsesApiService;
    private final RecipeQuerySessionService recipeQuerySessionService;

    @Autowired
    public QueryController(
            RequestValidatorService validatorService, PantryService pantryService,
            OpenAiResponsesApiService openAiResponsesApiService, RecipeQuerySessionService recipeQuerySessionService
    ) {
        this.openAiResponsesApiService = openAiResponsesApiService;
        this.recipeQuerySessionService = recipeQuerySessionService;
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
                    return openAiResponsesApiService.getOpenAiResponseStandard(requestId, sessionId, userPrompt);
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
        return recipeQuerySessionService.createSession(userId, userPrompt)
                .map(sessionId -> {
                    log.info("REQUEST_ID={} ; SESSION_ID={} ; Created session for user {}",
                            requestId, sessionId, userId);
                    final Flux<String> eventStream = openAiResponsesApiService.getOpenAiResponseWebSearch(
                            requestId, sessionId, userPrompt);

                    return ResponseEntity.ok()
                            .header("X-Session-Id", sessionId)
                            .contentType(MediaType.TEXT_EVENT_STREAM)
                            .body(eventStream);
                });
    }

    @GetMapping(value = "/query/web-search-test/next", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Mono<ResponseEntity<Flux<String>>> recipeQuerySessionNextResults(
            @RequestHeader("X-Session-Id") final String sessionId,
            @RequestHeader("X-User-Id") final String userId,
            @RequestHeader("X-User-Roles") final String userRoles
    ) {
        final String requestId = UUID.randomUUID().toString();
        log.info("[{}] GET /query/web-search-test/next ; userId={} ; userRoles={}",
                requestId, userId, userRoles);
        return recipeQuerySessionService.getSession(sessionId)
                .map(session -> {
                    log.info("REQUEST_ID={} ; SESSION_ID={} ; Created session for user {}",
                            requestId, sessionId, userId);
                    final String userQuery = "Give me 3 unique recipes that you haven't given yet for my query: "
                            + session.getUserQuery();
                    final Flux<String> eventStream = openAiResponsesApiService.getOpenAiResponseWebSearchNextResults(
                            requestId, session.getSessionId(), session.getPrevOpenAiResponseId(), userQuery);

                    return ResponseEntity.ok()
                            .header("X-Session-Id", sessionId)
                            .contentType(MediaType.TEXT_EVENT_STREAM)
                            .body(eventStream);
                });
    }

    private String createPromptForLoadNextResultsRequest() {
        return "";
    }
} 