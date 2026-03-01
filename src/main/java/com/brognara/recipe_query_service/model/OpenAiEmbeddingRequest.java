 package com.brognara.recipe_query_service.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Builder
@Getter
@Setter
public class OpenAiEmbeddingRequest {
    private String model;
    private String input;

    public Map<String, Object> getBody() {
        return Map.of(
                "model", model,
                "input", input
        );
    }
}
