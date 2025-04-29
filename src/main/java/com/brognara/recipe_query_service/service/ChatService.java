package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.Recipe;
import com.brognara.recipe_query_service.model.RecipeQueryRequest;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ChatService {

    private final OpenAiChatModel openAiChatModel;
    private final PromptBuilderService promptBuilderService;

    public ChatService(OpenAiChatModel openAiChatModel, PromptBuilderService promptBuilderService) {
        this.openAiChatModel = openAiChatModel;
        this.promptBuilderService = promptBuilderService;
    }

    // TODO: implement retry logic and error handling
    /* 
    this is the error for exceeded rate limit:
    org.springframework.ai.retry.NonTransientAiException: 429 - {
        "error": {
            "message": "You exceeded your current quota, please check your plan and billing details. For more information on this error, read the docs: https://platform.openai.com/docs/guides/error-codes/api-errors.",
            "type": "insufficient_quota",
            "param": null,
            "code": "insufficient_quota"
        }
    }
    */
    // public Mono<Recipe> getChatResponse(RecipeQueryRequest request) {
    //     String prompt = promptBuilderService.buildPrompt(request);
    //     return Mono.fromCallable(() -> openAiChatModel.call(prompt))
    //             .map(response -> recipeParserService.parseRecipe(response));
    // }
} 