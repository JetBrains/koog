package org.example.koog.spring.kotlin.structs

import com.fasterxml.jackson.annotation.JsonCreator
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Transaction @JsonCreator constructor(
    val id: String,
    val sender: String,
    val recipient: String,
    val amount: Int,
    val timestamp: Instant,
    val status: Status
) {
    enum class Status {
        ERROR,
        SUCCESS,
        PENDING,
        REJECTED,
        DISPUTED,
        CANCELED
    }
}