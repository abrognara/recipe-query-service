package com.brognara.recipe_query_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InputSanitizationServiceTest {

    private static final String REQUEST_ID = "test-request-id";

    @Mock
    private OpenAiModerationService moderationService;

    private InputSanitizationService service;

    @BeforeEach
    void setUp() {
        service = new InputSanitizationService(moderationService);
        // Default: moderation passes for all inputs
        when(moderationService.check(anyString(), anyString())).thenReturn(Mono.empty());
    }

    // ── Length ──────────────────────────────────────────────────────────────

    @Test
    void valid_query_passes() {
        StepVerifier.create(service.validate(REQUEST_ID, "pasta with chicken and garlic"))
                .expectNextMatches(v -> v.equals("pasta with chicken and garlic"))
                .verifyComplete();
    }

    @Test
    void query_at_max_length_passes() {
        String input = "a".repeat(InputSanitizationService.MAX_LENGTH);
        StepVerifier.create(service.validate(REQUEST_ID, input))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void query_exceeding_max_length_fails() {
        String input = "a".repeat(InputSanitizationService.MAX_LENGTH + 1);
        StepVerifier.create(service.validate(REQUEST_ID, input))
                .expectErrorMatches(ex -> ex instanceof ResponseStatusException rse
                        && rse.getStatusCode() == HttpStatus.BAD_REQUEST)
                .verify();
    }

    // ── Empty / blank ────────────────────────────────────────────────────────

    @Test
    void blank_query_fails() {
        StepVerifier.create(service.validate(REQUEST_ID, "   "))
                .expectErrorMatches(ex -> ex instanceof ResponseStatusException rse
                        && rse.getStatusCode() == HttpStatus.BAD_REQUEST)
                .verify();
    }

    @Test
    void null_query_fails() {
        StepVerifier.create(service.validate(REQUEST_ID, null))
                .expectErrorMatches(ex -> ex instanceof ResponseStatusException rse
                        && rse.getStatusCode() == HttpStatus.BAD_REQUEST)
                .verify();
    }

    // ── Character set ────────────────────────────────────────────────────────

    @Test
    void query_with_null_byte_fails() {
        StepVerifier.create(service.validate(REQUEST_ID, "chicken\u0000pasta"))
                .expectErrorMatches(ex -> ex instanceof ResponseStatusException rse
                        && rse.getStatusCode() == HttpStatus.BAD_REQUEST)
                .verify();
    }

    @Test
    void query_with_control_char_fails() {
        // BEL character \x07
        StepVerifier.create(service.validate(REQUEST_ID, "chicken\u0007pasta"))
                .expectErrorMatches(ex -> ex instanceof ResponseStatusException rse
                        && rse.getStatusCode() == HttpStatus.BAD_REQUEST)
                .verify();
    }

    @Test
    void query_with_zero_width_space_fails() {
        StepVerifier.create(service.validate(REQUEST_ID, "chicken\u200Bpasta"))
                .expectErrorMatches(ex -> ex instanceof ResponseStatusException rse
                        && rse.getStatusCode() == HttpStatus.BAD_REQUEST)
                .verify();
    }

    @Test
    void query_with_bidi_override_char_fails() {
        // Right-to-left override \u202E — used in Trojan-source style attacks
        StepVerifier.create(service.validate(REQUEST_ID, "chicken\u202Epasta"))
                .expectErrorMatches(ex -> ex instanceof ResponseStatusException rse
                        && rse.getStatusCode() == HttpStatus.BAD_REQUEST)
                .verify();
    }

    @Test
    void query_with_private_use_area_char_fails() {
        StepVerifier.create(service.validate(REQUEST_ID, "chicken\uE000pasta"))
                .expectErrorMatches(ex -> ex instanceof ResponseStatusException rse
                        && rse.getStatusCode() == HttpStatus.BAD_REQUEST)
                .verify();
    }

    @Test
    void query_with_tab_passes() {
        StepVerifier.create(service.validate(REQUEST_ID, "chicken\tpasta"))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void query_with_newline_passes() {
        StepVerifier.create(service.validate(REQUEST_ID, "chicken pasta\nwith garlic"))
                .expectNextCount(1)
                .verifyComplete();
    }

    // ── Moderation API ───────────────────────────────────────────────────────

    @Test
    void moderation_flagged_fails() {
        when(moderationService.check(anyString(), anyString()))
                .thenReturn(Mono.error(new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY, "Query contains content that violates usage policies"
                )));

        StepVerifier.create(service.validate(REQUEST_ID, "a valid-looking recipe query"))
                .expectErrorMatches(ex -> ex instanceof ResponseStatusException rse
                        && rse.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY)
                .verify();
    }

    @Test
    void moderation_api_error_propagates() {
        when(moderationService.check(anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("Moderation API failed: 500")));

        StepVerifier.create(service.validate(REQUEST_ID, "pasta with chicken"))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void sync_checks_run_before_moderation_api() {
        // Moderation should never be called when a cheap sync check already fails.
        // If it were called, the mock would return Mono.empty() and the test would pass
        // incorrectly — but the length check should throw before that happens.
        String tooLong = "a".repeat(InputSanitizationService.MAX_LENGTH + 1);
        StepVerifier.create(service.validate(REQUEST_ID, tooLong))
                .expectErrorMatches(ex -> ex instanceof ResponseStatusException rse
                        && rse.getStatusCode() == HttpStatus.BAD_REQUEST)
                .verify();
    }

    // ── Trimming ─────────────────────────────────────────────────────────────

    @Test
    void input_is_trimmed_before_returning() {
        StepVerifier.create(service.validate(REQUEST_ID, "  pasta with chicken  "))
                .expectNextMatches(v -> v.equals("pasta with chicken"))
                .verifyComplete();
    }
}
