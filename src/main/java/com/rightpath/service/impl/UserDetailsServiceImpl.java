// Package declaration
package com.rightpath.service.impl;

// Import necessary Java and Spring packages
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory; // For logging
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy; // Lazy loads beans
import org.springframework.security.authentication.AuthenticationManager; // Used for authenticating users
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; // Used for passing authentication data
import org.springframework.security.core.Authentication; // Represents authentication token
import org.springframework.security.core.GrantedAuthority; // Represents roles/authorities granted to the user
import org.springframework.security.core.authority.SimpleGrantedAuthority; // Simple implementation of GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails; // Core interface for user information
import org.springframework.security.core.userdetails.UserDetailsService; // Loads user-specific data
import org.springframework.security.core.userdetails.UsernameNotFoundException; // Thrown if user is not found
import org.springframework.security.crypto.password.PasswordEncoder; // Interface for encoding passwords
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile; // Represents uploaded file

// Import custom project classes
import com.rightpath.dto.Login;
import com.rightpath.dto.UsersDto;
import com.rightpath.entity.Users;
import com.rightpath.exceptions.CustomException;
import com.rightpath.exceptions.InactiveUserException;
import com.rightpath.exceptions.UserAlreadyInDatabaseException;
import com.rightpath.exceptions.StorageException;
import com.rightpath.exceptions.UserNotFoundDbException;
import com.rightpath.repository.RoleRepository;
import com.rightpath.repository.UserRoleRepository;
import com.rightpath.repository.UsersRepository;
import com.rightpath.service.EmailService;
import com.rightpath.service.rbac.RbacAuthorityService;
import com.rightpath.util.ThreadLocalUserContext;
import com.rightpath.validator.PasswordValidator;

