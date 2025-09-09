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

    private List<Message> conversation = new ArrayList<>();
    private long createdAt;

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
