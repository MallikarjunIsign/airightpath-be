package com.rightpath.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestResultEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Lob
	private String input;

	@Lob
	private String expectedOutput;

	@Lob
	private String actualOutput;
	
	@Column(columnDefinition = "TEXT")
    private String testCasesJson;

	@Column(nullable = true)
	private Boolean passed;
	
	   private String questionId; 

	
	 @ManyToOne(fetch = FetchType.LAZY)
	    @JoinColumn(name = "submission_id", nullable = false)
	    private CodeSubmission submission;

	public String getInput() {
		return input;
	}

	public void setInput(String input) {
		this.input = input;
	}

	

	
}
