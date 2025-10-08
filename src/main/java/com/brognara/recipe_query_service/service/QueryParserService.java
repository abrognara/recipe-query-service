package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.OpenAiApiRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class QueryParserService {

    private static final String PREPROCESS_SYSTEM_PROMPT = "Convert the provided recipe query into a concise, lowercase version. Correct any spelling errors, ensure all relevant details about the recipe are preserved, and accurately capture its semantic meaning. After transforming the query, quickly verify that your output maintains key recipe information and semantic accuracy before finalizing your response. Only return the final response.\n" +
            "\n" +
            "For example, the following query \"give me a mediterranean-inspired pasta dish with chicken, but is also dairy free and high in protein\" would be converted into the following: \"mediterranean high-protein dairy-free pasta with chicken\".";

    private final OpenAiResponsesApiService openAiResponsesApiService;

    @Autowired
    public QueryParserService(OpenAiResponsesApiService openAiResponsesApiService) {
        this.openAiResponsesApiService = openAiResponsesApiService;
    }

    public Mono<String> preProcessQuery(final String rawUserQuery) {
        final OpenAiApiRequest queryParseRequest = OpenAiApiRequest.builder()
                .model("gpt-4.1-mini")
                .inputList(
                        List.of(
                                OpenAiApiRequest.Input.builder()
                                        .inputRole(OpenAiApiRequest.InputRole.SYSTEM)
                                        .inputContent(new OpenAiApiRequest.InputContent(PREPROCESS_SYSTEM_PROMPT))
                                        .build(),
                                OpenAiApiRequest.Input.builder()
                                        .inputRole(OpenAiApiRequest.InputRole.USER)
                                        .inputContent(new OpenAiApiRequest.InputContent(rawUserQuery))
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
    }

    public Mono<String> preProcessRecipe() {

    }

}
