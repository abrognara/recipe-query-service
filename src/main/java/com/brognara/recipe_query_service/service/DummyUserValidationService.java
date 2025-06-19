package com.brognara.recipe_query_service.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class DummyUserValidationService implements UserValidationService {

    @Override
    public Mono<String> validateUser() {
        return Mono.just("user-123");
    }

}
