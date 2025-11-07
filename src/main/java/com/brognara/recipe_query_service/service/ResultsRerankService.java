package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.RecipeResearchResponse;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ResultsRerankService {
    Mono<List<RecipeResearchResponse.Recipe>> rerankResults(List<RecipeResearchResponse.Recipe> results);
}
