package org.example.koog.spring.kotlin.structs

import com.fasterxml.jackson.annotation.JsonCreator
import kotlinx.serialization.Serializable

@Serializable
data class UserResponse @JsonCreator constructor(
    val userConfirmed: Boolean,
    val response: String
)
