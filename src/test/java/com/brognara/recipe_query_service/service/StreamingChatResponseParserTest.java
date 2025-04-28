package com.brognara.recipe_query_service.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import java.time.Duration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ExtendWith(MockitoExtension.class)
public class StreamingChatResponseParserTest {

    private StreamingChatResponseParser parser = new StreamingChatResponseParser();
    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testParse() {
        final String testRequestId = "testRequestId";
        Flux<String> result = OpenAiStreamingChatServiceTest.generateMockResponseStream()
            .delayElements(Duration.ofMillis(5))
            .flatMap(line -> {
                // log.info("Received line: {}", line);
                if (line.equals("[DONE]")) return Flux.empty();
                try {
                    JsonNode root = objectMapper.readTree(line);
                    String content = root
                        .path("choices").get(0)
                        .path("delta")
                        .path("content").asText("");
                    return Flux.just(content);
                } catch (Exception e) {
                    log.error("Error parsing JSON: {}", e.getMessage());
                    return Flux.empty();
                }
            })
            .flatMap(token -> parser.parse(testRequestId, token));

        // StepVerifier.create(result)
        //     .expectNextCount(1)
        //     .verifyComplete();
    }
}
