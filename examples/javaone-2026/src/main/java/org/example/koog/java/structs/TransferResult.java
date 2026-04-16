package org.example.koog.java.structs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;

public record TransferResult(
        @JsonProperty("was_successful") boolean wasSuccessful,
        @JsonProperty("problem") @Nullable String problem,
        @JsonProperty("transaction_id") @Nullable String transactionId
) {
    @JsonCreator
    public TransferResult {
    }
}