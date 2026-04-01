package com.rightpath.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) for carrying admin profile details.
 * Used to send/receive admin profile information between layers (e.g., controller and service).
 * Includes personal details and an optional profile image in byte[] format.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminProfileDto {

    /**
     * The email address of the admin user.
     * Used as a unique identifier for profile updates and retrievals.
     */
    private String email;

    /**
     * The first name of the admin user.
     */
    private String firstName;

    /**
     * The last name of the admin user.
     */
    private String lastName;

    /**
     * The primary mobile number of the admin.
     * Can be used for contact and authentication purposes.
     */
    private String mobileNumber;

    /**
     * An alternative contact number for the admin.
     * Optional field for backup communication.
     */
    private String alternativeMobileNumber;

    /**
     * Profile image data stored as a byte array.
     * Typically uploaded through a multipart request.
     */
    private byte[] profileImage;
}
