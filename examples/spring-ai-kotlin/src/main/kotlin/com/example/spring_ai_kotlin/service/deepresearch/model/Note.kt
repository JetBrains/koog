package com.example.spring_ai_kotlin.service.deepresearch.model

import kotlinx.serialization.Serializable

@Serializable
data class Note(
    val paperId: String,
    val problem: String,
    val method: String,
    val findings: String,
    val limitations: String
)
