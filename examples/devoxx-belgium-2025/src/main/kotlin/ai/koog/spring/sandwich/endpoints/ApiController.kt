package ai.koog.spring.sandwich.endpoints

import ai.koog.agents.core.agent.AIAgentState
import ai.koog.spring.sandwich.agents.KoogAgentService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api")
internal class ApiController(
    /***/
    val agentService: KoogAgentService // Some new magic 🪄
) {
    @PostMapping("/support")
    suspend fun launchSupportAgent(
        principal: Principal,
        @RequestBody request: SupportRequest
    ): ResponseEntity<AgentResponse> {
        val agentId = agentService.launchSupportAgent(
            userId = principal.name,
            question = request.question
        )
        return ResponseEntity.ok(AgentResponse(agentId))
    }

    @GetMapping("/agents")
    suspend fun listAgents(principal: Principal): ResponseEntity<List<String>> {
        val userId = principal.name
        return ResponseEntity.ok(agentService.getAgentIds(userId))
    }

    @GetMapping("/agent/{id}/checkpoints")
    suspend fun listCheckpoints(@PathVariable id: String): ResponseEntity<List<KoogAgentService.CheckpointInfo>> {
        return ResponseEntity.ok(agentService.getCheckpoints(id))
    }

    @GetMapping("/agents/{id}/status")
    suspend fun status(@PathVariable id: String): ResponseEntity<String> {
        return try {
            when (val state = agentService.getState(agentId = id)) {
                is AIAgentState.Failed<*> -> ResponseEntity.internalServerError().body("Agent failed")
                is AIAgentState.Finished<*> -> ResponseEntity.ok("Agent finished with result: ${state.result}")
                is AIAgentState.NotStarted<*> -> ResponseEntity.ok("Agent not started")
                is AIAgentState.Running<*> -> ResponseEntity.ok("Agent is running...")
                is AIAgentState.Starting<*> -> ResponseEntity.ok("Agent is starting...")
            }
        } catch (e: Throwable) {
            ResponseEntity.badRequest().body(e.message)
        }
    }

    @PutMapping("/agents/{id}/rollback/{checkpointId}")
    suspend fun rollback(
        @PathVariable id: String,
        @PathVariable checkpointId: String
    ): ResponseEntity<String> {
        try {
            agentService.rollback(agentId = id, checkpointId = checkpointId)
            return ResponseEntity.ok("Rolled back to $checkpointId!")
        } catch (e: Throwable) {
            return ResponseEntity.badRequest().body(e.message)
        }
    }

    @JvmRecord
    data class SupportRequest(val question: String)

    @JvmRecord
    data class AgentResponse(val agentId: String)
}
