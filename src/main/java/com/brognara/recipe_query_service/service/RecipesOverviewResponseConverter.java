package com.brognara.recipe_query_service.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Flux;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class RecipesOverviewResponseConverter {
    private final ConcurrentMap<String, List<String>> responseMap = new ConcurrentHashMap<>();
    private final AtomicInteger openBracketCount = new AtomicInteger(0);
    
    public Flux<String> parse(String responseId, String nextToken) {
        log.info("Parsing nextToken: <{}>", nextToken);
        if (nextToken.isEmpty()) {
            return Flux.empty();
        }

        // response start at first open bracket
        if (isResponseStart(nextToken)) {
            return Flux.empty();
        }

        // response end at last close bracket
        if (isResponseEnd(nextToken)) {
            responseMap.remove(responseId);
            return Flux.empty();
        }

        // start of new json object
        if (nextToken.contains("{")) {
            List<String> responseList = new LinkedList<>();
            responseList.add(nextToken);
            responseMap.put(responseId, responseList);
            return Flux.empty();

        // end of json object
        } else if (nextToken.contains("}")) {
            String prevObjEndStr = "";
            if (nextToken.length() == 1) {
                prevObjEndStr = nextToken;
            } else {
                prevObjEndStr = nextToken.substring(0, nextToken.indexOf("}") + 1);
            }
//            String possibleRemainingStr = nextToken.substring(nextToken.indexOf("}"));
            responseMap.get(responseId).add(prevObjEndStr);
            String completeResponse = String.join("", responseMap.get(responseId))
                    .replace("\n", ""); // TODO could we remove newlines in the loop?
            responseMap.put(responseId, new LinkedList<>());

            log.info("Returning complete response: {}", completeResponse);
            return Flux.just(completeResponse);
        } else if (responseMap.containsKey(responseId)) {
            responseMap.get(responseId).add(nextToken);
            return Flux.empty();
        }

        return Flux.empty();
    }

    private boolean isResponseStart(final String nextToken) {
        if (nextToken.contains("[")) {
            int prev = openBracketCount.getAndIncrement();
            return prev == 0;
        }
        return false;
    }

    private boolean isResponseEnd(final String nextToken) {
        if (nextToken.contains("]")) {
            int cur = openBracketCount.decrementAndGet();
            return cur == 0;
        }
        return false;
    }

}
