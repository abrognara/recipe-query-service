package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.RecipeFilters;
import com.brognara.recipe_query_service.model.RecipeResearchResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reranks vector DB results in two passes:
 *
 * <ol>
 *   <li><b>Hard constraint filter</b> — removes recipes that violate safety/dietary constraints
 *       from the user's query regardless of similarity score:
 *       <ul>
 *         <li>{@code excludeIngredients}: any query-excluded ingredient appearing in the recipe's
 *             ingredient list causes immediate rejection (allergy/safety).</li>
 *         <li>{@code dietaryRestrictions}: the recipe must satisfy every restriction requested by
 *             the user (e.g. "vegan", "gluten-free").</li>
 *       </ul>
 *       Note: these are also applied as Upstash metadata filters at query time, but this pass acts
 *       as a safety net for metadata gaps or normalisation mismatches.
 *   </li>
 *   <li><b>Soft constraint scoring</b> — surviving recipes are scored [0, 1] based on how closely
 *       their attributes match what the user asked for. Only fields that the user actually specified
 *       in the query contribute to the score, so a sparse query does not unfairly penalise recipes.
 *       Weights are defined in {@link #WEIGHTS}.
 *   </li>
 * </ol>
 *
 * Results are returned sorted by soft score descending (best match first).
 */
@Log4j2
@Service
public class ResultsRerankServiceImpl implements ResultsRerankService {

    /**
     * Relative importance of each soft constraint.
     * Only fields present (non-null / non-empty) in the user's query participate in scoring.
     */
    private static final double W_CUISINE = 0.25;
    private static final double W_MEAL_TYPE = 0.20;
    private static final double W_FLAVOR_PROFILE = 0.20;
    private static final double W_COURSE = 0.10;
    private static final double W_INCLUDE_INGREDIENTS = 0.10;
    private static final double W_PREP_DIFFICULTY = 0.05;
    private static final double W_OCCASION = 0.05;
    private static final double W_TAGS = 0.05;

    @Override
    public Mono<List<RecipeResearchResponse.Recipe>> rerankResults(
            final List<RecipeResearchResponse.Recipe> results,
            final RecipeFilters queryFilters
    ) {
        return Mono.fromCallable(() -> {
            if (results == null || results.isEmpty()) return results;
            if (queryFilters == null) return results;

            List<RecipeResearchResponse.Recipe> filtered = results.stream()
                    .filter(recipe -> passesHardConstraints(recipe, queryFilters))
                    .collect(Collectors.toList());

            log.info("Reranking: {} of {} results passed hard constraints",
                    filtered.size(), results.size());

            return filtered.stream()
                    .sorted(Comparator.comparingDouble(
                            (RecipeResearchResponse.Recipe recipe) -> computeSoftScore(recipe, queryFilters)
                    ).reversed())
                    .collect(Collectors.toList());
        });
    }

    // ── Hard constraints ────────────────────────────────────────────────────

    /**
     * Returns {@code false} if the recipe violates any hard constraint from the query.
     * Recipes with missing filter metadata are passed through (fail-open) to avoid
     * incorrectly discarding valid results.
     */
    boolean passesHardConstraints(
            final RecipeResearchResponse.Recipe recipe,
            final RecipeFilters query
    ) {
        final RecipeFilters rf = recipe.getFilters();
        if (rf == null) return true;

        if (!excludedIngredientsCheck(query, rf)) return false;
        if (!dietaryRestrictionsCheck(query, rf)) return false;

        return true;
    }

    /**
     * Rejects recipes whose ingredient list contains any ingredient the user excluded.
     * Matching is case-insensitive and substring-based (e.g. "peanut" matches "peanut butter").
     */
    private boolean excludedIngredientsCheck(final RecipeFilters query, final RecipeFilters recipe) {
        List<String> excluded = query.getExcludeIngredients();
        if (excluded == null || excluded.isEmpty()) return true;

        List<String> recipeIngredients = recipe.getIncludeIngredients();
        if (recipeIngredients == null || recipeIngredients.isEmpty()) return true;

        List<String> normalizedRecipeIngredients = toLowerCaseList(recipeIngredients);
        for (String excludedItem : excluded) {
            String norm = excludedItem.toLowerCase();
            boolean found = normalizedRecipeIngredients.stream()
                    .anyMatch(ri -> ri.contains(norm));
            if (found) {
                log.debug("Hard constraint violation: excluded ingredient '{}' found in recipe", excludedItem);
                return false;
            }
        }
        return true;
    }

    /**
     * Rejects recipes that don't satisfy every dietary restriction the user requested
     * (e.g. user wants "vegan" but the recipe is not labelled "vegan").
     */
    private boolean dietaryRestrictionsCheck(final RecipeFilters query, final RecipeFilters recipe) {
        List<String> required = query.getDietaryRestrictions();
        if (required == null || required.isEmpty()) return true;

        List<String> recipeRestrictions = recipe.getDietaryRestrictions();
        if (recipeRestrictions == null || recipeRestrictions.isEmpty()) {
            log.debug("Hard constraint violation: recipe has no dietary restriction labels, required: {}", required);
            return false;
        }

        List<String> normalized = toLowerCaseList(recipeRestrictions);
        for (String restriction : required) {
            if (!normalized.contains(restriction.toLowerCase())) {
                log.debug("Hard constraint violation: dietary restriction '{}' not satisfied", restriction);
                return false;
            }
        }
        return true;
    }

    // ── Soft constraint scoring ─────────────────────────────────────────────

    /**
     * Returns a score in [0, 1] representing how well the recipe matches the user's soft
     * constraints. Only constraints that the user actually specified contribute to the score,
     * so a recipe is not penalised for fields the user left unspecified.
     */
    double computeSoftScore(
            final RecipeResearchResponse.Recipe recipe,
            final RecipeFilters query
    ) {
        RecipeFilters rf = recipe.getFilters();
        if (rf == null) return 0.0;

        double earned = 0.0;
        double possible = 0.0;

        // cuisine — exact string match
        if (query.getCuisine() != null) {
            possible += W_CUISINE;
            if (equalsIgnoreCase(query.getCuisine(), rf.getCuisine())) earned += W_CUISINE;
        }

        // mealType — exact string match
        if (query.getMealType() != null) {
            possible += W_MEAL_TYPE;
            if (equalsIgnoreCase(query.getMealType(), rf.getMealType())) earned += W_MEAL_TYPE;
        }

        // flavorProfile — overlap ratio: how many of the requested flavours does the recipe have?
        if (hasItems(query.getFlavorProfile())) {
            possible += W_FLAVOR_PROFILE;
            earned += W_FLAVOR_PROFILE * listOverlapRatio(query.getFlavorProfile(), rf.getFlavorProfile());
        }

        // course — exact string match
        if (query.getCourse() != null) {
            possible += W_COURSE;
            if (equalsIgnoreCase(query.getCourse(), rf.getCourse())) earned += W_COURSE;
        }

        // includeIngredients — how many of the user's requested ingredients does the recipe include?
        if (hasItems(query.getIncludeIngredients())) {
            possible += W_INCLUDE_INGREDIENTS;
            earned += W_INCLUDE_INGREDIENTS * listOverlapRatio(query.getIncludeIngredients(), rf.getIncludeIngredients());
        }

        // prepDifficulty — exact string match
        if (query.getPrepDifficulty() != null) {
            possible += W_PREP_DIFFICULTY;
            if (equalsIgnoreCase(query.getPrepDifficulty(), rf.getPrepDifficulty())) earned += W_PREP_DIFFICULTY;
        }

        // occasion — overlap ratio
        if (hasItems(query.getOccasion())) {
            possible += W_OCCASION;
            earned += W_OCCASION * listOverlapRatio(query.getOccasion(), rf.getOccasion());
        }

        // tags — overlap ratio
        if (hasItems(query.getTags())) {
            possible += W_TAGS;
            earned += W_TAGS * listOverlapRatio(query.getTags(), rf.getTags());
        }

        if (possible == 0.0) return 0.0;
        double score = earned / possible;
        log.debug("Soft score for '{}': {}", recipe.getRecipeName(), score);
        return score;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Returns the fraction of {@code queryList} items that appear in {@code recipeList},
     * e.g. query=["garlic","lemon"], recipe=["garlic","olive oil"] → 0.5.
     * Returns 0.0 if either list is null/empty.
     */
    private double listOverlapRatio(final List<String> queryList, final List<String> recipeList) {
        if (!hasItems(queryList) || !hasItems(recipeList)) return 0.0;
        List<String> normalizedRecipe = toLowerCaseList(recipeList);
        long matched = queryList.stream()
                .filter(item -> normalizedRecipe.contains(item.toLowerCase()))
                .count();
        return (double) matched / queryList.size();
    }

    private boolean equalsIgnoreCase(final String a, final String b) {
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    private boolean hasItems(final List<String> list) {
        return list != null && !list.isEmpty();
    }

    private List<String> toLowerCaseList(final List<String> list) {
        if (list == null) return List.of();
        return list.stream()
                .filter(s -> s != null)
                .map(String::toLowerCase)
                .collect(Collectors.toList());
    }
}
