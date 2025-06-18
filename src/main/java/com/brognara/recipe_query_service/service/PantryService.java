package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.RecipeQuerySessionRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class PantryService {

    public Mono<List<String>> getAvailableIngredients() {
        // TODO: Implement actual pantry service integration
        // This is a mock implementation that returns some common ingredients
        return Mono.just(List.of(
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

    public Mono<RecipeQuerySessionRequest> enhanceRequestWithPantryInfo(RecipeQuerySessionRequest request) {
        if (!request.isUseAvailableIngredients()) {
            return Mono.just(request);
        }

        return getAvailableIngredients()
                .map(ingredients -> {
                    request.setAvailableIngredients(ingredients);
                    return request;
                });
    }
} 