package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.Conversation;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.time.Instant;
import java.util.*;

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

        final Conversation convo = new Conversation(
                List.of(
                        new Conversation.Message("prompt", userPrompt, Instant.now().toEpochMilli())
                ),
                Instant.now().toEpochMilli(),
                null
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

    public Mono<List<Map<String, Conversation>>> getAllConversationsForUser(final String userId) {
        return Mono.fromCallable(() -> {
            log.info("REDIS Get All Conversations ; userId={}", userId);
            final List<Map<String, Conversation>> convoIdToConversations = new LinkedList<>();

            String cursor = ScanParams.SCAN_POINTER_START;
            final ScanParams params = new ScanParams();
            params.match(
                    key(userId, "*") // wildcard matches all convos
            );
            params.count(100); // batch size for the scan

            do {
                ScanResult<String> scanResult = jedis.scan(cursor, params);

                for (final String convoKey : scanResult.getResult()) {
                    final String convoJson = jedis.get(convoKey);
                    if (convoJson != null) {
                        final Conversation convo = objectMapper.readValue(convoJson, Conversation.class);
                        final String convoId = convoKey.substring(convoKey.lastIndexOf(":") + 1);
                        convoIdToConversations.add(
                                Map.of(convoId, convo)
                        );
                    }
                }
                cursor = scanResult.getCursor();
            } while (!cursor.equals(ScanParams.SCAN_POINTER_START));

            // Sort by createdAt field (latest first)
            convoIdToConversations.sort(
                    Comparator.comparingLong(m -> m.values().iterator().next().getCreatedAt())
            );

            return convoIdToConversations;
        });
    }

    public Mono<String> addFirstResponseMessage(final String userId, final String convoId,
                                                final String openAiRequestId, final Conversation.Message message) {
        return getConversation(userId, convoId)
                .flatMap(convo -> {
                    // TODO handle getConversation() returns null
                    convo.setOpenAiRequestId(openAiRequestId);
                    convo.getConversation().add(message);
                    log.info("REDIS Add First Response Message ; userId={} ; convoId={} ; convo={}",
                            userId, convoId, convo);
                    return writeConversation(userId, convoId, convo);
                });
    }

    public Mono<String> addMessage(final String userId, final String convoId, final Conversation.Message message) {
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
