package com.brognara.recipe_query_service.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SimpleRecipeQuerySessionRequest {
    private String query;
//    private boolean usePantry;
//    private boolean useAppliancesOwned;
//    private DifficultyLevel difficultyLevel;

    public enum DifficultyLevel {
        SIMPLE("simple"),
        MODERATE("moderately sophisticated"),
        COMPLEX("complex")
        ;

        private final String description;

        DifficultyLevel(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
} 