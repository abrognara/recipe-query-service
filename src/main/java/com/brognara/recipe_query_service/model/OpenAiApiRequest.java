package com.brognara.recipe_query_service.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Builder
@Getter
@Setter
public class OpenAiApiRequest {
    private String model;
    private List<Input> inputList;
    private Text text;
    private List<Tool> tools; // TODO default null because its optional
    private ToolChoice toolChoice; // TODO default null because its optional
    private boolean stream;
    private String previousResponseId; // TODO default null because its optional

    @Setter
    @Getter
    @Builder
    public static class Input {
        private InputRole inputRole;
        private InputContent inputContent;
    }

    public enum InputRole {
        SYSTEM,
        USER;

        String asText() {
            return this.name().toLowerCase();
        }
    }

    @Getter
    public static class InputContent {
        private static final String TYPE_INPUT_TEXT = "input_text";
        private final String type = TYPE_INPUT_TEXT;
        private final String text;

        public InputContent(final String text) {
            this.text = text;
        }
    }

    @Setter
    @Getter
    @Builder
    public static class Text {
        // maps to 'format' = jsonSchema
        private Object format;
    }

    public enum Tool {
        WEB_SEARCH;

        String asText() {
            return this.name().toLowerCase();
        }
    }

    public enum ToolChoice {
        OPTIONAL,
        REQUIRED;

        String asText() {
            return this.name().toLowerCase();
        }
    }

    public Map<String, Object> getBody() {
        final Map<String, Object> body = new java.util.HashMap<>(Map.of(
                "model", model,
                "input", inputList.stream()
                        .map(input ->
                                Map.of(
                                        "role", input.getInputRole().asText(),
                                        "content", List.of(
                                                Map.of(
                                                        "type", input.getInputContent().getType(),
                                                        "text", input.getInputContent().getText()
                                                )
                                        )
                                )
                        ).toList(),
                "text", Map.of("format", text.getFormat()), // format = the json schema obj
                "tools", tools.stream().map(tool -> Map.of("type", tool.asText())).toList(),
                "tool_choice", toolChoice.asText(),
                "stream", stream
        ));

        if (previousResponseId != null) {
            body.put("previous_response_id", previousResponseId);
        }
        return body;
    }
}
