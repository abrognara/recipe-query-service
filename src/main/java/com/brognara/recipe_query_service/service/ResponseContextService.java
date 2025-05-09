package com.brognara.recipe_query_service.service;

import com.brognara.recipe_query_service.model.ResponseContext;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ResponseContextService {

    private final ConcurrentMap<String, ResponseContext> responseContextMap = new ConcurrentHashMap<>();

    public void initContext(final String appRequestId, final String openAiRequestId) {
        final ResponseContext responseContext = new ResponseContext();
        responseContext.setAppResponseId(appRequestId);
        responseContext.setOpenAiResponseId(openAiRequestId);
        responseContextMap.put(appRequestId, responseContext);
    }

    public ResponseContext getContext(final String appRequestId) {
        return responseContextMap.get(appRequestId);
    }

}
