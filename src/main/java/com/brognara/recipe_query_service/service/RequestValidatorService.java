package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.RecipeQuerySessionRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class RequestValidatorService {

    public Mono<RecipeQuerySessionRequest> validateRequest(RecipeQuerySessionRequest request) {
        return Mono.just(request)
                .flatMap(req -> {
                    // Validate basic requirements
                    if (req.getQuery() == null || req.getQuery().trim().isEmpty()) {
                        return Mono.error(new IllegalArgumentException("Query cannot be empty"));
                    }
                    
                    if (req.getServings() <= 0) {
                        return Mono.error(new IllegalArgumentException("Servings must be greater than 0"));
                    }
                    
                    // Validate user preferences if they are to be used
                    if (req.isUseUserPreferences() && req.getUserPreferences() != null) {
                        if (req.getUserPreferences().getDietaryRestrictions() == null) {
                            return Mono.error(new IllegalArgumentException("Dietary restrictions cannot be null when using user preferences"));
                        }
                        
                        if (req.getUserPreferences().getSpicePreference() == null) {
                            return Mono.error(new IllegalArgumentException("Spice preference cannot be null when using user preferences"));
                        }
                        
                        if (req.getUserPreferences().getAppliancesOwned() == null) {
                            return Mono.error(new IllegalArgumentException("Appliances owned cannot be null when using user preferences"));
                        }
                    }
                    
                    return Mono.just(req);
                });
    }
} 