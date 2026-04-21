package org.example.koog.spring.kotlin.structs

import com.fasterxml.jackson.annotation.JsonCreator
import kotlinx.serialization.Serializable

@Serializable
data class TransferResult @JsonCreator constructor(
    val wasSuccessful: Boolean,
    val problem: String,
    val transactionId: String
) 