package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.OpenAiResponsesRequest;
import com.brognara.recipe_query_service.model.RecipeResearchResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.java.Log;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Log4j2
@Service
public class RecipeResearchService {

    private static final String SYSTEM_PROMPT = "You're a recipe research specialist who finds recipes for users based on their query.  Add recipes to the response based on the schema object.  \n" +
            "\n" +
            "For the 'description' field - create a brief description about the recipe that preserves all relevant details about the recipe and accurately capture its semantic meaning. For example, the following query \"give me a mediterranean-inspired pasta dish with chicken, but is also dairy free and high in protein\" would be converted into the following: \"mediterranean high-protein dairy-free pasta with chicken\".\n" +
            "\n" +
            "For the 'details' field - only add recipe details that you're 100% sure about. Don't estimate anything about the recipe - only add information that's actually contained in the webpage.\n" +
            "\n" +
            "For the final response, include 5 recipes per user query.";

    private final OpenAiResponsesService openAiResponsesService;
    private final Object recipeResearchJsonSchema;
    private final ObjectMapper objectMapper;

    @Autowired
    public RecipeResearchService(final OpenAiResponsesService openAiResponsesService,
                                 @Qualifier("recipeResearchJsonSchema") final Object recipeResearchJsonSchema, ObjectMapper objectMapper) {
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

            recipes.add(
                    RecipeResearchResponse.Recipe.builder()
                            .url(url)
                            .description(description)
                            .details(
                                    RecipeResearchResponse.Details.builder()
                                            .dishType(r.get("details").get("dishType").asText())
                                            .nutrition(
                                                    detailsChildElmList(r.get("details").get("nutrition"))
                                            )
                                            .ingredients(
                                                    detailsChildElmList(r.get("details").get("ingredients"))
                                            )
                                            .cuisineTypes(
                                                    detailsChildElmList(r.get("details").get("cuisineTypes"))
                                            )
                                            .appliances(
                                                    detailsChildElmList(r.get("details").get("appliances"))
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

    private List<String> detailsChildElmList(final JsonNode listNode) {
        List<String> list = new LinkedList<>();
        for (JsonNode n : listNode) {
            list.add(n.asText());
        }
        return list;
    }

}
