package org.example.koog.spring.kotlin.endpoints

import org.example.koog.spring.kotlin.agents.customersupport.BankingAgentService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
class ChatController(
    val agentService: BankingAgentService,
) {

    @PostMapping(value = ["/support"])
    suspend fun customerSupport(
        principal: Principal,
        @RequestBody request: ChatRequest
    ): ChatResponse {
        val response = agentService.launchSupportAgent(principal.getName(), request.question)
        return ChatResponse(response)
    }
}

data class ChatRequest(val question: String)

data class ChatResponse(val response: String)
