package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.RecipeQuerySession;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Log4j2
@Service
public class LocalRecipeQuerySessionService implements RecipeQuerySessionService {

    private final ConcurrentMap<String, RecipeQuerySession> recipeQuerySessionMap = new ConcurrentHashMap<>();

    public void createSession(final String userId, final String userQuery) {
        final RecipeQuerySession newSession = RecipeQuerySession.builder()
                .sessionId(UUID.randomUUID().toString())
                .userId(userId)
                .userQuery(userQuery)
                .build();
        log.info("Created new session: {}", newSession);

        recipeQuerySessionMap.put(
                userId,
                newSession
        );
    }

    public RecipeQuerySession getSession(final String userId) {
        return recipeQuerySessionMap.get(userId);
    }

    @Override
    public void updatePrevOpenAiRequestId(final String userId, final String prevOpenAiRequestId) {
        recipeQuerySessionMap.get(userId)
                .setPrevOpenAiResponseId(prevOpenAiRequestId);
    }

    public void endSession(final String userId) {
        recipeQuerySessionMap.remove(userId);
    }

}
