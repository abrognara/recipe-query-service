package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.OpenAiApiRequest;
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
public class OpenAiResponsesApiService {

    private static final String SYSTEM_TEXT_FOR_PROMPT = "You are a helpful assistant that can find recipes based on the user's prompt. You must search for recipes using web search tool. Do not estimate any part of the recipe - only include data directly from the web page in the response. Return 5 recipes per request. Try to include unique and less-common sources where possible. All responses must be within the user's constraints.";

    @Value("${spring.ai.openai.model}")
    private String model;

    private final WebClient openAiWebClient;
    private final OpenAiResponsesApiEventParser eventParser;
    private final Object recipeQueryResultsJsonSchema;
    private final Object recipeQueryParseJsonSchema;

    @Autowired
    public OpenAiResponsesApiService(
            WebClient openAiWebClient, OpenAiResponsesApiEventParser eventParser,
            Object recipeQueryResultsJsonSchema, Object recipeQueryParseJsonSchema) {
        this.openAiWebClient = openAiWebClient;
        this.eventParser = eventParser;
        this.recipeQueryResultsJsonSchema = recipeQueryResultsJsonSchema;
        this.recipeQueryParseJsonSchema = recipeQueryParseJsonSchema;
    }

    public Mono<String> getOpenAiResponseQueryParse(final String appRequestId, final String userPrompt) {
        // TODO add the system prompt
        final String systemPrompt = "Your only job is to interpret user queries about recipes and transform them into structured json based on the schema." +
                "Regarding the 'errors' field - if you cannot determine a dish type from the query, populate the 'errors' field with the reason(s) why you can’t parse it." +
                "Otherwise, the 'errors' field should be empty." +
                "Don’t return any text besides the response json or error reason string.\n" +
                "For example: a query that reads “I’d like a dutch oven beef stew recipe that uses carrots and onions and is low sodium, and does not use celery”\n" +
                "Would be translated into\n" +
                "{\n" +
                "\t“dishType”: “beef stew”,\n" +
                "“appliances”: {\n" +
                "“includes”: [“dutch oven”]\n" +
                "},\n" +
                "“ingredients”: {\n" +
                "“includes”: [“carrot”, “onion”],\n" +
                "“excludes”: [“celery”]\n" +
                "},\n" +
                "“nutrition”: [“low sodium”]\n" +
                "}";
        final OpenAiApiRequest queryParseRequest = OpenAiApiRequest.builder()
                .model("gpt-4.1-mini")
                .inputList(
                        List.of(
                                OpenAiApiRequest.Input.builder()
                                        .inputRole(OpenAiApiRequest.InputRole.SYSTEM)
                                        .inputContent(new OpenAiApiRequest.InputContent(systemPrompt))
                                        .build(),
                                OpenAiApiRequest.Input.builder()
                                        .inputRole(OpenAiApiRequest.InputRole.USER)
                                        .inputContent(new OpenAiApiRequest.InputContent(userPrompt))
                                        .build()
                        )
                )
                .text(
                        OpenAiApiRequest.Text.builder()
                                .format(recipeQueryParseJsonSchema)
                                .build()
                )
                .stream(false)
                .build();
        return getOpenAiResponseQueryParse(appRequestId, queryParseRequest);
    }

    private Mono<String> getOpenAiResponseQueryParse(final String appRequestId, final OpenAiApiRequest openAiApiRequest) {
        return openAiWebClient.post()
                .uri("/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(openAiApiRequest.getBody())
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .flatMap(err -> {
                                    log.info("OpenAi request failed: {}", err);
                                    return Mono.error(new RuntimeException("OpenAi request failed: " + err));
                                })
                )
                .bodyToMono(String.class);
    }

    public Flux<String> getOpenAiResponseWebSearch(final String appRequestId, final String userPrompt) {
        final Map<String, Object> requestBody = OpenAiApiRequest.builder()
                .model(model)
                .inputList(
                        List.of(
                                OpenAiApiRequest.Input.builder()
                                        .inputRole(OpenAiApiRequest.InputRole.SYSTEM)
                                        .inputContent(new OpenAiApiRequest.InputContent(SYSTEM_TEXT_FOR_PROMPT))
                                        .build(),
                                OpenAiApiRequest.Input.builder()
                                        .inputRole(OpenAiApiRequest.InputRole.USER)
                                        .inputContent(new OpenAiApiRequest.InputContent(userPrompt))
                                        .build()
                        )
                )
                .text(
                        OpenAiApiRequest.Text.builder()
                        .format(recipeQueryResultsJsonSchema)
                        .build()
                )
                .tools(
                        List.of(OpenAiApiRequest.Tool.WEB_SEARCH)
                )
                .toolChoice(OpenAiApiRequest.ToolChoice.REQUIRED)
                .stream(true)
                .build()
                .getBody();
        return getOpenAiResponseWebSearch(appRequestId, requestBody);
    }

    public Flux<String> getOpenAiResponseWebSearchNextResults(
            final String appRequestId, final String prevOpenAiResponseId, final String userPrompt) {
//        final Map<String, Object> requestBody = new HashMap<>(createStandardRequestBody(
//                userPrompt, "recipes-overview-response-schema-web-search.json"));
//        requestBody.put("previous_response_id", prevOpenAiResponseId);
//        return getOpenAiResponseWebSearch(appRequestId, requestBody);
        return getOpenAiResponseWebSearch(appRequestId, userPrompt);
    }

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
