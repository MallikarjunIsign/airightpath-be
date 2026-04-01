package com.rightpath.exceptions;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Global exception handler for the application that centralizes error handling.
 * 
 * <p>
 * This class handles various exceptions and returns appropriate HTTP responses
 * with consistent error structures. It provides:
 * <ul>
 * <li>Standardized error responses</li>
 * <li>Proper HTTP status codes</li>
 * <li>Detailed error messages</li>
 * <li>Logging of exceptions</li>
 * </ul>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/**
	 * Handles validation errors from method arguments.
	 * 
	 * @param ex The validation exception
	 * @return ResponseEntity containing field errors with status 400 (BAD_REQUEST)
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
		Map<String, String> errors = new HashMap<>();
		ex.getBindingResult().getAllErrors().forEach(error -> {
			String fieldName = ((FieldError) error).getField();
			String errorMessage = error.getDefaultMessage();
			errors.put(fieldName, errorMessage);
		});
		logger.warn("Validation errors: {}", errors);
		return ResponseEntity.badRequest().body(errors);
	}

	/**
	 * Handles authentication failures due to bad credentials.
	 * 
	 * @param ex The authentication exception
	 * @return ResponseEntity with status 401 (UNAUTHORIZED) and error message
	 */
	@ExceptionHandler(BadCredentialsException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ResponseEntity<ErrorResponse> handleBadCredentialsException(BadCredentialsException ex) {
		logger.warn("Authentication failed: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Invalid username or password"));
	}

	/**
	 * Handles cases when a user is not found in the database.
	 * 
	 * @param ex The user not found exception
	 * @return ResponseEntity with status 404 (NOT_FOUND) and error message
	 */
	@ExceptionHandler(UserNotFoundDbException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ResponseEntity<ErrorResponse> handleUserNotFoundException(UserNotFoundDbException ex) {
		logger.warn("User not found: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
	}

	/**
	 * Handles cases when a user already exists in the database.
	 * 
	 * @param ex The duplicate user exception
	 * @return ResponseEntity with status 208 (ALREADY_REPORTED) and error message
	 */
	@ExceptionHandler(UserAlreadyInDatabaseException.class)
	@ResponseStatus(HttpStatus.ALREADY_REPORTED)
	public ResponseEntity<ErrorResponse> handleUserAlreadyExistsException(UserAlreadyInDatabaseException ex) {
		logger.warn("Duplicate user registration attempt: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.ALREADY_REPORTED).body(new ErrorResponse(ex.getMessage()));
	}

	/**
	 * Handles password policy violations.
	 * 
	 * @param ex The password exception
	 * @return ResponseEntity with status 400 (BAD_REQUEST) and error message
	 */
	@ExceptionHandler(PasswordSizeException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ResponseEntity<ErrorResponse> handlePasswordSizeException(PasswordSizeException ex) {
		logger.warn("Password policy violation: {}", ex.getMessage());
		return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
	}

	/**
	 * Handles attempts to use inactive accounts.
	 * 
	 * @param ex The inactive user exception
	 * @return ResponseEntity with status 401 (UNAUTHORIZED) and error message
	 */
	@ExceptionHandler(InactiveUserException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ResponseEntity<ErrorResponse> handleInactiveUserException(InactiveUserException ex) {
		logger.warn("Inactive user access attempt: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(ex.getMessage()));
	}

	@ExceptionHandler(StorageException.class)
	public ResponseEntity<String> handleStorageError(StorageException ex) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
	}

	/**
	 * Standard error response structure.
	 */
	private static class ErrorResponse {
		private final String message;
		private final long timestamp;

		public ErrorResponse(String message) {
			this.message = message;
			this.timestamp = System.currentTimeMillis();
		}

		// Getters
		public String getMessage() {
			return message;
		}

		public long getTimestamp() {
			return timestamp;
		}
	}
}