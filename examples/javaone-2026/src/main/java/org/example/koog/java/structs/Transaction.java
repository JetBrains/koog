package org.example.koog.java.structs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record Transaction(
        @JsonProperty("id") String id,
        @JsonProperty("sender") String sender,
        @JsonProperty("recipient") String recipient,
        @JsonProperty("amount") Integer amount,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("status") Status status
) {

    @JsonCreator
    public Transaction {}

    public enum Status {
        ERROR,
        SUCCESS,
        PENDING,
        REJECTED,
        DISPUTED,
        CANCELED
    }
}