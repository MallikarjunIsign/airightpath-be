package com.rightpath.dto;

import java.util.Set;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserInfo user,
        Set<String> roles,
        Set<String> permissions
) {
}
