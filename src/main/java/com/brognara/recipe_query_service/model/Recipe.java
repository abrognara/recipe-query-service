package com.brognara.recipe_query_service.model;

import java.util.List;

public class Recipe {
    private String name;
    private String style;
    private String time;
    private String servings;
    private List<String> newIngredients;
    private List<Ingredient> ingredients;
    private List<String> instructions;

    public static class Ingredient {
        private String amount;
        private String name;

        public Ingredient(String amount, String name) {
            this.amount = amount;
            this.name = name;
        }

        public String getAmount() {
            return amount;
        }

        public String getName() {
            return name;
        }
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getServings() {
        return servings;
    }

    public void setServings(String servings) {
        this.servings = servings;
    }

    public List<String> getNewIngredients() {
        return newIngredients;
    }

    public void setNewIngredients(List<String> newIngredients) {
        this.newIngredients = newIngredients;
    }

    public List<Ingredient> getIngredients() {
        return ingredients;
    }

    public void setIngredients(List<Ingredient> ingredients) {
        this.ingredients = ingredients;
    }

    public List<String> getInstructions() {
        return instructions;
    }

    public void setInstructions(List<String> instructions) {
        this.instructions = instructions;
    }
} 