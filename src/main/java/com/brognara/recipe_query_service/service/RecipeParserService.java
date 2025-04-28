package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.Recipe;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RecipeParserService {

    public Recipe parseRecipe(String text) {
        Recipe recipe = new Recipe();
        
        // Parse basic information
        parseBasicInfo(recipe, text);
        
        // Parse ingredients
        parseIngredients(recipe, text);
        
        // Parse instructions
        parseInstructions(recipe, text);
        
        return recipe;
    }

    private void parseBasicInfo(Recipe recipe, String text) {
        // Extract name
        Pattern namePattern = Pattern.compile("Name: (.*?)\\n");
        Matcher nameMatcher = namePattern.matcher(text);
        if (nameMatcher.find()) {
            recipe.setName(nameMatcher.group(1).trim());
        }

        // Extract style
        Pattern stylePattern = Pattern.compile("Style: (.*?)\\n");
        Matcher styleMatcher = stylePattern.matcher(text);
        if (styleMatcher.find()) {
            recipe.setStyle(styleMatcher.group(1).trim());
        }

        // Extract time
        Pattern timePattern = Pattern.compile("Time: (.*?)\\n");
        Matcher timeMatcher = timePattern.matcher(text);
        if (timeMatcher.find()) {
            recipe.setTime(timeMatcher.group(1).trim());
        }

        // Extract servings
        Pattern servingsPattern = Pattern.compile("Servings: (.*?)\\n");
        Matcher servingsMatcher = servingsPattern.matcher(text);
        if (servingsMatcher.find()) {
            recipe.setServings(servingsMatcher.find() ? servingsMatcher.group(1).trim() : null);
        }

        // Extract new ingredients
        Pattern newIngredientsPattern = Pattern.compile("New ingredients: (.*?)\\n");
        Matcher newIngredientsMatcher = newIngredientsPattern.matcher(text);
        if (newIngredientsMatcher.find()) {
            String[] newIngredients = newIngredientsMatcher.group(1).split(",");
            List<String> cleanedIngredients = new ArrayList<>();
            for (String ingredient : newIngredients) {
                cleanedIngredients.add(ingredient.trim());
            }
            recipe.setNewIngredients(cleanedIngredients);
        }
    }

    private void parseIngredients(Recipe recipe, String text) {
        List<Recipe.Ingredient> ingredients = new ArrayList<>();
        
        // Find the ingredients section
        String[] sections = text.split("Ingredients:|Instructions:");
        if (sections.length > 1) {
            String ingredientsText = sections[1].trim();
            String[] ingredientLines = ingredientsText.split("\\n");
            
            for (String line : ingredientLines) {
                line = line.trim();
                if (!line.isEmpty()) {
                    // Split on the first occurrence of a space after the amount
                    String[] parts = line.split(" ", 2);
                    if (parts.length == 2) {
                        ingredients.add(new Recipe.Ingredient(parts[0].trim(), parts[1].trim()));
                    }
                }
            }
        }
        
        recipe.setIngredients(ingredients);
    }

    private void parseInstructions(Recipe recipe, String text) {
        List<String> instructions = new ArrayList<>();
        
        // Find the instructions section
        String[] sections = text.split("Instructions:");
        if (sections.length > 1) {
            String instructionsText = sections[1].trim();
            String[] instructionLines = instructionsText.split("\\n");
            
            for (String line : instructionLines) {
                line = line.trim();
                if (!line.isEmpty()) {
                    // Remove step numbers if present
                    line = line.replaceAll("^\\d+\\.\\s*", "");
                    instructions.add(line);
                }
            }
        }
        
        recipe.setInstructions(instructions);
    }
} 