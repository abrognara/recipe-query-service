package com.brognara.recipe_query_service.controller;

import com.brognara.recipe_query_service.model.Conversation;
import com.brognara.recipe_query_service.service.ConversationSessionService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Log4j2
@RestController
@RequestMapping("/api/v1/chats")
public class ConversationController {

    private final ConversationSessionService conversationSessionService;

    @Autowired
    public ConversationController(final ConversationSessionService conversationSessionService) {
        this.conversationSessionService = conversationSessionService;
    }

    @GetMapping("/{convoId}")
    public Mono<ResponseEntity<Conversation>> getConversationByIdForUser(
            @RequestHeader("X-User-Id") final String userId,
            @RequestHeader("X-User-Roles") final String userRoles,
            @PathVariable final String convoId
    ) {
        final String requestId = "1234";
        log.info("[{}] GET /chats/{} ; userId={} ; userRoles={}",
                requestId, convoId, userId, userRoles);
        return conversationSessionService.getConversation(userId, convoId)
                .map(ResponseEntity::ok);
    }

    // TODO make it so don't need to add "/" at the end of the api call
    @GetMapping("/")
    public Mono<ResponseEntity<List<Map<String, Conversation>>>> getAllConversationsForUser(
            @RequestHeader("X-User-Id") final String userId,
            @RequestHeader("X-User-Roles") final String userRoles
    ) {
        final String requestId = "1234";
        log.info("[{}] GET /chats/ ; userId={} ; userRoles={}",
                requestId, userId, userRoles);
        return conversationSessionService.getAllConversationsForUser(userId)
                .map(ResponseEntity::ok);
    }

}
