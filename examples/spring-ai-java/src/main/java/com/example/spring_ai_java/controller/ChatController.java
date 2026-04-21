package com.example.spring_ai_java.controller;

import com.example.spring_ai_java.service.customersupport.CustomerSupportGraphService;
import com.example.spring_ai_java.service.deepresearch.DeepLiteratureResearchService;
import com.example.spring_ai_java.service.knowledgebase.KnowledgeBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final KnowledgeBaseService knowledgeBaseService;
    private final CustomerSupportGraphService customerSupportService;
    private final DeepLiteratureResearchService literatureResearchService;

    public ChatController(
        KnowledgeBaseService knowledgeBaseService,
        CustomerSupportGraphService customerSupportService,
        DeepLiteratureResearchService literatureResearchService
    ) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.customerSupportService = customerSupportService;
        this.literatureResearchService = literatureResearchService;
    }

    @PostMapping("/knowledgeBase")
    public ChatResponse knowledgeBase(@RequestBody ChatRequest request) {
        try {
            String result = knowledgeBaseService.createAndRunAgent(request.prompt());
            return new ChatResponse(result);
        } catch (Exception e) {
            logger.error("Failed to run an agent", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to run an agent");
        }
    }

    @PostMapping("/customerSupport")
    public ChatResponse customerSupport(@RequestBody ChatRequest request) {
        try {
            String result = customerSupportService.createAndRunAgent(request.prompt(), "customer-123");
            return new ChatResponse(result);
        } catch (Exception e) {
            logger.error("Failed to run an agent", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to run an agent");
        }
    }

    @PostMapping("/deepResearch")
    public ChatResponse deepResearch(@RequestBody ChatRequest request) {
        try {
            String result = literatureResearchService.createAndRunAgent(request.prompt());
            return new ChatResponse(result);
        } catch (Exception e) {
            logger.error("Failed to run an agent", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to run an agent");
        }
    }

    public record ChatRequest(String prompt) {}

    public record ChatResponse(String response) {}
}