// Mark the class as a service layer bean
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

	// Logger for this class
	private static final Logger logger = LoggerFactory.getLogger(UserDetailsServiceImpl.class);

	// Inject PasswordValidator to check password strength/size
	@Autowired
	private PasswordValidator passwordValidator;

	// Inject repository for DB operations on Users
	@Autowired
	private UsersRepository userRepository;

	// Inject password encoder for hashing passwords
	@Autowired
	private PasswordEncoder bCryptPasswordEncoder;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private UserRoleRepository userRoleRepository;

	// RBAC authority resolver (roles + permissions)
	@Autowired
	private RbacAuthorityService rbacAuthorityService;

	// AuthenticationManager is lazily injected to avoid circular dependency
	@Lazy
	@Autowired
	private AuthenticationManager authenticationManager;

	// Service to generate JWT tokens
	@Autowired
	private JwtService jwtService;

	// Email service for sending notifications
	@Autowired
	private EmailService emailService;

	// Method to load a user by their email address (used by Spring Security)
	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		logger.info("Loading user by email: {}", email);

		// Fetch user by email or throw custom exception if not found
		Users user = userRepository.findByEmail(email).orElseThrow(() -> {
			logger.error("User not found in database for email: {}", email);
			return new UserNotFoundDbException("User does not exist");
		});

		// Ensure the user account is active
		checkActiveUser(user.getEnabled());

		// Set user in thread-local context for downstream usage
		ThreadLocalUserContext.setUser(user);

		// Convert authorities into Spring Security format.
		// Prefer RBAC-derived authorities (ROLE_* + permissions) if present.
		Set<String> authorityStrings = rbacAuthorityService.resolveAuthorities(user.getEmail());
		// If user has no RBAC roles yet, treat as having no authorities.
		// (We assign USER by default at registration; this is just defensive.)
		if (authorityStrings == null) {
			authorityStrings = java.util.Collections.emptySet();
		}
		Set<GrantedAuthority> grantedAuthorities = authorityStrings.stream()
				.filter(a -> a != null && !a.isBlank())
				.map(a -> new SimpleGrantedAuthority(a.startsWith("ROLE_") ? a.toUpperCase() : a))
				.collect(Collectors.toSet());

		logger.debug("User loaded successfully: {}", email);

		// Return Spring Security User object
		return new org.springframework.security.core.userdetails.User(user.getEmail(), user.getPassword(),
				grantedAuthorities);
	}

	// Add a new user to the system
	public Map<String, String> addUser(UsersDto usersDto) {
	    logger.info("Attempting to add new user: {}", usersDto.getEmail());

	    // ✅ 1. Check if email already exists
	    if (userRepository.existsByEmail(usersDto.getEmail())) {
	        throw new CustomException("Email already exists");
	    }

	    // ✅ 2. Check if mobile number already exists
	    if (userRepository.existsByMobileNumber(usersDto.getMobileNumber())) {
	        throw new CustomException("Mobile number already exists");
	    }

	    // ✅ 3. Validate password strength
	    passwordValidator.passwordSizeChecker(usersDto.getPassword());

	    // ✅ 4. Encode the password
	    String encodedPassword = bCryptPasswordEncoder.encode(usersDto.getPassword());

	    // ✅ 5. Create and save user (RBAC roles are stored in user_roles)
	    Users user = new Users(
	        usersDto.getEmail(),
	        usersDto.getFirstName(),
	        usersDto.getLastName(),
	        encodedPassword,
	        usersDto.getEnabled(),
	        usersDto.getMobileNumber()
	    );

	    userRepository.save(user);

	    // ✅ 6. Assign default role USER (active)
	    // Be resilient in fresh DBs/environments: if the USER role isn't seeded yet, create it.
	    try {
	        var role = roleRepository.findByName(com.rightpath.rbac.RoleName.USER)
	                .orElseGet(() -> {
	                    logger.warn("Default role USER not found; creating it on the fly");
	                    var r = new com.rightpath.entity.Role();
	                    r.setName(com.rightpath.rbac.RoleName.USER);
	                    return roleRepository.save(r);
	                });

	        // Avoid duplicate links if the method is ever retried.
	        var existing = userRoleRepository
	                .findByUser_EmailAndRole_NameAndActiveTrue(user.getEmail(), com.rightpath.rbac.RoleName.USER)
	                .orElse(null);
	        if (existing == null) {
	            var userRole = new com.rightpath.entity.UserRole();
	            userRole.setUser(user);
	            userRole.setRole(role);
	            userRole.setActive(true);
	            userRoleRepository.save(userRole);
	        }
	    } catch (Exception e) {
	        logger.error("Failed to assign default USER role for {}", usersDto.getEmail(), e);
	        throw e;
	    }

	    // ✅ 7. Send email
	    emailService.sendSuccessRegistrationEmail(user.getEmail(), user.getLastName(), user.getFirstName(),user.getMobileNumber());

	    // ✅ 8. Return success
	    return Map.of("message", "success");
	}

	// Update user details and profile image
	public Map<String, String> updateUser(String email, String firstName, String lastName, String mobileNumber,
			String alternativeMobileNumber, MultipartFile profileImage) {
		logger.info("Updating user: {}", email);

		// Fetch the user from DB
		Users user = userRepository.findByEmail(email)
				.orElseThrow(() -> {
					logger.error("User not found for update: {}", email);
					return new UserNotFoundDbException("User not found with email: " + email);
				});

		// Update fields if new values are provided
		if (firstName != null && !firstName.isEmpty())
			user.setFirstName(firstName);
		if (lastName != null && !lastName.isEmpty())
			user.setLastName(lastName);
		if (mobileNumber != null && !mobileNumber.isEmpty())
			user.setMobileNumber(mobileNumber);
		if (alternativeMobileNumber != null && !alternativeMobileNumber.isEmpty())
			user.setAlternativeMobileNumber(alternativeMobileNumber);

		// Update profile image if provided
		if (profileImage != null && !profileImage.isEmpty()) {
			try {
				user.setProfileImage(profileImage.getBytes());
			} catch (IOException e) {
				logger.error("Failed to process profile image for user: {}", email, e);
				throw new StorageException("Error processing profile image");
			}
		}

		// Save updated user to DB
		userRepository.save(user);
		logger.info("User updated successfully: {}", email);

		// Prepare and return response
		Map<String, String> response = new HashMap<>();
		response.put("message", "User updated successfully");

		if (user.getProfileImage() != null) {
			String base64Image = Base64.getEncoder().encodeToString(user.getProfileImage());
			response.put("profileImage", base64Image);
		}

		response.put("firstName", user.getFirstName());
		response.put("lastName", user.getLastName());
		response.put("mobileNumber", user.getMobileNumber());
		response.put("alternativeMobileNumber", user.getAlternativeMobileNumber());

		return response;
	}

	// Authenticate a user and return JWT token + user info
	public Map<String, String> getLoginDetails(Login loginUser) {
		logger.info("Authenticating user: {}", loginUser.getEmail());

		// Validate password length
		passwordValidator.passwordSizeChecker(loginUser.getPassword());

		// Authenticate using Spring Security AuthenticationManager
		Authentication authenticate = authenticationManager
				.authenticate(new UsernamePasswordAuthenticationToken(loginUser.getEmail(), loginUser.getPassword()));

		if (!authenticate.isAuthenticated()) {
			logger.error("Authentication failed for: {}", loginUser.getEmail());
			throw new UsernameNotFoundException("User Name Not Found");
		}

		// Generate JWT token
		UserDetails userDetails = (UserDetails) authenticate.getPrincipal();
		String token = jwtService.generateToken(userDetails);

		// Add token to user map
		Map<String, String> userMap = ThreadLocalUserContext.getUser();
		userMap.put("jwtToken", token);

		logger.info("Authentication successful for: {}", loginUser.getEmail());

		return userMap;
	}

	// Validate if user already exists by email
	public void validateUser(String email) {
		if (userRepository.findByEmail(email).isPresent()) {
			logger.warn("Attempted to register an already existing user: {}", email);
			throw new UserAlreadyInDatabaseException("UserAlready in Database ");
		}
	}

	// Check if user is active
	public void checkActiveUser(Boolean activeUser) {
		if (!activeUser) {
			logger.warn("Inactive user tried to login");
			throw new InactiveUserException("User is Inactive");
		}
	}

	// Mark user as active
	public Map<String, String> updateActive(String email) {
		try {
			logger.info("Activating user: {}", email);
			userRepository.activeByEmail(email);
			Map<String, String> map = new HashMap<>();
			map.put("success", "success");
			return map;
		} catch (UserNotFoundDbException e) {
			logger.error("Failed to activate user: {}", email);
			throw new UserNotFoundDbException("User Not Found");
		}
	}

	// Deactivate or delete user
	public Map<String, String> updateDeactive(String email) {
		try {
			logger.info("Deactivating user: {}", email);
			userRepository.deleteByEmail(email);
			Map<String, String> map = new HashMap<>();
			map.put("success", "success");
			return map;
		} catch (UserNotFoundDbException e) {
			logger.error("Failed to deactivate user: {}", email);
			throw new UserNotFoundDbException("User Not Found");
		}
	}

	// Fetch users who are not admins
	public List<UsersDto> getNonAdminUsers() {
		logger.debug("Fetching non-admin users");
		List<Users> nonAdminUsers = userRepository.findAllNonAdminUsers(com.rightpath.rbac.RoleName.ADMIN);
		return nonAdminUsers.stream().map(UsersDto::new).collect(Collectors.toList());
	}

	// Check if email is already registered
	public boolean isEmailRegistered(String email) {
		boolean exists = userRepository.existsByEmail(email);
		logger.debug("Checking if email is registered ({}): {}", email, exists);
		return exists;
	}

	// Get profile image as byte array
	public byte[] getProfileImage(String email) {
	    Users user = userRepository.findByEmail(email)
	            .orElseThrow(() -> new UserNotFoundDbException("User not found with email: " + email));

	    return user.getProfileImage(); // may be null
	}

	// Fetch user by email
	public Users getUserByEmail(String email) {
		logger.debug("Fetching user by email: {}", email);
		return userRepository.findByEmail(email).orElseThrow(() -> {
			logger.error("User not found for email: {}", email);
			return new RuntimeException("User not found with email: " + email);
		});
	}

	// Allow user to change their password
	public String changePassword(String email, String oldPassword, String newPassword, String confirmPassword) {
		logger.info("Password change requested for: {}", email);
		Optional<Users> optionalUser = userRepository.findByEmail(email);
		if (optionalUser.isEmpty()) {
			logger.error("User not found: {}", email);
			throw new IllegalArgumentException("User not found");
		}

		Users user = optionalUser.get();

		if (!newPassword.equals(confirmPassword)) {
			logger.warn("New password and confirm password do not match for user: {}", email);
			throw new IllegalArgumentException("New password and confirm password do not match");
		}

		if (!bCryptPasswordEncoder.matches(oldPassword, user.getPassword())) {
			logger.warn("Old password mismatch for user: {}", email);
			throw new IllegalArgumentException("Old password does not match");
		}

		passwordValidator.passwordSizeChecker(newPassword);
		user.setPassword(bCryptPasswordEncoder.encode(newPassword));
		userRepository.save(user);

		logger.info("Password changed successfully for: {}", email);
		return "Password updated successfully";
	}
}
