package org.knaw.huc.sdswitch.server.security.data;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Tokens(@JsonProperty("access_token") String accessToken,
                     @JsonProperty("token_type") String tokenType,
                     @JsonProperty("refresh_token") String refreshToken,
                     @JsonProperty("expires_in") long expiresIn,
                     @JsonProperty("id_token") String idToken) {
}
