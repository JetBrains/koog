package com.example.spring_boot_kotlin_yaml.controller

import com.example.spring_boot_kotlin_yaml.service.AgentService
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class ChatController(val aiService: AgentService) {

    @PostMapping(value = ["/chat"])
    fun chat(@RequestBody request: ChatRequest): ChatResponse? = runBlocking {
        try {
            val result = aiService.createAndRunAgent(request.prompt)
            return@runBlocking ChatResponse(result)
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to run an agent: ${e.message}", e)
        }
    }
}


data class ChatRequest(val prompt: String)

data class ChatResponse(val response: String)