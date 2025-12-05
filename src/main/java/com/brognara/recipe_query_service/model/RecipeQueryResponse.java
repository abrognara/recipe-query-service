package com.brognara.recipe_query_service.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class RecipeQueryResponse {
    // For recipe queries we want to return convo id and latest response message
    private String convoId;
    private Conversation.Message responseMsg;
}
