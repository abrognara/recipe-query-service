package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.RecipeQuerySession;

// TODO change to user sessionId (or similar) instead of userId
public interface RecipeQuerySessionService {
    void createSession(final String userId, final String userQuery);
    RecipeQuerySession getSession(final String userId);
    void updatePrevOpenAiRequestId(final String userId, final String prevOpenAiRequestId);
    void endSession(final String userId);
}
