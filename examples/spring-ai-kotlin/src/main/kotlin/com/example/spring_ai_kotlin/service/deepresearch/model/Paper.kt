package com.example.spring_ai_kotlin.service.deepresearch.model

import kotlinx.serialization.Serializable

@Serializable
data class Paper(
    val id: String,
    val title: String,
    val authors: List<String>,
    val abstract: String,
    val url: String,
    val published: String
)
