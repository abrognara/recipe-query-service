package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.RecipeResearchResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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

    public Mono<Void> upsert(final RecipeResearchResponse.Recipe recipe, final List<Double> embedding) {
        final Map<String, Object> upsertRequestBody = Map.of(
                "id", "v-" + UUID.randomUUID().toString(),
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
                "url", recipe.getUrl(),
                "description", recipe.getDescription(),
                "details", Map.of(
                        "dishType", recipe.getDetails().getDishType(),
                        "appliances", recipe.getDetails().getAppliances(),
                        "ingredients", recipe.getDetails().getIngredients(),
                        "nutrition", recipe.getDetails().getNutrition(),
                        "cuisineTypes", recipe.getDetails().getCuisineTypes()
                )
        );
    }

    public Mono<List<RecipeResearchResponse.Recipe>> query(final List<Double> vector) {
        // TODO add metadata filter
        Map<String, Object> queryRequestBody = Map.of(
                "topK", 5,
                "vector", vector,
                "includeMetadata", true
        );

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

    private RecipeResearchResponse.Recipe parseMetadataToRecipe(final Map metadata) {
        Map details = (Map) metadata.get("details");
        return RecipeResearchResponse.Recipe
                .builder()
                .url((String) metadata.get("url"))
                .description((String) metadata.get("description"))
                .details(
                        RecipeResearchResponse.Details.builder()
                                .appliances((List<String>) details.get("appliances"))
                                .cuisineTypes((List<String>) details.get("cuisineTypes"))
                                .ingredients((List<String>) details.get("ingredients"))
                                .nutrition((List<String>) details.get("nutrition"))
                                .dishType((String) details.get("dishType"))
                                .build()
                )
                .build();
    }

}
