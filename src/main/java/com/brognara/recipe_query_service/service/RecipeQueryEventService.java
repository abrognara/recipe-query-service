package com.brognara.recipe_query_service.service;

public interface RecipeQueryEventService {
    void publishQueryResponse();
    void publishQuerySessionOpened();
    void publishQuerySessionClosed();
}
