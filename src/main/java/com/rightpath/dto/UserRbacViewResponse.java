package com.rightpath.dto;

import java.util.Set;

public record UserRbacViewResponse(
        String userEmail,
        Set<String> roles,
        Set<String> permissions
) {
}
