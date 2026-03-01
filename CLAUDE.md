# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
mvn clean package -DskipTests

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=QueryControllerTest

# Run locally (uses application-local.properties)
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Docker
docker build -t recipe-query-service .
docker-compose up
```

Required environment variables for local runs: `OPENAI_API_KEY`, and Upstash credentials set in `application-local.properties`.

## Architecture

Spring Boot WebFlux (reactive) microservice that finds recipes via semantic search and OpenAI web search.

**Primary query flow** (`POST /api/v1/recipe-query`):
1. Create Redis conversation session
2. In parallel: preprocess query for semantic meaning + generate `RecipeFilters` from query (both via LLM)
3. Embed the preprocessed query (`text-embedding-3-small`)
4. Search Upstash Vector DB with embedding + metadata filters
5. If results are insufficient, fall back to `RecipeResearchService` which uses OpenAI web search (Responses API)
6. Embed and upsert newly researched recipes into the vector store for future queries
7. Persist conversation to Redis and return `RecipeQueryResponse`

**External integrations:**
- **OpenAI** — GPT-4.1 (`gpt-4.1`) for LLM calls, `text-embedding-3-small` for embeddings, Responses API with web search tool
- **Upstash Vector** — stores recipe summary embeddings with metadata filters; queried for semantic search
- **Upstash Redis** — stores conversation sessions keyed as `conversation:{userId}:{convoId}`

## Key Files

| Layer | File |
|---|---|
| REST endpoints | `controller/QueryController.java`, `controller/ConversationController.java` |
| Query preprocessing & filter extraction | `service/PreProcessingService.java` |
| OpenAI Responses API (streaming + web search) | `service/OpenAiResponsesService.java`, `service/OpenAiResponsesApiEventParser.java` |
| Embeddings | `service/OpenAiEmbeddingService.java` |
| Vector DB operations | `service/VectorDbServiceImpl.java` |
| Recipe web research | `service/RecipeResearchService.java` |
| Conversation storage (Redis) | `service/ConversationSessionService.java` |
| LLM response JSON schemas | `src/main/resources/*.json` |
| Config beans | `config/` (UpstashVectorConfig, RedisConfig, OpenAiResponseJsonSchemaConfig, etc.) |

## Data Models

- **`RecipeFilters`** — rich filter object covering cuisine, mealType, ingredients, appliances, nutrition goals, dietary restrictions, etc. Serialized via `asMap()` / `fromMap()` for vector DB metadata.
- **`RecipeResearchResponse`** / **`Recipe`** — output of web search research; each recipe has url, recipeName, description, imgUrl, and filters.
- **`Conversation`** / **`Message`** — Redis-persisted conversation history with prompt/response messages.
- **`OpenAiResponsesRequest`** — builder-pattern request object for the OpenAI Responses API, supports messages, tools, streaming, and JSON schema output.

## LLM Structured Output

JSON schemas in `src/main/resources/` define the structured response formats for LLM calls:
- `recipe-research-and-generate-filters-schema.json` — recipe research with filter extraction
- `query-parse-generate-filters-schema.json` — parse user query into `RecipeFilters`
- `recipes-overview-response-schema-web-search.json` / `recipes-overview-response-schema-standard.json` — recipe result shapes

These are loaded as Spring beans in `OpenAiResponseJsonSchemaConfig` and injected into services.

## Reactive Patterns

The codebase uses Project Reactor (`Mono`, `Flux`) throughout. Streaming responses from OpenAI are parsed by `OpenAiResponsesApiEventParser` and returned as `Flux<String>` to clients. Avoid blocking calls inside reactive chains.
