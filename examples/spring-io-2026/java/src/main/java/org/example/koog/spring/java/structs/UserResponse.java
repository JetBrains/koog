package org.example.koog.spring.java.structs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record UserResponse(
        @JsonProperty("user_confirmed") Boolean userConfirmed,
        @JsonProperty("response") String response
) {
    @JsonCreator
    public UserResponse {
    }
}
