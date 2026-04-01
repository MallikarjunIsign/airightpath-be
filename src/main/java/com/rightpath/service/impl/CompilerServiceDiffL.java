package com.rightpath.service.impl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rightpath.dto.CodeErrorInfo;
import com.rightpath.dto.CodeSubmissionResponseDTO;
import com.rightpath.dto.TestCaseDTO;
import com.rightpath.entity.CodeSubmission;
import com.rightpath.entity.TestResultEntity;
import com.rightpath.exceptions.CompilerException;
import com.rightpath.repository.CodeSubmissionRepository;
import com.rightpath.service.CompilerService1;

import lombok.extern.slf4j.Slf4j;

@Service // Marks this class as a Spring Service
@Slf4j // Lombok annotation for SLF4J logging
public class CompilerServiceDiffL implements CompilerService1 {

	private static final Logger logger = LoggerFactory.getLogger(CompilerServiceDiffL.class);

	@Autowired // Injects the CodeSubmissionRepository bean
	private CodeSubmissionRepository submissionRepo;

	    private final ObjectMapper objectMapper = new ObjectMapper();
	    
	
	
//	@Override
//	public List<TestResultEntity> executeCode(CodeSubmission submissionRequest) {
//	    logger.info("Executing code for language: {}", submissionRequest.getLanguage());
//	    submissionRequest.setAttempted(true);
//	    validateSubmission(submissionRequest);
//
//	    List<TestResultEntity> testCases = submissionRequest.getTestResults();
//	    List<TestResultEntity> results = new ArrayList<>();
//
//	    // ✅ If test cases are present
//	    if (testCases != null && !testCases.isEmpty()) {
//	        for (TestResultEntity testCase : testCases) {
//	            String input = defaultIfNull(testCase.getInput());
//	            String expectedOutput = defaultIfNull(testCase.getExpectedOutput());
//	            
//
//	            TestResultEntity result = new TestResultEntity();
//	            result.setInput(input);
//	            result.setExpectedOutput(expectedOutput);
//	            result.setSubmission(submissionRequest);
//	            result.setQuestionId(submissionRequest.getQuestionId());
//	           
//
//	            try {
//	                logger.debug("Running test case with input: {}", input);
//	                String actualOutput = compileAndRunCode(submissionRequest.getScript(), input,
//	                        submissionRequest.getLanguage());
//
//	                result.setActualOutput(actualOutput);
//	                result.setPassed(expectedOutput.equals(actualOutput)); // ✅ Set passed
//	            } catch (Exception e) {
//	                logger.error("Execution failed for input [{}]: {}", input, e.getMessage(), e);
//	                result.setActualOutput("Runtime Error: " + e.getMessage());
//	                result.setPassed(false); // ✅ Mark failed if exception
//	            }
//
//	            results.add(result);
//	        }
//	    } else {
//	        // ✅ Free-run mode (no test cases): execute once without input
//	        TestResultEntity result = new TestResultEntity();
//	        result.setInput(""); // Optional
//	        result.setExpectedOutput(""); // Optional
//	        result.setSubmission(submissionRequest);
//	        result.setQuestionId(submissionRequest.getQuestionId()); 
//
//	        try {
//	            logger.debug("Running code in free-run mode");
//	            String output = compileAndRunCode(submissionRequest.getScript(), "", submissionRequest.getLanguage());
//	            result.setActualOutput(output);
//	            result.setPassed(null); // ✅ optional or omit this field if DB allows
//	            
//	        } catch (Exception e) {
//	            logger.error("Execution failed: {}", e.getMessage(), e);
//	            result.setActualOutput("Runtime Error: " + e.getMessage());
//	            result.setPassed(null); // or false depending on DB
//	           
//	        }
//
//	        results.add(result);
//	    }
//
//	    submissionRequest.setTestResults(results);
//	    submissionRepo.save(submissionRequest);
//
//	    logger.info("Execution completed. Total test cases processed: {}", results.size());
//	    return results;
//	}
	    
	    
	    @Override
	    public List<TestResultEntity> executeCode(CodeSubmission submissionRequest) {
	        logger.info("Executing code for language: {}", submissionRequest.getLanguage());
	        
	        // Mark as attempted
	        submissionRequest.setAttempted(true);
	        validateSubmission(submissionRequest);

	        List<TestResultEntity> testCases = submissionRequest.getTestResults();
	        List<TestResultEntity> results = new ArrayList<>();
	        boolean allPassed = true;

	        if (testCases != null && !testCases.isEmpty()) {
	            for (TestResultEntity testCase : testCases) {
	                TestResultEntity result = processTestCase(submissionRequest, testCase);
	                if (!result.getPassed()) {
	                    allPassed = false;
	                }
	                results.add(result);
	            }
	        } else {
	            // Free-run mode
	            TestResultEntity result = processFreeRun(submissionRequest);
	            results.add(result);
	            allPassed = false;
	        }

	        submissionRequest.setPassed(allPassed);
	        submissionRequest.setTestResults(results);
	        submissionRepo.save(submissionRequest);
	        
	        return results;
	    }

