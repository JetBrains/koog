package org.example.koog.spring.kotlin.structs

import com.fasterxml.jackson.annotation.JsonCreator
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class UserAccountInfo @JsonCreator constructor(
    val username: String,
    val email: String,
    val phoneNumber: String,
    val address: String,
    val accountNumber: String,
    val accountType: String,
    val balance: Int,
    val createdAt: Instant,
    val lastTransactionTime: Instant
)
