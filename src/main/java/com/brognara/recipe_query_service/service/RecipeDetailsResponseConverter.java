package com.brognara.recipe_query_service.service;

import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import reactor.core.publisher.Flux;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class RecipeDetailsResponseConverter {
    private ConcurrentMap<String, String> responseMap = new ConcurrentHashMap<>();
    
    public Flux<String> parse(String responseId, String nextToken) {
        log.info("Parsing nextToken: {}", nextToken);
        // start of new json object
        if (nextToken.contains("{")) {
            responseMap.put(responseId, nextToken);
            return Flux.empty();

        // end of json object
        } else if (nextToken.contains("}")) {
            String prevObjEndStr = "";
            if (nextToken.length() == 1) {
                prevObjEndStr = nextToken;
            } else {
                prevObjEndStr = nextToken.substring(0, nextToken.indexOf("}") + 1);
            }

            String possibleRemainingStr = nextToken.substring(nextToken.indexOf("}"));

            String completeResponse = responseMap.get(responseId) + prevObjEndStr;
            responseMap.put(responseId, possibleRemainingStr);

            log.info("Returning complete response: {}", completeResponse);
            return Flux.just(completeResponse);
        } else {
            responseMap.put(responseId, responseMap.get(responseId) + nextToken);
            return Flux.empty();
        }
    }
}