	    private TestResultEntity processTestCase(CodeSubmission submission, TestResultEntity testCase) {
	        TestResultEntity result = new TestResultEntity();
	        result.setInput(testCase.getInput());
	        result.setExpectedOutput(testCase.getExpectedOutput());
	        result.setSubmission(submission);
	        result.setQuestionId(submission.getQuestionId());

	        try {
	            String actualOutput = compileAndRunCode(submission.getScript(), testCase.getInput(), submission.getLanguage());
	            result.setActualOutput(actualOutput);
	            result.setPassed(testCase.getExpectedOutput().trim().equals(actualOutput.trim()));
	        } catch (CompilerException e) {
	            String rawError = e.getMessage();
	            result.setActualOutput("Runtime Error: " + rawError); // Clearly mark as runtime error
	            result.setPassed(false);
	        }

	        return result;
	    }


	    private TestResultEntity processFreeRun(CodeSubmission submission) {
	        TestResultEntity result = new TestResultEntity();
	        result.setInput("");
	        result.setExpectedOutput("");
	        result.setSubmission(submission);
	        result.setQuestionId(submission.getQuestionId());

	        try {
	            String output = compileAndRunCode(submission.getScript(), "", submission.getLanguage());
	            result.setActualOutput(output);
	            result.setPassed(null);
	        } catch (Exception e) {
	            result.setActualOutput("Runtime Error: " + e.getMessage());
	            result.setPassed(null);
	        }
	        return result;
	    }

	    // ... rest of existing methods ...


	
  // prevoius code
//	@Override
//	public void validateSubmission(CodeSubmission submissionRequest) {
//		// Validates presence of script, language, and test cases
//		if (submissionRequest == null || submissionRequest.getScript() == null
//				|| submissionRequest.getLanguage() == null) {
//			throw new CompilerException("Invalid code request: missing required fields.");
//		}
//
//		if (submissionRequest.getTestResults() == null || submissionRequest.getTestResults().isEmpty()) {
//			throw new CompilerException("No test cases provided.");
//		}
//	}
	
	
	// latest code for no testcases provided
	@Override
	public void validateSubmission(CodeSubmission submissionRequest) {
	    if (submissionRequest == null || submissionRequest.getScript() == null
	            || submissionRequest.getLanguage() == null) {
	        throw new CompilerException("Invalid code request: missing required fields.");
	    }

	    // Remove this strict check to allow free-run mode
	    // if (submissionRequest.getTestResults() == null || submissionRequest.getTestResults().isEmpty()) {
	    //     throw new CompilerException("No test cases provided.");
	    // }
	}

	@Override
	public String compileAndRunCode(String code, String input, String language) {
		logger.info("Compiling and running code in language: {}", language);
		try {
			Path tempDir = Files.createTempDirectory("compiler"); // Creates a temporary directory for compiling

			switch (language.toLowerCase()) {
			case "java":
				return compileAndRunJava(code, input, tempDir); // Java handler
			case "python":
			case "python3":
				return runPythonScript(code, input, tempDir); // Python handler
			case "c":
				return compileAndRunC(code, input, tempDir); // C handler
			case "cpp":
			case "c++":
				return compileAndRunCpp(code, input, tempDir); // C++ handler
			case "js":
			case "javascript":
				return runScript(code, input, tempDir); // JavaScript handler
			default:
				throw new CompilerException("Unsupported language: " + language);
			}
		} catch (IOException e) {
			logger.error("I/O error during compilation/execution: {}", e.getMessage(), e);
			throw new CompilerException("I/O Error: " + e.getMessage());
		} catch (Exception e) {
			logger.error("Unexpected error during execution: {}", e.getMessage(), e);
			throw new CompilerException("Runtime Error: " + e.getMessage());
		}
	}

