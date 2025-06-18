package com.brognara.recipe_query_service.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ResponseContext {
    private String appResponseId;
    private String openAiResponseId;
    private List<String> tokens;
}
