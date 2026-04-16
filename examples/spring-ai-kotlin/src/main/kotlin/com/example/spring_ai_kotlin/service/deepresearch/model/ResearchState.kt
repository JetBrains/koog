package com.example.spring_ai_kotlin.service.deepresearch.model

import kotlinx.serialization.Serializable

@Serializable
data class ResearchState(
    val topic: String,
    val queries: MutableList<String> = mutableListOf(),
    val papers: MutableList<Paper> = mutableListOf(),
    val selectedPapers: MutableList<Paper> = mutableListOf(),
    val notes: MutableList<Note> = mutableListOf(),
    var finalSummary: String? = null
)
