package com.rightpath.dto;

import com.rightpath.rbac.RoleName;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RoleAssignmentRequest(
        @NotBlank @Email String userEmail,
        @NotNull RoleName role
) {
}
