package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.RecipeQuerySessionRequest;
import org.springframework.stereotype.Service;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class PromptBuilderService {

    private static final String BASE_PROMPT = "You are a helpful assistant that can help this user find a recipe based on their prompt, which is: ";
    private static final String AVAILABLE_INGREDIENTS_PROMPT = "The user has the following available ingredients: ";
    private static final String DIFFICULTY_PROMPT = "The dish should be"; // simple, moderately sophisticated, or complex
    private static final String DIETARY_RESTRICTIONS_PROMPT = "The user has the following dietary restrictions: ";
    private static final String APPLIANCES_PROMPT = "The user has the following appliances to cook with: ";
    private static final String OUTPUT_FORMAT_PROMPT = "Format the output in json in the following schema: { “name”: <recipeName>, “ingredients”: [ { “ingredient_name”: <ingredient name>, “amount”: <amount>, “unit”: <unit> }, … ], “prep_steps”: [ <prep instruction>, … ], “cook_steps”: [ <cooking step>, …] }";
    private static final String PERIOD_NEXT_INSTRUCTION = ". ";

    public String buildPrompt(RecipeQuerySessionRequest request) {
        StringBuilder prompt = new StringBuilder(BASE_PROMPT);
        prompt.append(request.getQuery());
        prompt.append(PERIOD_NEXT_INSTRUCTION);

        if (request.isUseAvailableIngredients() && request.getAvailableIngredients() != null) {
            addAvailableIngredients(prompt, request);
        }

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

    private void addAvailableIngredients(StringBuilder prompt, RecipeQuerySessionRequest request) {
        prompt.append(AVAILABLE_INGREDIENTS_PROMPT);
        request.getAvailableIngredients().forEach(ingredient -> 
            prompt.append(ingredient).append(", "));
        prompt.setLength(prompt.length() - 2);
        prompt.append(PERIOD_NEXT_INSTRUCTION);
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