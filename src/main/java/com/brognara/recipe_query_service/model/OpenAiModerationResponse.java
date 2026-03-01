package com.brognara.recipe_query_service.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAiModerationResponse {

    private List<Result> results;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {

        private boolean flagged;

        /** Maps category name → whether it was triggered (e.g. "hate", "violence/graphic"). */
        private Map<String, Boolean> categories;

        /** Maps category name → confidence score [0, 1]. */
        @JsonProperty("category_scores")
        private Map<String, Double> categoryScores;
    }
}
