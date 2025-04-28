package com.brognara.recipe_query_service.controller;

import com.brognara.recipe_query_service.model.Recipe;
import com.brognara.recipe_query_service.model.RecipeQueryRequest;
import com.brognara.recipe_query_service.service.ChatService;
import com.brognara.recipe_query_service.service.PantryService;
import com.brognara.recipe_query_service.service.RequestValidatorService;
import com.brognara.recipe_query_service.service.OpenAiStreamingChatService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.http.MediaType;
import com.brognara.recipe_query_service.service.StreamingChatResponseParser;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class QueryController {

    private final ChatService chatService;
    private final RequestValidatorService validatorService;
    private final PantryService pantryService;
    private final OpenAiStreamingChatService openAiStreamingChatService;
    private final StreamingChatResponseParser streamingChatResponseParser;

    public QueryController(ChatService chatService, RequestValidatorService validatorService, 
    PantryService pantryService, OpenAiStreamingChatService openAiStreamingChatService, 
    StreamingChatResponseParser streamingChatResponseParser) {
        this.chatService = chatService;
        this.validatorService = validatorService;
        this.pantryService = pantryService;
        this.openAiStreamingChatService = openAiStreamingChatService;
        this.streamingChatResponseParser = streamingChatResponseParser;
    }
    
    @PostMapping("/query")
    public Mono<Recipe> query(@RequestBody final RecipeQueryRequest request) {
        // TODO maybe do tiered validation
        // first check if basic request is valid, then check if pantry is valid and chat is valid in parallel
        return validatorService.validateRequest(request)
                .flatMap(validatedRequest -> pantryService.enhanceRequestWithPantryInfo(validatedRequest))
                .flatMap(enhancedRequest -> chatService.getChatResponse(enhancedRequest));
    }

    @PostMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> queryStream(@RequestBody final RecipeQueryRequest request) {
        final String requestId = UUID.randomUUID().toString();
        return openAiStreamingChatService.streamDetailsChatCompletion(request)
            .flatMap(token -> streamingChatResponseParser.parse(requestId, token));
    }

    @PostMapping(value = "/query/stream/test", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> queryStreamTest(@RequestBody final RecipeQueryRequest request) {
        return openAiStreamingChatService.streamOverviewChatCompletion(request);
    }
} 