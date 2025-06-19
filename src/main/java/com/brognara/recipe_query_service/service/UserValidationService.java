package com.brognara.recipe_query_service.service;

import reactor.core.publisher.Mono;

public interface UserValidationService {
    // TODO user token should be included
    Mono<String> validateUser();
}
