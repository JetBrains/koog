package org.example.koog.java.endpoints;

import org.example.koog.java.agents.KoogAgentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api")
class ApiController {

    private final KoogAgentService agentService;

    public ApiController(KoogAgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/support")
    public ResponseEntity<AgentResponse> launchSupportAgent(
            Principal principal,
            @RequestBody SupportRequest request
    ) {
        var agentId = agentService.launchSupportAgent(principal.getName(), request.question());
        return ResponseEntity.ok(new AgentResponse(agentId));
    }


    public record SupportRequest(String question) {
    }

    public record AgentResponse(String agentId) {
    }
}
