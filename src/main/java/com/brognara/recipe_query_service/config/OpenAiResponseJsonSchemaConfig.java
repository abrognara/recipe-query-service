package com.brognara.recipe_query_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

@Configuration
public class OpenAiResponseJsonSchemaConfig {

    @Autowired
    private ObjectMapper objectMapper;

    @Bean
    public Object recipeQueryResultsJsonSchema() {
        return loadJsonSchema("recipes-overview-response-schema-web-search.json");
    }

    @Bean
    public Object recipeQueryParseJsonSchema() {
        return loadJsonSchema("query-parse-schema.json");
    }

    private Object loadJsonSchema(final String jsonSchemaFilename) {
        try {
            return objectMapper.readValue(
                    new ClassPathResource(jsonSchemaFilename).getInputStream(),
                    Object.class
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
