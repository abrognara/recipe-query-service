package com.brognara.recipe_query_service.controller;

import com.brognara.recipe_query_service.service.*;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

class QueryControllerTest {

    @Mock
    private RequestValidatorService validatorService;

    @Mock
    private PantryService pantryService;

    @InjectMocks
    private QueryController queryController;

    @Test
    void queryRecipesOverview() {
//        queryController.queryRecipesOverview();
    }
}