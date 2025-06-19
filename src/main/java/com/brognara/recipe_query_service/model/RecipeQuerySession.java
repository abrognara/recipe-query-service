package com.brognara.recipe_query_service.model;

import lombok.*;

import java.util.LinkedList;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class RecipeQuerySession {
    private String sessionId;
    private String userId;
    private String prevOpenAiResponseId;
    // TODO include the full request instead of just the query
    private String userQuery;
    // TODO can the responses be compressed so they don't take up space
    private LinkedList<String> responsesList = new LinkedList<>();
}
