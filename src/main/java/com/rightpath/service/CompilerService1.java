package com.rightpath.service;

import java.util.List;

import com.rightpath.dto.CodeErrorInfo;
import com.rightpath.entity.CodeSubmission;
import com.rightpath.entity.TestResultEntity;

/**
 * Service interface for handling code compilation, execution, and validation.
 * This includes methods to compile code in different languages, run test cases,
 * extract class names, and parse compilation/runtime errors.
 */
public interface CompilerService1 {

    /**
     * Executes the submitted code against a predefined set of test cases.
     *
     * @param submissionRequest The code submission request containing source code, language, and input/output expectations.
     * @return A list of test results including output, pass/fail status, and error messages if any.
     *
     * Log Suggestions:
     * - INFO: "Executing submitted code for user: {userId or identifier}"
     * - DEBUG: "Submission details: {submissionRequest}"
     * - INFO: "Test execution completed with {number of passed}/{total} cases"
     */
    List<TestResultEntity> executeCode(CodeSubmission submissionRequest);

    /**
     * Compiles and runs the given source code in the specified language with the provided input.
     *
     * @param sourceCode The code to compile and execute.
     * @param input      Input data to pass to the program during execution.
     * @param language   The programming language of the code (e.g., Java, Python, C++).
     * @return The output of the executed program or any error messages encountered.
     *
     * Log Suggestions:
     * - INFO: "Compiling and running code in language: {language}"
     * - DEBUG: "Source code snippet: {first 100 chars}"
     * - INFO: "Execution completed with output length: {output.length()}"
     */
    String compileAndRunCode(String sourceCode, String input, String language);

    /**
     * Extracts the main class name from the provided code. Useful for Java submissions where
     * class names must match filenames.
     *
     * @param code The Java source code.
     * @return The extracted class name.
     *
     * Log Suggestions:
     * - DEBUG: "Extracting class name from code"
     * - INFO: "Extracted class name: {className}"
     */
    String extractClassName(String code);

    /**
     * Validates the code submission by checking required fields like language, code content,
     * and possibly security constraints.
     *
     * @param submissionRequest The code submission to validate.
     *
     * Log Suggestions:
     * - INFO: "Validating submission for language: {language}"
     * - WARN: "Validation failed: {error details}" (if any issues)
     */
    void validateSubmission(CodeSubmission submissionRequest);

    /**
     * Parses raw error messages generated during code compilation or execution and converts
     * them into a structured format for UI feedback.
     *
     * @param rawError The raw error message returned by the compiler or runtime.
     * @param language The programming language used.
     * @return A structured object containing error line, message, and code snippet.
     *
     * Log Suggestions:
     * - DEBUG: "Parsing error for language: {language}"
     * - INFO: "Error parsed into CodeErrorInfo object"
     */
    CodeErrorInfo parseErrorInfo(String rawError, String language);

}
