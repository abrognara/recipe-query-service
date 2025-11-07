package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.RecipeResearchResponse;
import reactor.core.publisher.Mono;

import java.util.List;

public interface VectorDbService {
    Mono<Void> upsert(final RecipeResearchResponse.Recipe recipe, final List<Double> embedding);
    public Mono<List<RecipeResearchResponse.Recipe>> query(final List<Double> vector);
}
