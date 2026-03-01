package com.brognara.recipe_query_service.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Map;

@Configuration
public class OpenAiResponseJsonSchemaConfig {

    @Autowired
    private ObjectMapper objectMapper;

    @Bean
    public Map<String, Object> recipeQueryResultsJsonSchema() {
        return loadJsonSchema("recipes-overview-response-schema-web-search.json");
    }

    @Bean
    public Map<String, Object> recipeQueryParseJsonSchema() {
        return loadJsonSchema("query-parse-generate-filters-schema.json");
    }

    @Bean
    public Map<String, Object> recipeResearchJsonSchema() {
        return loadJsonSchema("recipe-research-and-generate-filters-schema.json");
    }

    private Map<String, Object> loadJsonSchema(final String jsonSchemaFilename) {
        try {
            return objectMapper.readValue(
                    new ClassPathResource(jsonSchemaFilename).getInputStream(),
                    new TypeReference<Map<String, Object>>() {}
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
