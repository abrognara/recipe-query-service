package com.brognara.recipe_query_service.model;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RecipeQuerySessionRequest {
    // TODO add mode for pantry usage: STRICT, SOME, NONE
    private String query;
    private int servings;
    private boolean useAvailableIngredients;
    private List<String> availableIngredients;
    private boolean useUserPreferences;
    private UserPreferences userPreferences;
    private DifficultyLevel difficultyLevel;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class UserPreferences {
        private List<DietaryRestriction> dietaryRestrictions;
        private SpicePreference spicePreference;
        private List<Appliance> appliancesOwned;
    }

    public enum DietaryRestriction {
        VEGETARIAN,
        VEGAN,
        GLUTEN_FREE,
        DAIRY_FREE,
        NUT_FREE,
        SHELLFISH_FREE,
        EGG_FREE,
        SOY_FREE
    }

    public enum SpicePreference {
        MILD,
        MEDIUM,
        HOT,
        VERY_HOT
    }

    public enum Appliance {
        OVEN,
        STOVE,
        MICROWAVE,
        BLENDER,
        FOOD_PROCESSOR,
        SLOW_COOKER,
        AIR_FRYER,
        GRILL
    }

    public enum DifficultyLevel {
        BEGINNER("simple"),
        INTERMEDIATE("moderately sophisticated"),
        ADVANCED("complex")
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