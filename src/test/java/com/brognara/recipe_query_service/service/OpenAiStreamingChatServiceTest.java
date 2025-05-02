package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.RecipeQueryRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import java.time.Duration;
import java.util.stream.IntStream;
import org.springframework.test.util.ReflectionTestUtils;
import lombok.extern.log4j.Log4j2;

import com.brognara.recipe_query_service.TestDataUtils;

@Log4j2
class OpenAiStreamingChatServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec uriSpec;

    @Mock
    private WebClient.RequestBodySpec bodySpec;

    @Mock
    private WebClient.RequestHeadersSpec headerSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private PromptBuilderService promptBuilderService;

    @Mock
    private OverviewPromptBuilderService overviewPromptBuilderService;

    @Mock
    private OpenAiStreamResponseParser openAiStreamResponseParser;

    private OpenAiStreamingChatService chatService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        chatService = new OpenAiStreamingChatService(webClient, new ObjectMapper(), promptBuilderService, overviewPromptBuilderService, openAiStreamResponseParser);
        ReflectionTestUtils.setField(chatService, "model", "gpt-4.1-mini-2025-04-14");
    }

    @Test
    void getChatResponse_shouldReturnRecipe() {
        final RecipeQueryRequest request = new RecipeQueryRequest();
        request.setQuery("test prompt");

        String[] mockResponseTokens = IntStream.range(0, 5)
            .mapToObj(i -> generateMockResponse("chatcmpl-" + i, "token " + i))
            .toArray(String[]::new);

        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.accept(any())).thenReturn(bodySpec);
        when(bodySpec.bodyValue(any())).thenReturn(headerSpec);
        when(headerSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(String.class))
            .thenReturn(
                Flux.just(mockResponseTokens)
                .delayElements(Duration.ofMillis(5))
            );

        when(promptBuilderService.buildPrompt(request)).thenReturn("test prompt");

        // When
        Flux<String> result = chatService.streamDetailsChatCompletion(request);

        // Then
        StepVerifier.create(result)
            .expectNext("token 0")
            .expectNext("token 1")
            .expectNext("token 2")
            .expectNext("token 3")
            .expectNext("token 4")
            .verifyComplete();
    }

    // TODO: move to integration test
    @Test
    void componentTest() {
        final RecipeQueryRequest request = new RecipeQueryRequest();
        request.setQuery("test prompt");

        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.accept(any())).thenReturn(bodySpec);
        when(bodySpec.bodyValue(any())).thenReturn(headerSpec);
        when(headerSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(String.class))
            .thenReturn(
                TestDataUtils.generateRecipeDetailsResponseStream()
                    .delayElements(Duration.ofMillis(5))
            );

        when(promptBuilderService.buildPrompt(request)).thenReturn("test prompt");

        // When
        Flux<String> result = chatService.streamDetailsChatCompletion(request);
    }

    private String generateMockResponse(final String id, final String content) {
        StringBuilder sb = new StringBuilder("{\"id\":\"chatcmpl-");
        sb.append(id);
        sb.append("\",\"object\":\"chat.completion.chunk\",\"created\":1745355852,\"model\":\"gpt-4.1-mini-2025-04-14\",\"service_tier\":\"default\",\"system_fingerprint\":\"fp_79b79be41f\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"");
        sb.append(content);
        sb.append("\"},\"logprobs\":null,\"finish_reason\":null}]}");
        return sb.toString();
    }
    
} 