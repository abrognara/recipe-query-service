package com.brognara.recipe_query_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.ai.openai.OpenAiChatModel;

@SpringBootTest
class RecipeQueryServiceApplicationTests {

	@MockBean
	private OpenAiChatModel openAiChatModel;

//	@Test
	void contextLoads() {
	}

}
