package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.RecipeQueryRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Set;

@Service
public class PantryService {

    public Mono<Set<String>> getAvailableIngredients() {
        // TODO: Implement actual pantry service integration
        // This is a mock implementation that returns some common ingredients
        return Mono.just(Set.of(
            "salt",
            "pepper",
            "olive oil",
            "garlic",
            "onion",
            "pasta",
            "rice",
            "tomatoes",
            "cheese",
            "eggs",
            "milk",
            "butter",
            "flour",
            "sugar"
        ));
    }

    public Mono<RecipeQueryRequest> enhanceRequestWithPantryInfo(RecipeQueryRequest request) {
        if (!request.isUseAvailableIngredients()) {
            return Mono.just(request);
        }

        return getAvailableIngredients()
                .map(availableIngredients -> {
                    // Add pantry information to the query
                    String pantryInfo = String.format(" I have these ingredients available: %s. ",
                            String.join(", ", availableIngredients));
                    request.setQuery(request.getQuery() + pantryInfo);
                    return request;
                });
    }
} 