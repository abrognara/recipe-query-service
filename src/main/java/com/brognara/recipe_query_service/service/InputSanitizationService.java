package com.brognara.recipe_query_service.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;

@Log4j2
@Service
public class InputSanitizationService {

    static final int MAX_LENGTH = 500;

    /**
     * Blocks control characters (excluding tab \x09, newline \x0A, carriage return \x0D),
     * zero-width/invisible Unicode, bidirectional control characters, and private-use area
     * codepoints — all common vectors for encoding attacks or prompt injection obfuscation.
     */
    private static final Pattern DANGEROUS_CHARS = Pattern.compile(
            "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F" +      // control chars (excl. tab, LF, CR)
            "\\u200B-\\u200F" +                                // zero-width spaces / joiners
            "\\u202A-\\u202E" +                                // bidi embedding / override chars
            "\\u2066-\\u2069" +                                // bidi isolate chars
            "\\uFEFF" +                                        // BOM / zero-width no-break space
            "\\uE000-\\uF8FF" +                                // private use area
            "\\uFFF0-\\uFFFF]"                                 // Unicode specials block
    );

    private final OpenAiModerationService moderationService;

    @Autowired
    public InputSanitizationService(OpenAiModerationService moderationService) {
        this.moderationService = moderationService;
    }

    /**
     * Validates the raw user input. Runs cheap sync checks first (length, character set),
     * then calls the OpenAI Moderation API for content classification.
     * Returns the trimmed input on success, or signals a {@link ResponseStatusException} on any violation.
     *
     * @param requestId correlation ID for log tracing
     * @param input     raw user-supplied query string
     */
    public Mono<String> validate(String requestId, String input) {
        return Mono.fromCallable(() -> {
                    if (input == null || input.isBlank()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query must not be empty");
                    }

                    String trimmed = input.strip();
                    checkLength(trimmed);
                    checkCharacterSet(trimmed);
                    return trimmed;
                })
                .flatMap(trimmed -> moderationService.check(trimmed, requestId)
                        .thenReturn(trimmed));
    }

    private void checkLength(String input) {
        if (input.length() > MAX_LENGTH) {
            log.warn("Input rejected: length {} exceeds max {}", input.length(), MAX_LENGTH);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Query exceeds maximum allowed length of " + MAX_LENGTH + " characters"
            );
        }
    }

    private void checkCharacterSet(String input) {
        if (DANGEROUS_CHARS.matcher(input).find()) {
            log.warn("Input rejected: contains disallowed characters");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query contains disallowed characters");
        }
    }
}
