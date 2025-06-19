package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.RecipeQuerySession;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Log4j2
@Service
public class LocalRecipeQuerySessionService implements RecipeQuerySessionService {

    private final ConcurrentMap<String, RecipeQuerySession> recipeQuerySessionMap = new ConcurrentHashMap<>();

    public Mono<String> createSession(final String userId, final String userQuery) {
        final RecipeQuerySession newSession = RecipeQuerySession.builder()
                .sessionId(UUID.randomUUID().toString())
                .userId(userId)
                .userQuery(userQuery)
                .build();
        log.info("Created new session: {}", newSession);

        recipeQuerySessionMap.put(
                newSession.getSessionId(),
                newSession
        );

        return Mono.just(newSession.getSessionId());
    }

    public Mono<RecipeQuerySession> getSession(final String sessionId) {
        return Mono.just(recipeQuerySessionMap.get(sessionId));
    }

    @Override
    public void updatePrevOpenAiRequestId(final String sessionId, final String prevOpenAiRequestId) {
        recipeQuerySessionMap.get(sessionId)
                .setPrevOpenAiResponseId(prevOpenAiRequestId);
    }

    public void endSession(final String sessionId) {
        recipeQuerySessionMap.remove(sessionId);
    }

}
