package com.example.agent.model

import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.llm.LLModel

class Models {
    companion object {
        fun getLLModel(id: String): LLModel {
            return when (id) {
                GoogleModels.Gemini2_0Flash.id -> {
                    GoogleModels.Gemini2_0Flash
                }
                GoogleModels.Gemini2_0Flash001.id -> {
                    GoogleModels.Gemini2_0Flash001
                }
                GoogleModels.Gemini2_0FlashLite.id -> {
                    GoogleModels.Gemini2_0FlashLite
                }
                GoogleModels.Gemini2_0FlashLite001.id -> {
                    GoogleModels.Gemini2_0FlashLite001
                }
                GoogleModels.Gemini2_5Pro.id -> {
                    GoogleModels.Gemini2_5Pro
                }
                GoogleModels.Gemini2_5Flash.id -> {
                    GoogleModels.Gemini2_5Flash
                }
                GoogleModels.Gemini2_5FlashLite.id -> {
                    GoogleModels.Gemini2_5FlashLite
                }
                else -> {
                    throw IllegalArgumentException("Unknown id: $id")
                }
            }
        }
    }
}
