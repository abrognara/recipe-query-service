package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.RecipeQuerySession;
import reactor.core.publisher.Mono;

// TODO change to user sessionId (or similar) instead of userId
public interface RecipeQuerySessionService {
    Mono<String> createSession(final String userId, final String userQuery);
    Mono<RecipeQuerySession> getSession(final String sessionId);
    void updatePrevOpenAiRequestId(final String sessionId, final String prevOpenAiRequestId);
    void endSession(final String sessionId);
}
