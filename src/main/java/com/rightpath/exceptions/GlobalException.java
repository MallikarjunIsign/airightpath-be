package com.rightpath.exceptions;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import io.jsonwebtoken.MalformedJwtException;

@ControllerAdvice
public class GlobalException {

	/**
	 * Handles exceptions of type UserNotFoundDbException.
	 * 
	 * @param ex The exception instance.
	 * @return A ResponseEntity with an error message and HTTP status NOT_FOUND.
	 */
	@ExceptionHandler(UserNotFoundDbException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ResponseEntity<String> handleUsernameNotFoundException(UserNotFoundDbException ex) {
		return new ResponseEntity<>(ex.getMessage() + " Bad credentials", HttpStatus.NOT_FOUND);
	}
	


	/**
	 * Handles exceptions of type BadCredentialsException.
	 * 
	 * @param ex The exception instance.
	 * @return A ResponseEntity with an error message and HTTP status UNAUTHORIZED.
	 */
	@ExceptionHandler(BadCredentialsException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ResponseEntity<String> handleBadCredentialsException(BadCredentialsException ex) {
		return new ResponseEntity<>("Password is not correct " + ex.getMessage(), HttpStatus.UNAUTHORIZED);
	}

	/**
	 * Handles exceptions of type UserAlreadyInDatabaseException.
	 * 
	 * @param ex The exception instance.
	 * @return A ResponseEntity with an error message and HTTP status
	 *         ALREADY_REPORTED.
	 */
	@ExceptionHandler(UserAlreadyInDatabaseException.class)
	@ResponseStatus(HttpStatus.ALREADY_REPORTED)
	public ResponseEntity<String> handleUserAlreadyInDatabaseException(UserAlreadyInDatabaseException ex) {
		return new ResponseEntity<>(ex.getMessage() + " Bad credentials", HttpStatus.ALREADY_REPORTED);
	}

	/**
	 * Handles exceptions of type MethodArgumentNotValidException.
	 * 
	 * This exception is thrown when a validation error occurs in method arguments.
	 * The response contains a map of field names and their respective error
	 * messages.
	 * 
	 * @param ex The exception instance.
	 * @return A ResponseEntity with a map of validation errors and HTTP status
	 *         BAD_REQUEST.
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
		return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
	}

	/**
	 * Handles exceptions of type PasswordSizeException.
	 * 
	 * @param ex The exception instance.
	 * @return A ResponseEntity with an error message and HTTP status BAD_REQUEST.
	 */
	@ExceptionHandler(PasswordSizeException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ResponseEntity<String> handlePasswordSizeException(PasswordSizeException ex) {
		return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
	}

	/**
	 * Handles exceptions of type InactiveUserException.
	 * 
	 * @param ex The exception instance.
	 * @return A ResponseEntity with an error message and HTTP status UNAUTHORIZED.
	 */
	@ExceptionHandler(InactiveUserException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ResponseEntity<String> handleInactiveUserException(InactiveUserException ex) {
		return new ResponseEntity<>(ex.getMessage(), HttpStatus.UNAUTHORIZED);
	}

}
