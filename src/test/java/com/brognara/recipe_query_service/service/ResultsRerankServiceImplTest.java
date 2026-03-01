package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.RecipeFilters;
import com.brognara.recipe_query_service.model.RecipeResearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResultsRerankServiceImplTest {

    private ResultsRerankServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ResultsRerankServiceImpl();
    }

    // ── Edge cases ───────────────────────────────────────────────────────────

    @Test
    void empty_results_returns_empty() {
        StepVerifier.create(service.rerankResults(List.of(), someQuery()))
                .expectNextMatches(List::isEmpty)
                .verifyComplete();
    }

    @Test
    void null_query_filters_returns_results_unchanged() {
        List<RecipeResearchResponse.Recipe> results = List.of(recipe("Pasta", null));
        StepVerifier.create(service.rerankResults(results, null))
                .expectNextMatches(r -> r.size() == 1)
                .verifyComplete();
    }

    @Test
    void recipe_with_null_filters_passes_hard_constraints() {
        // Fail-open: missing metadata should not discard a recipe
        RecipeResearchResponse.Recipe r = recipe("Unknown Recipe", null);
        assertThat(service.passesHardConstraints(r, someQuery())).isTrue();
    }

    // ── Hard constraints ─────────────────────────────────────────────────────

    @Nested
    class ExcludedIngredients {

        @Test
        void recipe_without_excluded_ingredient_passes() {
            RecipeFilters query = RecipeFilters.builder()
                    .excludeIngredients(List.of("peanut"))
                    .build();
            RecipeFilters recipeFilters = RecipeFilters.builder()
                    .includeIngredients(List.of("chicken", "garlic", "olive oil"))
                    .build();
            assertThat(service.passesHardConstraints(recipe("Chicken Garlic", recipeFilters), query)).isTrue();
        }

        @Test
        void recipe_with_excluded_ingredient_is_rejected() {
            RecipeFilters query = RecipeFilters.builder()
                    .excludeIngredients(List.of("peanut"))
                    .build();
            RecipeFilters recipeFilters = RecipeFilters.builder()
                    .includeIngredients(List.of("chicken", "peanut butter", "soy sauce"))
                    .build();
            // substring match: "peanut" is contained in "peanut butter"
            assertThat(service.passesHardConstraints(recipe("Thai Chicken", recipeFilters), query)).isFalse();
        }

        @Test
        void excluded_ingredient_check_is_case_insensitive() {
            RecipeFilters query = RecipeFilters.builder()
                    .excludeIngredients(List.of("PEANUT"))
                    .build();
            RecipeFilters recipeFilters = RecipeFilters.builder()
                    .includeIngredients(List.of("peanut butter"))
                    .build();
            assertThat(service.passesHardConstraints(recipe("Satay", recipeFilters), query)).isFalse();
        }

        @Test
        void no_excluded_ingredients_in_query_always_passes() {
            RecipeFilters query = RecipeFilters.builder().build();
            RecipeFilters recipeFilters = RecipeFilters.builder()
                    .includeIngredients(List.of("peanut", "shellfish"))
                    .build();
            assertThat(service.passesHardConstraints(recipe("Satay", recipeFilters), query)).isTrue();
        }
    }

    @Nested
    class DietaryRestrictions {

        @Test
        void recipe_satisfying_all_restrictions_passes() {
            RecipeFilters query = RecipeFilters.builder()
                    .dietaryRestrictions(List.of("vegan"))
                    .build();
            RecipeFilters recipeFilters = RecipeFilters.builder()
                    .dietaryRestrictions(List.of("vegan", "gluten-free"))
                    .build();
            assertThat(service.passesHardConstraints(recipe("Vegan Bowl", recipeFilters), query)).isTrue();
        }

        @Test
        void recipe_missing_required_restriction_is_rejected() {
            RecipeFilters query = RecipeFilters.builder()
                    .dietaryRestrictions(List.of("vegan"))
                    .build();
            RecipeFilters recipeFilters = RecipeFilters.builder()
                    .dietaryRestrictions(List.of("vegetarian"))
                    .build();
            assertThat(service.passesHardConstraints(recipe("Veggie Burger", recipeFilters), query)).isFalse();
        }

        @Test
        void recipe_with_no_restriction_labels_is_rejected_when_query_requires_one() {
            RecipeFilters query = RecipeFilters.builder()
                    .dietaryRestrictions(List.of("gluten-free"))
                    .build();
            RecipeFilters recipeFilters = RecipeFilters.builder().build(); // no labels
            assertThat(service.passesHardConstraints(recipe("Mystery Cake", recipeFilters), query)).isFalse();
        }

        @Test
        void dietary_restriction_check_is_case_insensitive() {
            RecipeFilters query = RecipeFilters.builder()
                    .dietaryRestrictions(List.of("VEGAN"))
                    .build();
            RecipeFilters recipeFilters = RecipeFilters.builder()
                    .dietaryRestrictions(List.of("vegan"))
                    .build();
            assertThat(service.passesHardConstraints(recipe("Vegan Tacos", recipeFilters), query)).isTrue();
        }

        @Test
        void no_dietary_restrictions_in_query_always_passes() {
            RecipeFilters query = RecipeFilters.builder().build();
            RecipeFilters recipeFilters = RecipeFilters.builder().build();
            assertThat(service.passesHardConstraints(recipe("Beef Stew", recipeFilters), query)).isTrue();
        }
    }

    @Test
    void recipes_failing_hard_constraints_are_removed_from_results() {
        RecipeFilters query = RecipeFilters.builder()
                .excludeIngredients(List.of("shellfish"))
                .build();

        List<RecipeResearchResponse.Recipe> results = List.of(
                recipe("Shrimp Pasta", RecipeFilters.builder()
                        .includeIngredients(List.of("shrimp", "pasta")).build()),
                recipe("Chicken Pasta", RecipeFilters.builder()
                        .includeIngredients(List.of("chicken", "pasta")).build())
        );

        // "shrimp" contains "shellfish"? No — let's use a direct match
        RecipeFilters directQuery = RecipeFilters.builder()
                .excludeIngredients(List.of("shrimp"))
                .build();

        StepVerifier.create(service.rerankResults(results, directQuery))
                .expectNextMatches(r -> r.size() == 1 && r.get(0).getRecipeName().equals("Chicken Pasta"))
                .verifyComplete();
    }

    // ── Soft constraint scoring ───────────────────────────────────────────────

    @Nested
    class SoftScoring {

        @Test
        void recipe_with_null_filters_scores_zero() {
            RecipeFilters query = RecipeFilters.builder().cuisine("Italian").build();
            assertThat(service.computeSoftScore(recipe("Unknown", null), query)).isEqualTo(0.0);
        }

        @Test
        void matching_cuisine_contributes_full_weight() {
            RecipeFilters query = RecipeFilters.builder().cuisine("Italian").build();
            RecipeFilters rf = RecipeFilters.builder().cuisine("Italian").build();
            // cuisine is the only specified field → should get perfect score
            assertThat(service.computeSoftScore(recipe("Pasta", rf), query)).isEqualTo(1.0);
        }

        @Test
        void mismatched_cuisine_scores_zero() {
            RecipeFilters query = RecipeFilters.builder().cuisine("Italian").build();
            RecipeFilters rf = RecipeFilters.builder().cuisine("Mexican").build();
            assertThat(service.computeSoftScore(recipe("Tacos", rf), query)).isEqualTo(0.0);
        }

        @Test
        void cuisine_match_is_case_insensitive() {
            RecipeFilters query = RecipeFilters.builder().cuisine("ITALIAN").build();
            RecipeFilters rf = RecipeFilters.builder().cuisine("italian").build();
            assertThat(service.computeSoftScore(recipe("Pasta", rf), query)).isEqualTo(1.0);
        }

        @Test
        void partial_flavor_profile_overlap_gives_partial_score() {
            // query wants 2 flavors, recipe has 1 of them → 0.5 overlap → 0.5 * W_FLAVOR_PROFILE / W_FLAVOR_PROFILE = 0.5
            RecipeFilters query = RecipeFilters.builder()
                    .flavorProfile(List.of("spicy", "savory"))
                    .build();
            RecipeFilters rf = RecipeFilters.builder()
                    .flavorProfile(List.of("spicy", "sweet"))
                    .build();
            double score = service.computeSoftScore(recipe("Spicy Dish", rf), query);
            assertThat(score).isEqualTo(0.5); // 1 of 2 matched
        }

        @Test
        void full_flavor_profile_overlap_gives_full_score() {
            RecipeFilters query = RecipeFilters.builder()
                    .flavorProfile(List.of("spicy", "savory"))
                    .build();
            RecipeFilters rf = RecipeFilters.builder()
                    .flavorProfile(List.of("spicy", "savory", "umami"))
                    .build();
            assertThat(service.computeSoftScore(recipe("Dish", rf), query)).isEqualTo(1.0);
        }

        @Test
        void unspecified_query_fields_do_not_affect_score() {
            // Only mealType specified; recipe matches perfectly → score = 1.0
            // Other fields (cuisine, flavorProfile, etc.) are null in query and should not count
            RecipeFilters query = RecipeFilters.builder().mealType("dinner").build();
            RecipeFilters rf = RecipeFilters.builder()
                    .mealType("dinner")
                    .cuisine("Japanese")
                    .flavorProfile(List.of("umami"))
                    .build();
            assertThat(service.computeSoftScore(recipe("Ramen", rf), query)).isEqualTo(1.0);
        }

        @Test
        void results_sorted_by_soft_score_descending() {
            RecipeFilters query = RecipeFilters.builder()
                    .cuisine("Italian")
                    .mealType("dinner")
                    .build();

            RecipeResearchResponse.Recipe highMatch = recipe("Pasta", RecipeFilters.builder()
                    .cuisine("Italian").mealType("dinner").build());
            RecipeResearchResponse.Recipe partialMatch = recipe("Pizza", RecipeFilters.builder()
                    .cuisine("Italian").mealType("lunch").build());
            RecipeResearchResponse.Recipe noMatch = recipe("Tacos", RecipeFilters.builder()
                    .cuisine("Mexican").mealType("lunch").build());

            // Deliberately pass them in reverse order to verify sorting
            List<RecipeResearchResponse.Recipe> results = List.of(noMatch, partialMatch, highMatch);

            StepVerifier.create(service.rerankResults(results, query))
                    .expectNextMatches(r -> {
                        assertThat(r.get(0).getRecipeName()).isEqualTo("Pasta");
                        assertThat(r.get(1).getRecipeName()).isEqualTo("Pizza");
                        assertThat(r.get(2).getRecipeName()).isEqualTo("Tacos");
                        return true;
                    })
                    .verifyComplete();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private RecipeResearchResponse.Recipe recipe(String name, RecipeFilters filters) {
        return RecipeResearchResponse.Recipe.builder()
                .recipeName(name)
                .url("https://example.com/" + name.toLowerCase().replace(" ", "-"))
                .description("A delicious " + name)
                .filters(filters)
                .build();
    }

    private RecipeFilters someQuery() {
        return RecipeFilters.builder().cuisine("Italian").build();
    }
}
