package com.rightpath.exceptions;

/**
 * Custom runtime exception for compiler-related errors in the application.
 * 
 * <p>This exception should be thrown when:
 * <ul>
 *   <li>Code compilation fails</li>
 *   <li>Execution timeout occurs</li>
 *   <li>Unsupported language features are encountered</li>
 *   <li>Other compiler-specific errors occur</li>
 * </ul>
 * 
 * <p>This is an unchecked exception (extends {@link RuntimeException}) as these
 * are typically unrecoverable errors that should bubble up to be handled by
 * the global exception handler.
 */
public class CompilerException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new compiler exception with the specified detail message.
     * 
     * @param message the detail message (which should include specific error details
     *                about the compilation failure)
     */
    public CompilerException(String message) {
        super(message);
    }

    /**
     * Constructs a new compiler exception with the specified detail message and cause.
     * 
     * @param message the detail message
     * @param cause the root cause of the exception (typically the original exception
     *              that occurred during compilation)
     */
    public CompilerException(String message, Throwable cause) {
        super(message, cause);
    }
}