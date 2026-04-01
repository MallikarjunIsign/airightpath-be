package com.rightpath.dto;

import com.rightpath.entity.Users;

import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;

public class ResumeDTO {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY) // auto generated
	private Long id; // for id

	private String fileName; // for fileName

	private String fileType; // file Type

	@Lob
	private byte[] data; // for storing resume

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "user_id", nullable = false)
	private Users users; // to get the details of the user

}
