package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.OpenAiModerationResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@Service
public class OpenAiModerationService {

    private static final String MODERATION_MODEL = "omni-moderation-latest";

    private final WebClient openAiWebClient;

    @Autowired
    public OpenAiModerationService(final WebClient openAiWebClient) {
        this.openAiWebClient = openAiWebClient;
    }

    /**
     * Calls the OpenAI Moderation API for the given input.
     * Returns {@link Mono#empty()} if the content is safe, or signals a
     * {@link ResponseStatusException} (HTTP 422) if any category is flagged.
     */
    public Mono<Void> check(final String input, final String requestId) {
        return openAiWebClient.post()
                .uri("/moderations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("model", MODERATION_MODEL, "input", input))
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .flatMap(err -> {
                                    log.error("[{}] Moderation API error: {}", requestId, err);
                                    return Mono.error(new RuntimeException("Moderation API failed: " + err));
                                })
                )
                .bodyToMono(OpenAiModerationResponse.class)
                .flatMap(response -> {
                    OpenAiModerationResponse.Result result = response.getResults().get(0);

                    if (result.isFlagged()) {
                        String triggered = result.getCategories().entrySet().stream()
                                .filter(Map.Entry::getValue)
                                .map(Map.Entry::getKey)
                                .collect(Collectors.joining(", "));
                        log.warn("[{}] Input flagged by moderation API. Categories: {}", requestId, triggered);
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.UNPROCESSABLE_ENTITY,
                                "Query contains content that violates usage policies"
                        ));
                    }

                    log.debug("[{}] Moderation check passed", requestId);
                    return Mono.empty();
                });
    }
}
