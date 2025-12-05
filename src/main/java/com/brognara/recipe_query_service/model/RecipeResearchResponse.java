package com.brognara.recipe_query_service.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@ToString
@Builder
public class RecipeResearchResponse {
    private List<Recipe> recipes;

    @Getter
    @Setter
    @ToString
    @Builder
    public static class Recipe {
        private String url;
        private String description;
        private RecipeFilters filters;
    }

}
