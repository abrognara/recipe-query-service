package com.brognara.recipe_query_service.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class OpenAiStreamResponseParser {

    private final ObjectMapper objectMapper;

    @Autowired
    public OpenAiStreamResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Flux<String> parse(final String body) {
//        log.info("Received line: {}", body);
        if (body.equals("[DONE]")) return Flux.empty();
        try {
            JsonNode root = objectMapper.readTree(body);
            String content = root
                .path("choices").get(0)
                .path("delta")
                .path("content").asText("");
            return Flux.just(content);
        } catch (Exception e) {
            log.error("Error parsing JSON: {}", e.getMessage());
            return Flux.empty();
        }
    }
}
