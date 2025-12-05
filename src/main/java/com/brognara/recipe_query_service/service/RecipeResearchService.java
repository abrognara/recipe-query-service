package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.OpenAiResponsesRequest;
import com.brognara.recipe_query_service.model.RecipeFilters;
import com.brognara.recipe_query_service.model.RecipeResearchResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Log4j2
@Service
public class RecipeResearchService {

    // For the 'filters' field - try your best to populate all filters, but only if you're 100% sure about the accuracy of the data. Otherwise, it's ok to leave fields as null if you're unsure. These filters will be used to search for the recipe, so accuracy and relevancy to data in the webpage is of utmost importance.

    private static final String SYSTEM_PROMPT = "You're a recipe research specialist who finds recipes for users based on their query.  Add recipes to the response based on the schema object.  \n" +
            "\n" +
            "For the 'description' field - create a brief description about the recipe that preserves all relevant details about the recipe and accurately capture its semantic meaning. For example, the following query \"give me a mediterranean-inspired pasta dish with chicken, but is also dairy free and high in protein\" would be converted into the following: \"mediterranean high-protein dairy-free pasta with chicken\".\n" +
            "\n" +
            "For the 'filters' field - try your best to populate all filters, but only if you're 100% sure about the accuracy of the data. Otherwise, it's ok to leave fields as null if you're unsure. These filters will be used to search for the recipe, so accuracy and relevancy to data in the webpage is of utmost importance.\n" +
            "\n" +
            "For the final response, include 5 recipes per user query.";

    private final OpenAiResponsesService openAiResponsesService;
    private final Map<String, Object> recipeResearchJsonSchema;
    private final ObjectMapper objectMapper;

    @Autowired
    public RecipeResearchService(
            final OpenAiResponsesService openAiResponsesService,
            @Qualifier("recipeResearchJsonSchema") final Map<String, Object> recipeResearchJsonSchema,
            final ObjectMapper objectMapper) {
        this.openAiResponsesService = openAiResponsesService;
        this.recipeResearchJsonSchema = recipeResearchJsonSchema;
        this.objectMapper = objectMapper;
    }

    public Mono<RecipeResearchResponse> researchRecipes(final String appRequestId, final String userQuery) {
        log.info("[{}] Research recipes request", appRequestId);
        final OpenAiResponsesRequest request = OpenAiResponsesRequest.builder()
                .model("gpt-4.1")
                .inputList(
                        List.of(
                                OpenAiResponsesRequest.Input.builder()
                                        .inputRole(OpenAiResponsesRequest.InputRole.SYSTEM)
                                        .inputContent(new OpenAiResponsesRequest.InputContent(SYSTEM_PROMPT))
                                        .build(),
                                OpenAiResponsesRequest.Input.builder()
                                        .inputRole(OpenAiResponsesRequest.InputRole.USER)
                                        .inputContent(new OpenAiResponsesRequest.InputContent(userQuery))
                                        .build()
                        )
                )
                .tools(
                        List.of(OpenAiResponsesRequest.Tool.WEB_SEARCH)
                )
                .text(
                        OpenAiResponsesRequest.Text.builder()
                                .format(recipeResearchJsonSchema)
                                .build()
                )
                .toolChoice(OpenAiResponsesRequest.ToolChoice.REQUIRED)
                .stream(false)
                .build();
        return openAiResponsesService.callOpenAiResponses(appRequestId, request)
                .bodyToMono(Map.class)
                .map(this::parseResearchRecipesResponse)
                .doOnNext(recipeResearchResponse -> log.info("Recipe research response: {}", recipeResearchResponse));
    }

    private RecipeResearchResponse parseResearchRecipesResponse(final Map response) {
        final List output = (List) response.get("output");
        final Object messageObj = output.stream()
                .filter(e -> ((String) ((Map) e).get("type")).equalsIgnoreCase("message"))
                .findFirst()
                .get();
        final List contentList = (List) ((Map) messageObj).get("content");
        final String jsonText = (String) ((Map) contentList.get(0)).get("text");

        JsonNode root;
        try {
            root = objectMapper.readTree(jsonText);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        List<RecipeResearchResponse.Recipe> recipes = new LinkedList<>();
        for (JsonNode r : root.get("recipes")) {
            final String url = r.get("url").asText();
            final String description = r.get("description").asText();

            final JsonNode filters = r.get("filters");
            recipes.add(
                    RecipeResearchResponse.Recipe.builder()
                            .url(url)
                            .description(description)
                            .filters(
                                    RecipeFilters.builder()
                                            .cuisine(filters.get("cuisine").asText())
                                            .mealType(filters.get("mealType").asText())
                                            .course(filters.get("course").asText())
                                            .includeIngredients(
                                                    getListFromElm(filters.get("includeIngredients"))
                                            )
                                            .excludeIngredients(
                                                    getListFromElm(filters.get("excludeIngredients"))
                                            )
                                            .includeAppliances(
                                                    getListFromElm(filters.get("includeAppliances"))
                                            )
                                            .excludeAppliances(
                                                    getListFromElm(filters.get("excludeAppliances"))
                                            )
                                            .totalTimeMinutes(filters.get("totalTimeMinutes").asDouble())
                                            .servings(filters.get("servings").asInt())
                                            .prepDifficulty(filters.get("prepDifficulty").asText())
                                            .nutritionGoals(
                                                    RecipeFilters.NutritionGoals.builder()
                                                            .protein(filters.get("nutritionGoals").get("protein").asText())
                                                            .carbs(filters.get("nutritionGoals").get("carbs").asText())
                                                            .calories(filters.get("nutritionGoals").get("calories").asText())
                                                            .build()
                                            )
                                            .dietaryRestrictions(
                                                    getListFromElm(filters.get("dietaryRestrictions"))
                                            )
                                            .flavorProfile(
                                                    getListFromElm(filters.get("flavorProfile"))
                                            )
                                            .occasion(
                                                    getListFromElm(filters.get("occasion"))
                                            )
                                            .tags(
                                                    getListFromElm(filters.get("tags"))
                                            )
                                            .build()
                            )
                            .build()
            );
        }

        return RecipeResearchResponse.builder()
                .recipes(recipes)
                .build();
    }

    private List<String> getListFromElm(final JsonNode listNode) {
        List<String> list = new LinkedList<>();
        for (JsonNode n : listNode) {
            list.add(n.asText());
        }
        return list;
    }

}
