package com.rightpath.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "resume")
public class Resume {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY) // auto generated
	private Long id; // for id

	private String fileName; // for fileName

	private String fileType; // file Type

	@Lob
	private byte[] data; // for storing resume

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private Users users; // to get the details of the user
	
	
	
	  @ManyToOne(fetch = FetchType.LAZY)
	    @JoinColumn(name = "job_post_id", nullable = false)
	    private JobPost jobPost;

}