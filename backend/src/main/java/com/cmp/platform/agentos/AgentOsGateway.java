package com.cmp.platform.agentos;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AgentOsGateway {

    private final AgentOsService service;

    AgentOsGateway(AgentOsService service) {
        this.service = service;
    }

    public Map<String, Object> createTask(String idempotencyKey, Map<String, Object> request) {
        return service.createTask(idempotencyKey, request).body();
    }

    public Map<String, Object> createHumanConfirmation(Map<String, Object> request) {
        return service.createHumanConfirmation(request);
    }
}
