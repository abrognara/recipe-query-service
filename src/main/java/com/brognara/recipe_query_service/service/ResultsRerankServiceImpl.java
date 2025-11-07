package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.RecipeResearchResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class ResultsRerankServiceImpl implements ResultsRerankService {

    @Override
    public Mono<List<RecipeResearchResponse.Recipe>> rerankResults(final List<RecipeResearchResponse.Recipe> results) {
        return Mono.just(results);
    }
}
