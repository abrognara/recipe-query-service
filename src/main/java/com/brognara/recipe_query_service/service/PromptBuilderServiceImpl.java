package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.RecipeQuerySessionRequest;
import com.brognara.recipe_query_service.model.SimpleRecipeQuerySessionRequest;
import org.springframework.stereotype.Service;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class PromptBuilderServiceImpl implements PromptBuilderService {

    private static final String DIFFICULTY_PROMPT = "The dish should be"; // simple, moderately sophisticated, or complex
    private static final String DIETARY_RESTRICTIONS_PROMPT = "I have the following dietary restrictions: ";
    private static final String APPLIANCES_PROMPT = "I have the following appliances to cook with: ";
    private static final String INGREDIENTS_PROMPT = "I have the following ingredients: ";
    private static final String RECIPE_UNIQUENESS_INSTRUCTION = "When searching for recipes on the web, try to include unique and less-common sources where possible.";
    private static final String PERIOD_NEXT_INSTRUCTION = ". ";

    public String buildPrompt(final SimpleRecipeQuerySessionRequest request) {
        // add user query i.e. "I would like a chicken dish"
        StringBuilder prompt = new StringBuilder(request.getQuery());
        prompt.append(PERIOD_NEXT_INSTRUCTION);

        prompt.append(DIETARY_RESTRICTIONS_PROMPT);
        prompt.append("vegan");
        prompt.append(PERIOD_NEXT_INSTRUCTION);

        prompt.append(APPLIANCES_PROMPT);
        prompt.append("stove, oven, microwave, blender, air fryer");
        prompt.append(PERIOD_NEXT_INSTRUCTION);

        prompt.append(INGREDIENTS_PROMPT);
        prompt.append("salt, pepper, olive oil, garlic, onion, pasta, rice, tomatoes, cheese, eggs, milk. butter, flour, sugar");
        prompt.append(PERIOD_NEXT_INSTRUCTION);

        prompt.append(RECIPE_UNIQUENESS_INSTRUCTION + ".");

        final String finalPrompt = prompt.toString();
        log.info("Prompt: {}", finalPrompt);
        return finalPrompt;
    }

    private void addDietaryRestrictions(StringBuilder prompt, RecipeQuerySessionRequest request) {
        if (!request.getUserPreferences().getDietaryRestrictions().isEmpty()) {
            prompt.append(DIETARY_RESTRICTIONS_PROMPT);
            request.getUserPreferences().getDietaryRestrictions().forEach(restriction ->
                    prompt.append(restriction.name().toLowerCase().replace("_", " ")).append(", "));
            prompt.setLength(prompt.length() - 2); // Remove trailing comma and space
            prompt.append(PERIOD_NEXT_INSTRUCTION);
        }
    }

    private void addSpicePreference(StringBuilder prompt, RecipeQuerySessionRequest request) {
        if (request.getUserPreferences().getSpicePreference() != null) {
            prompt.append("\nSpice level: ").append(request.getUserPreferences().getSpicePreference().name().toLowerCase());
            prompt.append(PERIOD_NEXT_INSTRUCTION);
        }
    }

    private void addAvailableAppliances(StringBuilder prompt, RecipeQuerySessionRequest request) {
        if (!request.getUserPreferences().getAppliancesOwned().isEmpty()) {
            prompt.append(APPLIANCES_PROMPT);
            request.getUserPreferences().getAppliancesOwned().forEach(appliance ->
                    prompt.append(appliance.name().toLowerCase().replace("_", " ")).append(", "));
            prompt.setLength(prompt.length() - 2);
            prompt.append(PERIOD_NEXT_INSTRUCTION);
        }
    }
} 