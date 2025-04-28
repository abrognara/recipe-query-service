package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.RecipeQueryRequest;
import org.springframework.stereotype.Service;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class OverviewPromptBuilderService {

    private static final String BASE_PROMPT = "You are a helpful assistant that can help this user find recipes based on their prompt, which is: ";
    private static final String DIFFICULTY_PROMPT = "The dish should be"; // simple, moderately sophisticated, or complex
    private static final String DIETARY_RESTRICTIONS_PROMPT = "The user has the following dietary restrictions: ";
    private static final String APPLIANCES_PROMPT = "The user has the following appliances to cook with: ";
    private static final String OUTPUT_FORMAT_PROMPT = "Return a list of 10 relevant recipes with only the following info in one json object per recipe in the following json schema: { “name”: <recipe name>, “cook_time”: <cook time>, “user_missing_ingredients”: [ <ingredient name>, … ] }. Order the list by the number of user missing ingredients, with recipes containing the least number of missing ingredients first.";
    private static final String PERIOD_NEXT_INSTRUCTION = ". ";

    public String buildPrompt(RecipeQueryRequest request) {
        StringBuilder prompt = new StringBuilder(BASE_PROMPT);
        prompt.append(request.getQuery());
        prompt.append(PERIOD_NEXT_INSTRUCTION);

        // Add difficulty level
        if (request.getDifficultyLevel() != null) {
            prompt.append(DIFFICULTY_PROMPT).append(request.getDifficultyLevel().getDescription());
            prompt.append(PERIOD_NEXT_INSTRUCTION);
        }

        // Add servings information
        // prompt.append("If no servings are previously specified, please assume the user wants ").append(request.getServings()).append(" servings. ");
        
        // Add dietary restrictions if specified
        if (request.isUseUserPreferences() && request.getUserPreferences() != null) {
            addDietaryRestrictions(prompt, request);
            // addSpicePreference(prompt, request);
            addAvailableAppliances(prompt, request);
        }
        prompt.append(OUTPUT_FORMAT_PROMPT);

        final String finalPrompt = prompt.toString();
        log.info("Prompt: {}", finalPrompt);
        return finalPrompt;
    }

    private void addDietaryRestrictions(StringBuilder prompt, RecipeQueryRequest request) {
        if (!request.getUserPreferences().getDietaryRestrictions().isEmpty()) {
            prompt.append(DIETARY_RESTRICTIONS_PROMPT);
            request.getUserPreferences().getDietaryRestrictions().forEach(restriction -> 
                prompt.append(restriction.name().toLowerCase().replace("_", " ")).append(", "));
            prompt.setLength(prompt.length() - 2); // Remove trailing comma and space
            prompt.append(PERIOD_NEXT_INSTRUCTION);
        }
    }

    private void addSpicePreference(StringBuilder prompt, RecipeQueryRequest request) {
        if (request.getUserPreferences().getSpicePreference() != null) {
            prompt.append("\nSpice level: ").append(request.getUserPreferences().getSpicePreference().name().toLowerCase());
            prompt.append(PERIOD_NEXT_INSTRUCTION);
        }
    }

    private void addAvailableAppliances(StringBuilder prompt, RecipeQueryRequest request) {
        if (!request.getUserPreferences().getAppliancesOwned().isEmpty()) {
            prompt.append(APPLIANCES_PROMPT);
            request.getUserPreferences().getAppliancesOwned().forEach(appliance -> 
                prompt.append(appliance.name().toLowerCase().replace("_", " ")).append(", "));
            prompt.setLength(prompt.length() - 2);
            prompt.append(PERIOD_NEXT_INSTRUCTION);
        }
    }
} 