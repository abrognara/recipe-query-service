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
    private final OpenAiResponsesApiEventParser eventParser;
    private final Object recipeQueryResultsJsonSchema;

    @Autowired
    public OpenAiResponsesService(
            WebClient openAiWebClient, OpenAiResponsesApiEventParser eventParser,
            Object recipeQueryResultsJsonSchema, Object recipeQueryParseJsonSchema) {
        this.openAiWebClient = openAiWebClient;
        this.eventParser = eventParser;
        this.recipeQueryResultsJsonSchema = recipeQueryResultsJsonSchema;
    }

    public Flux<String> getOpenAiResponseWebSearch(final String appRequestId, final String userPrompt) {
        final Map<String, Object> requestBody = OpenAiResponsesRequest.builder()
                .model(model)
                .inputList(
                        List.of(
                                OpenAiResponsesRequest.Input.builder()
                                        .inputRole(OpenAiResponsesRequest.InputRole.SYSTEM)
                                        .inputContent(new OpenAiResponsesRequest.InputContent(SYSTEM_TEXT_FOR_PROMPT))
                                        .build(),
                                OpenAiResponsesRequest.Input.builder()
                                        .inputRole(OpenAiResponsesRequest.InputRole.USER)
                                        .inputContent(new OpenAiResponsesRequest.InputContent(userPrompt))
                                        .build()
                        )
                )
                .text(
                        OpenAiResponsesRequest.Text.builder()
                        .format(recipeQueryResultsJsonSchema)
                        .build()
                )
                .tools(
                        List.of(OpenAiResponsesRequest.Tool.WEB_SEARCH)
                )
                .toolChoice(OpenAiResponsesRequest.ToolChoice.REQUIRED)
                .stream(true)
                .build()
                .getBody();
        return getOpenAiResponseWebSearch(appRequestId, requestBody);
    }

    // TODO remove this (10/29/25)
    public Flux<String> getOpenAiResponseWebSearchNextResults(
            final String appRequestId, final String prevOpenAiResponseId, final String userPrompt) {
//        final Map<String, Object> requestBody = new HashMap<>(createStandardRequestBody(
//                userPrompt, "recipes-overview-response-schema-web-search.json"));
//        requestBody.put("previous_response_id", prevOpenAiResponseId);
//        return getOpenAiResponseWebSearch(appRequestId, requestBody);
        return getOpenAiResponseWebSearch(appRequestId, userPrompt);
    }

    public WebClient.ResponseSpec callOpenAiResponses(final String appRequestId, final OpenAiResponsesRequest request) {
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
                )
                .bodyToMono(String.class);
    }

    // this method is currently being used by streaming calls to responses
    private Flux<String> getOpenAiResponseWebSearch(final String appRequestId, final Map<String, Object> requestBody) {
        return openAiWebClient.post()
                .uri("/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .flatMap(err -> {
                                    log.info("OpenAi request failed: {}", err);
                                    return Mono.error(new RuntimeException("OpenAi request failed: " + err));
                                })
                )
                .bodyToFlux(String.class)
                .flatMap(rawResponse -> eventParser.parseEvent(appRequestId, rawResponse));
    }
}
