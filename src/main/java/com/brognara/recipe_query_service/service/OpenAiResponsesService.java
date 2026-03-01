package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.OpenAiResponsesRequest;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Log4j2
@Service
public class OpenAiResponsesService {

    private static final String SYSTEM_TEXT_FOR_PROMPT = "You are a helpful assistant that can find recipes based on the user's prompt. You must search for recipes using web search tool. Do not estimate any of the ingredients, ingredient amounts, nutritional data, etc. - only include data directly from the web page in the response. Try to include unique and less-common sources where possible. All responses must be within the user's dietary constraints if they specify any.";

    @Value("${spring.ai.openai.model}")
    private String model;

    private final WebClient openAiWebClient;
    private final Map<String, Object> recipeQueryResultsJsonSchema;

    @Autowired
    public OpenAiResponsesService(
            WebClient openAiWebClient, Map<String, Object> recipeQueryResultsJsonSchema,
            Map<String, Object> recipeQueryParseJsonSchema) {
        this.openAiWebClient = openAiWebClient;
        this.recipeQueryResultsJsonSchema = recipeQueryResultsJsonSchema;
    }

    public WebClient.ResponseSpec callOpenAiResponses(final String appRequestId, final OpenAiResponsesRequest request) {
//        log.info("[DEBUG] request {}", request.getBody());
        return openAiWebClient.post()
                .uri("/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request.getBody())
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .flatMap(err -> {
                                    log.info("OpenAi responses api request failed: {}", err);
                                    return Mono.error(new RuntimeException("OpenAi responses api request failed: " + err));
                                })
                );
    }

    // this method is currently being used by non-streaming calls to responses
    // TODO use the generic version or convert to Map
    public Mono<String> callOpenAiResponsesReturnsString(final String appRequestId, final OpenAiResponsesRequest request) {
//        log.info("[DEBUG] request {}", request.getBody());
        return openAiWebClient.post()
                .uri("/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request.getBody())
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .flatMap(err -> {
                                    log.error("OpenAi responses api request failed: {}", err);
                                    return Mono.error(new RuntimeException("OpenAi responses api request failed: " + err));
                                })
                )
                .bodyToMono(String.class);
    }
}
