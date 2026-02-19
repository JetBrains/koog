package org.example.koog.java.endpoints;

import ai.koog.agents.core.agent.AIAgentState;
import org.example.koog.java.agents.KoogAgentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

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

    @GetMapping("/agents")
    public ResponseEntity<List<String>> listAgents(Principal principal) {
        String userId = principal.getName();
        return ResponseEntity.ok(agentService.getAgentIds(userId));
    }

    @GetMapping("/agents/{id}/status")
    public ResponseEntity<String> status(@PathVariable String id) {
        try {
            var state = agentService.getState(id);
            if (state instanceof AIAgentState.Failed<?>) {
                return ResponseEntity.internalServerError().body("Agent failed");
            } else if (state instanceof AIAgentState.Finished<?> finished) {
                return ResponseEntity.ok("Agent finished with result: " + finished.getResult());
            } else if (state instanceof AIAgentState.NotStarted<?>) {
                return ResponseEntity.ok("Agent not started");
            } else if (state instanceof AIAgentState.Running<?>) {
                return ResponseEntity.ok("Agent is running...");
            } else if (state instanceof AIAgentState.Starting<?>) {
                return ResponseEntity.ok("Agent is starting...");
            } else {
                return ResponseEntity.ok("Unknown state");
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    public record SupportRequest(String question) {
    }

    public record AgentResponse(String agentId) {
    }
}
