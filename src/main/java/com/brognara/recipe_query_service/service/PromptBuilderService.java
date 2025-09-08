package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.SimpleRecipeQuerySessionRequest;

public interface PromptBuilderService {
    String buildPrompt(final SimpleRecipeQuerySessionRequest request);
}
