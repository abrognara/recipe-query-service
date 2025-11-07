package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.OpenAiResponsesRequest;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Log4j2
@Service
public class PreProcessingService {

    private static final String PREPROCESS_QUERY_SYSTEM_PROMPT = "Convert the provided recipe query into a concise, lowercase version. Correct any spelling errors, ensure all relevant details about the recipe are preserved, and accurately capture its semantic meaning. After transforming the query, quickly verify that your output maintains key recipe information and semantic accuracy before finalizing your response. Only return the final response.\n" +
            "\n" +
            "For example, the following query \"give me a mediterranean-inspired pasta dish with chicken, but is also dairy free and high in protein\" would be converted into the following: \"mediterranean high-protein dairy-free pasta with chicken\".";

    private final OpenAiResponsesService openAiResponsesService;
    private final Object recipeQueryParseJsonSchema;

    @Autowired
    public PreProcessingService(OpenAiResponsesService openAiResponsesService, Object recipeQueryParseJsonSchema) {
        this.openAiResponsesService = openAiResponsesService;
        this.recipeQueryParseJsonSchema = recipeQueryParseJsonSchema;
    }

    public Mono<String> preProcessQuery(final String appRequestId, final String rawUserQuery) {
        final OpenAiResponsesRequest queryParseRequest = OpenAiResponsesRequest.builder()
                .model("gpt-4.1")
                .inputList(
                        List.of(
                                OpenAiResponsesRequest.Input.builder()
                                        .inputRole(OpenAiResponsesRequest.InputRole.SYSTEM)
                                        .inputContent(new OpenAiResponsesRequest.InputContent(PREPROCESS_QUERY_SYSTEM_PROMPT))
                                        .build(),
                                OpenAiResponsesRequest.Input.builder()
                                        .inputRole(OpenAiResponsesRequest.InputRole.USER)
                                        .inputContent(new OpenAiResponsesRequest.InputContent(rawUserQuery))
                                        .build()
                        )
                )
                .stream(false)
                .build();

        return openAiResponsesService.callOpenAiResponsesReturnsString(appRequestId, queryParseRequest)
                .doOnNext(response -> log.info("[{}] Preprocess Query Response: {}", appRequestId, response));
    }

//    public Mono<String> preProcessRecipe() {
//
//    }

    // 'enhanced' preprocess returns metadata filters in a json object
    public Mono<String> preProcessQueryEnhanced(final String appRequestId, final String userPrompt) {
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
        final OpenAiResponsesRequest queryParseRequest = OpenAiResponsesRequest.builder()
                .model("gpt-4.1-mini")
                .inputList(
                        List.of(
                                OpenAiResponsesRequest.Input.builder()
                                        .inputRole(OpenAiResponsesRequest.InputRole.SYSTEM)
                                        .inputContent(new OpenAiResponsesRequest.InputContent(systemPrompt))
                                        .build(),
                                OpenAiResponsesRequest.Input.builder()
                                        .inputRole(OpenAiResponsesRequest.InputRole.USER)
                                        .inputContent(new OpenAiResponsesRequest.InputContent(userPrompt))
                                        .build()
                        )
                )
                .text(
                        OpenAiResponsesRequest.Text.builder()
                                .format(recipeQueryParseJsonSchema)
                                .build()
                )
                .stream(false)
                .build();
        return openAiResponsesService.callOpenAiResponsesReturnsString(appRequestId, queryParseRequest);
    }

    // 'enhanced' preprocess returns metadata filters in a json object
    public Mono<String> preProcessRecipeDataEnhanced(final String appRequestId, final String userPrompt) {
        final String systemPrompt = "Your only job is to interpret recipe data and transform the data into structured json based on the schema." +
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
        final OpenAiResponsesRequest queryParseRequest = OpenAiResponsesRequest.builder()
                .model("gpt-4.1-mini")
                .inputList(
                        List.of(
                                OpenAiResponsesRequest.Input.builder()
                                        .inputRole(OpenAiResponsesRequest.InputRole.SYSTEM)
                                        .inputContent(new OpenAiResponsesRequest.InputContent(systemPrompt))
                                        .build(),
                                OpenAiResponsesRequest.Input.builder()
                                        .inputRole(OpenAiResponsesRequest.InputRole.USER)
                                        .inputContent(new OpenAiResponsesRequest.InputContent(userPrompt))
                                        .build()
                        )
                )
                .text(
                        OpenAiResponsesRequest.Text.builder()
                                .format(recipeQueryParseJsonSchema)
                                .build()
                )
                .stream(false)
                .build();
        return openAiResponsesService.callOpenAiResponsesReturnsString(appRequestId, queryParseRequest)
                .doOnNext(response -> log.info("[{}] Preprocess Recipe Data Enhanced Response: {}", appRequestId, response));
    }

}
