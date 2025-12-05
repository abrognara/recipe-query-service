package com.brognara.recipe_query_service.model;

import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class Conversation {

    // TODO add a conversationSummary which captures a summary of the convo for UI friendliness

    private List<Message> conversation = new ArrayList<>();
    private long createdAt;
    private String openAiRequestId;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    public static class Message {
        private String type; // "prompt" or "response"
        private Object content;
        private long createdAt;
    }

}
