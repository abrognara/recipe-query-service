package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.Conversation;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.json.Path;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Log4j2
@Service
public class ConversationSessionService {

    private final JedisPooled jedis;
    private final ObjectMapper objectMapper;

    @Autowired
    public ConversationSessionService(final JedisPooled jedis, final ObjectMapper objectMapper) {
        this.jedis = jedis;
        this.objectMapper = objectMapper;
    }

    private String key(String userId, String convoId) {
        return "conversation:" + userId + ":" + convoId;
    }

    // start conversation with a user prompt (message type = prompt)
    public Mono<String> createConversation(final String userId, final String userPrompt) {
        // create convo id as a random UUID
        final String convoId = UUID.randomUUID().toString();

        Conversation convo = new Conversation(
                List.of(
                        new Conversation.Message("prompt", userPrompt, Instant.now().toEpochMilli())
                ),
                Instant.now().toEpochMilli()
        );

        log.info("REDIS Create Conversation ; userId={} ; convoId={} ; convo={}",
                userId, convoId, convo);
        return writeConversation(userId, convoId, convo);
    }

    // add next user prompt to conversation (message type = prompt)
    public Mono<String> addNextPromptToConversation(final String userId, final String convoId, final String userPrompt) {
        log.info("REDIS Add Next Prompt To Conversation ; userId={} ; convoId={} ; userPrompt={}",
                userId, convoId, userPrompt);
        return addMessage(userId, convoId,
                new Conversation.Message("prompt", userPrompt, Instant.now().toEpochMilli()));
    }

    // upsert
    public Mono<String> writeConversation(final String userId, final String convoId, final Conversation convo) {
        return Mono.fromCallable(() -> {
            try {
                String k = key(userId, convoId);
                String json = objectMapper.writeValueAsString(convo);
                jedis.set(k, json);
                return convoId;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public Mono<Conversation> getConversation(String userId, String convoId) {
        return Mono.fromCallable(() -> {
            log.info("REDIS Get Conversation ; userId={} ; convoId={}",
                    userId, convoId);
            String k = key(userId, convoId);
            String json = jedis.get(k);
            return json != null ? objectMapper.readValue(json, Conversation.class) : null;
        });
    }

    public Mono<String> addMessage(String userId, String convoId, Conversation.Message message) {
        return getConversation(userId, convoId)
                .flatMap(convo -> {
                    // TODO handle getConversation() returns null
                    convo.getConversation().add(message);
                    log.info("REDIS Add Message ; userId={} ; convoId={} ; convo={}",
                            userId, convoId, convo);
                    return writeConversation(userId, convoId, convo);
                });
    }
}
