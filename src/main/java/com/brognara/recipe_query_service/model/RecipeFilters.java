package com.brognara.recipe_query_service.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = false)
public class RecipeFilters {

    private String cuisine;
    private String mealType;
    private String course;

    private List<String> includeIngredients;
    private List<String> excludeIngredients;

    private List<String> includeAppliances;
    private List<String> excludeAppliances;

    private Double totalTimeMinutes;
    private Integer servings;

    private String prepDifficulty;

    private NutritionGoals nutritionGoals;

    private List<String> dietaryRestrictions;
    private List<String> flavorProfile;
    private List<String> occasion;
    private List<String> tags;

    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();

        map.put("cuisine", cuisine);
        map.put("mealType", mealType);
        map.put("course", course);

        map.put("includeIngredients", includeIngredients);
        map.put("excludeIngredients", excludeIngredients);

        map.put("includeAppliances", includeAppliances);
        map.put("excludeAppliances", excludeAppliances);

        map.put("totalTimeMinutes", totalTimeMinutes);
        map.put("servings", servings);

        map.put("prepDifficulty", prepDifficulty);

        map.put("nutritionGoals",
                nutritionGoals != null ? nutritionGoals.asMap() : null);

        map.put("dietaryRestrictions", dietaryRestrictions);
        map.put("flavorProfile", flavorProfile);
        map.put("occasion", occasion);
        map.put("tags", tags);

        return map;
    }

    public static RecipeFilters fromMap(Map<String, Object> map) {
        if (map == null) return null;

        RecipeFilters.RecipeFiltersBuilder builder = RecipeFilters.builder();

        builder.cuisine((String) map.get("cuisine"));
        builder.mealType((String) map.get("mealType"));
        builder.course((String) map.get("course"));

        builder.includeIngredients((List<String>) map.get("includeIngredients"));
        builder.excludeIngredients((List<String>) map.get("excludeIngredients"));

        builder.includeAppliances((List<String>) map.get("includeAppliances"));
        builder.excludeAppliances((List<String>) map.get("excludeAppliances"));

        // numeric conversions handled safely
        builder.totalTimeMinutes(toDouble(map.get("totalTimeMinutes")));
        builder.servings(toInteger(map.get("servings")));

        builder.prepDifficulty((String) map.get("prepDifficulty"));

        // nested object
        Map<String, Object> ng = (Map<String, Object>) map.get("nutritionGoals");
        if (ng != null) {
            builder.nutritionGoals(NutritionGoals.fromMap(ng));
        }

        builder.dietaryRestrictions((List<String>) map.get("dietaryRestrictions"));
        builder.flavorProfile((List<String>) map.get("flavorProfile"));
        builder.occasion((List<String>) map.get("occasion"));
        builder.tags((List<String>) map.get("tags"));

        return builder.build();
    }

    private static Double toDouble(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        return Double.valueOf(obj.toString());
    }

    private static Integer toInteger(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).intValue();
        return Integer.valueOf(obj.toString());
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = false)
    public static class NutritionGoals {
        private String protein;
        private String carbs;
        private String calories;

        public Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("protein", protein);
            map.put("carbs", carbs);
            map.put("calories", calories);
            return map;
        }

        public static NutritionGoals fromMap(Map<String, Object> map) {
            if (map == null) return null;

            return NutritionGoals.builder()
                    .protein((String) map.get("protein"))
                    .carbs((String) map.get("carbs"))
                    .calories((String) map.get("calories"))
                    .build();
        }
    }
}