	// Java compilation and execution
	private String compileAndRunJava(String code, String input, Path tempDir) throws Exception {
	    String className = extractClassName(code);
	    if (className == null)
	        throw new CompilerException("Could not find public class in Java code.");

	    Path sourceFile = tempDir.resolve(className + ".java");
	    Files.write(sourceFile, code.getBytes());

	    // Compile command
	    String[] compileCommand = { "javac", sourceFile.toString() };

	    try {
	        runProcess(compileCommand, null); // Will throw if compilation fails
	    } catch (CompilerException ce) {
	        throw new CompilerException(ce.getMessage()); // Propagate the error trace
	    }

	    // If compilation succeeds
	    String[] runCommand = { "java", "-cp", tempDir.toString(), className };
	    return runProcess(runCommand, input);
	}


	// Executes Python code
	private String runPythonScript(String code, String input, Path tempDir) throws Exception {
		Path scriptFile = tempDir.resolve("script.py");
		Files.write(scriptFile, code.getBytes());

		String[] command = { "python", scriptFile.toString() };
		return runProcess(command, input);
	}

	// Executes JavaScript code using Node
	private String runScript(String code, String input, Path tempDir) throws Exception {
		Path scriptFile = tempDir.resolve("script.js");
		Files.write(scriptFile, code.getBytes());

		String[] command = { "node", scriptFile.toString() };
		return runProcess(command, input);
	}

	// C compilation and execution
	private String compileAndRunC(String code, String input, Path tempDir) throws Exception {
		Path sourceFile = tempDir.resolve("main.c");
		Files.write(sourceFile, code.getBytes());

		Path outputFile = tempDir.resolve("main_exec");
		String[] compileCommand = { "gcc", sourceFile.toString(), "-o", outputFile.toString() };
		runProcess(compileCommand, null);

		String[] runCommand = { outputFile.toString() };
		return runProcess(runCommand, input);
	}

	// C++ compilation and execution
	private String compileAndRunCpp(String code, String input, Path tempDir) throws Exception {
		Path sourceFile = tempDir.resolve("main.cpp");
		Files.write(sourceFile, code.getBytes());

		Path outputFile = tempDir.resolve("main_exec");
		String[] compileCommand = { "g++", sourceFile.toString(), "-o", outputFile.toString() };
		runProcess(compileCommand, null);

		String[] runCommand = { outputFile.toString() };
		return runProcess(runCommand, input);
	}

