package org.example.koog.spring.java.structs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record UserAccountInfo(
        @JsonProperty("username") String username,
        @JsonProperty("email") String email,
        @JsonProperty("phoneNumber") String phoneNumber,
        @JsonProperty("address") String address,
        @JsonProperty("accountNumber") String accountNumber,
        @JsonProperty("accountType") String accountType,
        @JsonProperty("balance") Integer balance,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("lastTransactionTime") Instant lastTransactionTime
) {
    @JsonCreator
    public UserAccountInfo {}
}
