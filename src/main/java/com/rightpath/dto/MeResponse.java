package com.rightpath.dto;

import java.util.Set;

public record MeResponse(
        UserInfo user,
        Set<String> roles,
        Set<String> permissions
) {
}
