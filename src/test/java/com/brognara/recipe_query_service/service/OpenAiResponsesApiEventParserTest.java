package com.brognara.recipe_query_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OpenAiResponsesApiEventParserTest {

    @Mock
    private ResponseContextService responseContextService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private RecipesOverviewResponseConverter responseConverter;

    private OpenAiResponsesApiEventParser eventParser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        eventParser = new OpenAiResponsesApiEventParser(
                responseContextService,
                objectMapper,
                responseConverter
        );
    }

    @Test
    void parseEvent() {
        final String testRequestId = "request-id";
        doAnswer(invocationOnMock -> {
            String appRequestId = invocationOnMock.getArgument(0);
            String openAiRequestId = invocationOnMock.getArgument(1);
            assertEquals(testRequestId, appRequestId);
            assertEquals("resp_681795369d3881918bffb873c2978e0b05b31444ed8e4035", openAiRequestId);
            return null;
        }).when(responseContextService).initContext(anyString(), anyString());

        Flux<String> eventFlux = eventParser.parseEvent(testRequestId, getTestResponseCreatedEvent());

        StepVerifier.create(eventFlux)
                .verifyComplete();
    }

    @Test
    void parseEvent_responseOutputTextDelta() {
        final String testRequestId = "request-id";

        when(responseConverter.parse(anyString(), anyString()))
                .thenReturn(Flux.just("converted data"));

        Flux<String> eventFlux = eventParser.parseEvent(testRequestId, getTestOutputTextDeltaEvent());

        StepVerifier.create(eventFlux)
                .expectNext("converted data")
                .verifyComplete();
    }

    private String getTestResponseCreatedEvent() {
        return "{\"type\":\"response.created\",\"response\":{\"id\":\"resp_681795369d3881918bffb873c2978e0b05b31444ed8e4035\",\"object\":\"response\",\"created_at\":1746375990,\"status\":\"in_progress\",\"error\":null,\"incomplete_details\":null,\"instructions\":null,\"max_output_tokens\":null,\"model\":\"gpt-4.1-mini-2025-04-14\",\"output\":[],\"parallel_tool_calls\":true,\"previous_response_id\":null,\"reasoning\":{\"effort\":null,\"summary\":null},\"service_tier\":\"auto\",\"store\":true,\"temperature\":1.0,\"text\":{\"format\":{\"type\":\"json_schema\",\"description\":null,\"name\":\"recipe_collection\",\"schema\":{\"type\":\"object\",\"properties\":{\"recipes\":{\"type\":\"array\",\"description\":\"An array of recipe objects.\",\"items\":{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\",\"description\":\"The name of the recipe.\"},\"cook_time\":{\"type\":\"string\",\"description\":\"The cook time for the recipe.\"},\"user_missing_ingredients\":{\"type\":\"array\",\"description\":\"List of ingredients the user is missing to prepare the recipe.\",\"items\":{\"type\":\"string\",\"description\":\"The name of the missing ingredient.\"}}},\"required\":[\"name\",\"cook_time\",\"user_missing_ingredients\"],\"additionalProperties\":false}}},\"required\":[\"recipes\"],\"additionalProperties\":false},\"strict\":true}},\"tool_choice\":\"required\",\"tools\":[{\"type\":\"web_search_preview\",\"search_context_size\":\"medium\",\"user_location\":{\"type\":\"approximate\",\"city\":null,\"country\":\"US\",\"region\":null,\"timezone\":null}}],\"top_p\":1.0,\"truncation\":\"disabled\",\"usage\":null,\"user\":null,\"metadata\":{}}}";
    }

    private String getTestOutputTextDeltaEvent() {
        return "{\"type\":\"response.output_text.delta\",\"item_id\":\"msg_68179539e498819185acc3aa44678f9105b31444ed8e4035\",\"output_index\":1,\"content_index\":0,\"delta\":\"name\"}";
    }
}