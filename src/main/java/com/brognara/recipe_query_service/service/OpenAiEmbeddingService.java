package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.OpenAiEmbeddingRequest;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Log4j2
@Service
public class OpenAiEmbeddingService {

    @Value("${spring.ai.openai.model.embeddings}")
    private String model;

    private final WebClient openAiWebClient;

    @Autowired
    public OpenAiEmbeddingService(WebClient openAiWebClient) {
        this.openAiWebClient = openAiWebClient;
    }

    public Mono<List<Double>> createVectorEmbedding(final String appRequestId, final String text) {
        final OpenAiEmbeddingRequest embeddingRequest = OpenAiEmbeddingRequest.builder()
                .model(model)
                .input(text)
                .build();
        return callOpenAiEmbeddings(appRequestId, embeddingRequest)
                .map(this::parseEmbeddingsResponseGetVector);
    }

    private Mono<Map> callOpenAiEmbeddings(final String appRequestId, final OpenAiEmbeddingRequest embeddingRequest) {
        return openAiWebClient.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(embeddingRequest.getBody())
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .flatMap(err -> {
                                    log.error("OpenAi embeddings request failed: {}", err);
                                    return Mono.error(new RuntimeException("OpenAi embeddings request failed: " + err));
                                })
                )
                .bodyToMono(Map.class);
    }

    private List<Double> parseEmbeddingsResponseGetVector(final Map<String, Object> response) {
//        log.info("Response object: {}", response);
        final List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
        final List<Double> embedding = (List<Double>) data.get(0).get("embedding");
        return embedding;
    }

}
