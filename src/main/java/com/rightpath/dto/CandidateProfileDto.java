package com.rightpath.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CandidateProfileDto {
	private String email;
	private String firstName;
	private String lastName;
	private String mobileNumber;
	private String alternativeMobileNumber;
	private byte[] profileImage;
}