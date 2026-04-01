package com.rightpath.dto;

import com.rightpath.entity.Users;

import jakarta.persistence.Column;
import jakarta.persistence.Lob;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UsersDto {
	@Pattern(regexp = "^[a-zA-Z0-9._%+-]+@gmail\\.com$", message = "Email must be in the format 'something@gmail.com'")
	@Size(min = 11, max = 30, message = "Email must be between 11 and 30 characters")
	@NotBlank(message = "Email is mandatory")
	private String email;
	@NotBlank(message = "First name is mandatory")
	@Size(min = 1, max = 25, message = "First name must be between 1 and 25 characters")
	private String firstName;
	@NotBlank(message = "Last name is mandatory")
	@Size(min = 1, max = 25, message = "Last name must be between 1 and 25 characters")
	private String lastName;
	@NotBlank(message = "Password is mandatory")
	@Pattern(regexp = "^(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>?/]).*$",

			message = "Password must contain at least one special character")
	private String password;
	private Boolean enabled = true;
	@Column(nullable = false, unique = true)
	private String mobileNumber;

	private String alternativeMobileNumber;

	@Lob
	@Column(name = "profileImage", columnDefinition = "LONGBLOB")
	private byte[] profileImage;

	public UsersDto(Users users) {
		this.email = users.getEmail();
		this.firstName = users.getFirstName();
		this.lastName = users.getLastName();
		this.password = users.getPassword();
		this.enabled = users.getEnabled();
		this.mobileNumber = users.getMobileNumber();
		this.alternativeMobileNumber = users.getAlternativeMobileNumber();
		this.profileImage = users.getProfileImage();
	}
}
