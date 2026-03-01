package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.RecipeFilters;
import com.brognara.recipe_query_service.model.RecipeResearchResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Log4j2
@Service
public class VectorDbServiceImpl implements VectorDbService {

    private final WebClient upstashVectorClient;

    @Autowired
    public VectorDbServiceImpl(@Qualifier("upstashVectorClient") final WebClient upstashVectorClient) {
        this.upstashVectorClient = upstashVectorClient;
    }

    public Mono<Void> upsert(
            final RecipeResearchResponse.Recipe recipe,
            final List<Double> embedding,
            final RecipeFilters recipeMetadataFilters
    ) {
        final Map<String, Object> upsertRequestBody = Map.of(
                "id", "v-" + UUID.randomUUID(),
                "vector", embedding,
                "metadata", buildRecipeMetadata(recipe)
        );

        return upstashVectorClient.post()
                .uri("/upsert")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(upsertRequestBody)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(Map.class)
                                .flatMap(err -> {
                                    log.info("Upstash vector db upsert request failed: {}", err);
                                    return Mono.error(new RuntimeException("Upstash vector db upsert request failed: " + err));
                                })
                )
                .bodyToMono(Map.class)
                .doOnNext(response -> {
                    log.info("Upsert response: {}", response);
                    if (((String) response.get("result")).equalsIgnoreCase("success")) {
                        log.info("Upsert successful");
                    }
                })
                .then();
    }

    private Map<String, Object> buildRecipeMetadata(final RecipeResearchResponse.Recipe recipe) {
        return Map.of(
                "recipeName", recipe.getRecipeName(),
                "imgUrl", recipe.getImgUrl(),
                "url", recipe.getUrl(),
                "description", recipe.getDescription(),
                "filters", recipe.getFilters().asMap()
        );
    }

    public Mono<List<RecipeResearchResponse.Recipe>> query(
            final List<Double> vector,
            final RecipeFilters userQueryMetadataFilters
    ) {
        Map<String, Object> queryRequestBody = new java.util.HashMap<>(Map.of(
                "topK", 8,
                "vector", vector,
                "includeMetadata", true
        ));

        final String metadataFiltersStr = buildMetadataFiltersString(userQueryMetadataFilters);
        if (StringUtils.hasText(metadataFiltersStr)) {
            queryRequestBody.put("filter", metadataFiltersStr);
        }

        log.info("vector db query request body: {}", queryRequestBody);

        return upstashVectorClient.post()
                .uri("/query")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(queryRequestBody)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(Map.class)
                                .flatMap(err -> {
                                    log.info("Upstash vector db query request failed: {}", err);
                                    return Mono.error(new RuntimeException("Upstash vector db query request failed: " + err));
                                })
                )
                .bodyToMono(Map.class)
                .map(this::parseQueryResponse);
    }

    private List<RecipeResearchResponse.Recipe> parseQueryResponse(final Map queryResponse) {
        log.info("Query response: {}", queryResponse);
        List results = (List) queryResponse.get("result");
        return (List<RecipeResearchResponse.Recipe>) results.stream()
                .map(e -> {
                    // TODO need response object to include all fields
                    final Map elm = (Map) e;
                    final String id = (String) elm.get("id");
                    final double score = (double) elm.get("score");
                    final RecipeResearchResponse.Recipe recipe = parseMetadataToRecipe((Map) elm.get("metadata"));
                    return recipe;
                })
                .collect(Collectors.toList());

    }

    private RecipeResearchResponse.Recipe parseMetadataToRecipe(Map metadata) {
        if (metadata == null) return null;

        return RecipeResearchResponse.Recipe.builder()
                .url((String) metadata.get("url"))
                .description((String) metadata.get("description"))
                .imgUrl((String) metadata.get("imgUrl"))
                .recipeName((String) metadata.get("recipeName"))
                .filters(RecipeFilters.fromMap((Map<String, Object>) metadata.get("filters")))
                .build();
    }

    // TODO impl the build filters string for query
    private String buildMetadataFiltersString(final RecipeFilters recipeFilters) {
        final List<String> dbQueryFilters = new LinkedList<>();

        // include appliances
        addFilterForArrayContains(
                recipeFilters.getIncludeAppliances(),
                "filters.includeAppliances",
                dbQueryFilters
        );

        // exclude appliances
        addFilterForArrayContains(
                recipeFilters.getExcludeAppliances(),
                "filters.excludeAppliances",
                dbQueryFilters
        );

        // TODO how to interpret total time? i.e. less than X minutes or more than X minutes?
        // total time minutes
//        final double ttm = recipeFilters.getTotalTimeMinutes();
//        if (ttm > 0.0) {
//            dbQueryFilters.add(
//                    String.format("filters.totalTimeMinutes = %s", ttm)
//            );
//        }

        // TODO how to interpret servings? i.e. less than X servings or more than X servings?
        // servings
//        final int servings = recipeFilters.getServings();
//        if (servings > 0) {
//            dbQueryFilters.add(
//                    String.format("filters.servings = %s", servings)
//            );
//        }

        // dietary restrictions
        final List<String> dtryRstrs = recipeFilters.getDietaryRestrictions();
        if (dtryRstrs != null && !dtryRstrs.isEmpty()) {
            dbQueryFilters.add(
                    dtryRstrs.stream()
                            .map(elm -> String.format("filters.dietaryRestrictions CONTAINS '%s'", elm))
                            .collect(Collectors.joining(" OR "))
            );
        }

        return String.join(" AND ", dbQueryFilters);
    }

    private void addFilterForArrayContains(
            final List<String> arrFilter, final String metadataKey, final List<String> dbQueryFilters
    ) {
        if (arrFilter != null && !arrFilter.isEmpty()) {
            dbQueryFilters.add(
                    arrFilter.stream()
                            .map(elm -> String.format("%s CONTAINS '%s'", metadataKey, elm))
                            .collect(Collectors.joining(" OR "))
            );
        }
    }

}