	// Executes the given command and passes input to it if needed
	private String runProcess(String[] command, String input) throws CompilerException {
	    try {
	        ProcessBuilder builder = new ProcessBuilder(command);
	        builder.redirectErrorStream(true);
	        Process process = builder.start();

	        // Send input if needed
	        if (input != null && !input.isEmpty()) {
	            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
	                writer.write(input);
	                writer.flush();
	            }
	        }

	        // Read output (including stderr)
	        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
	        StringBuilder output = new StringBuilder();
	        String line;

	        while ((line = reader.readLine()) != null) {
	            output.append(line).append("\n");
	        }

	        int exitCode = process.waitFor();
	        if (exitCode != 0) {
	            throw new CompilerException(output.toString()); // ❗ Capture error
	        }

	        return output.toString().trim();
	    } catch (IOException | InterruptedException e) {
	        throw new CompilerException("Process execution failed: " + e.getMessage());
	    }
	}


	// Utility to return empty string if null
	private String defaultIfNull(String value) {
		return value != null ? value.trim() : "";
	}

	@Override
	public String extractClassName(String code) {
		// Finds the public class name in Java source code
		for (String line : code.split("\\n")) {
			line = line.trim();
			if (line.startsWith("public class ")) {
				String[] tokens = line.split("\\s+");
				if (tokens.length >= 3) {
					return tokens[2].split("\\{")[0].trim(); // Extracts class name
				}
			}
		}
		return null;
	}

	// Executes code with a single custom input and no expected output
	public TestResultEntity executeSingleInput(CodeSubmission submission, String customInput) {
		TestResultEntity result = new TestResultEntity(); // Prepare result
		result.setInput(customInput);
		  result.setQuestionId(submission.getQuestionId()); 

		try {
			// Run the code
			String actualOutput = compileAndRunCode(submission.getScript(), customInput, submission.getLanguage());

			result.setActualOutput(actualOutput);
//			result.setPassed(false); // No expected output to compare
			result.setSubmission(submission);
		} catch (Exception e) {
			logger.error("Execution failed for custom input: {}", e.getMessage(), e);
			result.setActualOutput("Runtime Error: " + e.getMessage());
//			result.setPassed(false);
			result.setSubmission(submission);
		}

		return result;
	}

	public List<CodeSubmission> getSubmissionsByUserAndLanguage(String userEmail, String language) {
		// Validate inputs
		if (userEmail == null || userEmail.trim().isEmpty()) {
			throw new IllegalArgumentException("User email must not be null or empty.");
		}
		if (language == null || language.trim().isEmpty()) {
			throw new IllegalArgumentException("Language must not be null or empty.");
		}

		// Log the request for traceability
		logger.info("Fetching code submissions for userEmail: {} and language: {}", userEmail, language);

		List<CodeSubmission> submissions = submissionRepo.findByUserEmailAndLanguage(userEmail.trim(), language.trim());

		// Optional: log number of records found
		logger.info("Found {} submissions for userEmail: {} and language: {}", submissions.size(), userEmail, language);

		return submissions;
	}
	
	public List<CodeSubmission> getSubmissionsByUserEmail(String userEmail) {
		// Validate inputs
		if (userEmail == null || userEmail.trim().isEmpty()) {
			throw new IllegalArgumentException("User email must not be null or empty.");
		}
		

		// Log the request for traceability
		logger.info("Fetching code submissions for userEmail: {} and language: {}", userEmail);

		List<CodeSubmission> submissions = submissionRepo.findByUserEmail(userEmail.trim());

		// Optional: log number of records found
		logger.info("Found {} submissions for userEmail: {} and language: {}", submissions.size(), userEmail);

		return submissions;
	}
	
	public List<CodeSubmission> getSubmissionsByUserAndQuestion(String userEmail, String questionId) {
	    if (userEmail == null || userEmail.trim().isEmpty() || questionId == null || questionId.trim().isEmpty()) {
	        throw new IllegalArgumentException("User email and question ID must not be null or empty.");
	    }

	    logger.info("Fetching submissions for user: {} and question: {}", userEmail, questionId);
	    List<CodeSubmission> submissions = submissionRepo.findByUserEmailAndQuestionId(userEmail.trim(), questionId.trim());
	    logger.info("Found {} submissions", submissions.size());
	    
	    return submissions;
	}
	
	
	
	
	public List<CodeSubmissionResponseDTO> getSubmissionsByPassStatus(String userEmail, Boolean passed) {
	    List<CodeSubmission> submissions = submissionRepo.findByUserEmail(userEmail);
	    
	    return submissions.stream()
	        .filter(sub -> sub.getTestResults() != null)
	        .map(sub -> {
	            CodeSubmissionResponseDTO dto = new CodeSubmissionResponseDTO();
	            dto.setLanguage(sub.getLanguage());
	            dto.setScript(sub.getScript());
	            dto.setUserEmail(sub.getUserEmail());
	            dto.setQuestionId(sub.getQuestionId());
	            dto.setCreatedAt(sub.getCreatedAt());
	            
	            List<TestCaseDTO> testCaseDTOs = sub.getTestResults().stream()
	                .filter(tr -> passed == null || tr.getPassed() == passed)
	                .map(tr -> {
	                    TestCaseDTO tc = new TestCaseDTO();
	                    tc.setInput(tr.getInput());
	                    tc.setExpectedOutput(tr.getExpectedOutput());
	                    tc.setActualOutput(tr.getActualOutput());
	                    tc.setPassed(tr.getPassed());
	                    tc.setQuestionId(tr.getQuestionId());

	                    // ✅ Here: Add errorInfo if runtime error
	                    if (tr.getActualOutput() != null && tr.getActualOutput().startsWith("Runtime Error")) {
	                        CodeErrorInfo error = parseErrorInfo(tr.getActualOutput(), sub.getLanguage());
	                        tc.setErrorInfo(error);
	                    }

	                    return tc;
	                })
	                .collect(Collectors.toList());
	            
	            dto.setTestResults(testCaseDTOs);
	            return dto;
	        })
	        .filter(dto -> !dto.getTestResults().isEmpty())
	        .collect(Collectors.toList());
	}

	   
	
	public CodeErrorInfo parseErrorInfo(String rawError, String language) {
	    CodeErrorInfo error = new CodeErrorInfo();
	    error.setFullTrace(rawError);

	    switch (language.toLowerCase()) {
	        case "java":
	            error.setType("CompilationError");
	            error.setMessage(extractJavaErrorMessage(rawError));
	            error.setLine(extractJavaLineNumber(rawError));
	            break;

	        case "python":
	        case "python3":
	            error.setType("SyntaxError");
	            error.setMessage(extractPythonErrorMessage(rawError));
	            error.setLine(extractPythonLineNumber(rawError));
	            break;

	        case "c":
	        case "cpp":
	        case "c++":
	            error.setType("CompilationError");
	            error.setMessage(extractGccErrorMessage(rawError));
	            error.setLine(extractGccLineNumber(rawError));
	            break;

	        case "javascript":
	        case "js":
	            error.setType("SyntaxError");
	            error.setMessage(extractJsErrorMessage(rawError));
	            error.setLine(extractJsLineNumber(rawError));
	            break;

	        default:
	            error.setType("Unknown");
	            error.setMessage("Could not parse error");
	            error.setLine(null);
	    }

	    return error;
	}

	
	private String extractJavaErrorMessage(String error) {
	    // Extract message from:
	    // error: ';' expected
	    Pattern pattern = Pattern.compile("error: (.+)");
	    Matcher matcher = pattern.matcher(error);
	    return matcher.find() ? matcher.group(1).trim() : "Compilation error";
	}

	private Integer extractJavaLineNumber(String error) {
	    // Match line number from javac output like:
	    // Main.java:5: error: ';' expected
	    Pattern pattern = Pattern.compile("\\b(?:\\w+\\.java):(\\d+):\\s+error:");
	    Matcher matcher = pattern.matcher(error);
	    return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
	}


	private Integer extractPythonLineNumber(String error) {
	    Pattern pattern = Pattern.compile("File \".*\", line (\\d+)");
	    Matcher matcher = pattern.matcher(error);
	    return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
	}

	private String extractPythonErrorMessage(String error) {
	    String[] lines = error.split("\\n");
	    for (String line : lines) {
	        if (line.trim().startsWith("SyntaxError") || line.trim().startsWith("IndentationError")) {
	            return line.trim();
	        }
	    }
	    return "Python error";
	}
	
	private Integer extractGccLineNumber(String error) {
	    Pattern pattern = Pattern.compile(".*?:(\\d+):\\d+: error:");
	    Matcher matcher = pattern.matcher(error);
	    return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
	}

	private String extractGccErrorMessage(String error) {
	    Pattern pattern = Pattern.compile("error: (.+?)(\\r?\\n|$)");
	    Matcher matcher = pattern.matcher(error);
	    return matcher.find() ? matcher.group(1).trim() : "Compilation error in C/C++";
	}
	
	private Integer extractJsLineNumber(String error) {
	    // Example: at Object.<anonymous> (script.js:3:10)
	    Pattern pattern = Pattern.compile("\\(.*:(\\d+):(\\d+)\\)");
	    Matcher matcher = pattern.matcher(error);
	    return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
	}

	private String extractJsErrorMessage(String error) {
	    String[] lines = error.split("\\n");
	    for (String line : lines) {
	        if (line.contains("SyntaxError") || line.contains("ReferenceError") || line.contains("TypeError")) {
	            return line.trim();
	        }
	    }
	    return "JavaScript error";
	}


	public List<CodeSubmission> getLatestSubmissionsByUserEmailAndJobPrefix(String userEmail, String jobPrefix) {
	    return submissionRepo.findLatestSubmissionsByUserEmailAndJobPrefix(userEmail, jobPrefix);
	}

	
	public Optional<CodeSubmission> getLatestSubmissionByUserEmailJobPrefixAndQuestionId(
	        String userEmail, String jobPrefix, String questionId) {

	    return submissionRepo
	            .findTopByUserEmailAndJobPrefixAndQuestionIdOrderByIdDesc(userEmail, jobPrefix, questionId);
	}

	
}
