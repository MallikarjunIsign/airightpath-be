package com.rightpath.dto;

public record UserInfo(
        String email,
        String firstName,
        String lastName,
        String mobileNumber,
        String alternativeMobileNumber
) {
}
