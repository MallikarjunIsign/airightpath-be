# Agent changes log

This file tracks notable changes made by the coding agent to this repository.

## 2026-01-02 — V2 auth (JWT access + rotating refresh tokens) + build/Lombok triage

**Reason**

- Implemented industry-standard token handling for API V2 without disturbing existing V1 auth flows.
- Added rotating, hashed refresh tokens and strict JWT validation.
- Began compilation triage; minor DTO cleanup and Lombok build wiring were added while investigating build failures.

**Files added**

- `src/main/java/com/rightpath/api/v2/dto/ApiResponse.java`
- `src/main/java/com/rightpath/api/v2/error/ApiError.java`
- `src/main/java/com/rightpath/api/v2/error/V2ErrorCodes.java`
- `src/main/java/com/rightpath/api/v2/error/V2ExceptionHandler.java`
- `src/main/java/com/rightpath/api/v2/auth/config/V2AuthConfig.java`
- `src/main/java/com/rightpath/api/v2/auth/config/V2AuthProperties.java`
- `src/main/java/com/rightpath/api/v2/auth/controller/AuthV2Controller.java`
- `src/main/java/com/rightpath/api/v2/auth/dto/V2LoginRequest.java`
- `src/main/java/com/rightpath/api/v2/auth/dto/AccessTokenResponse.java`
- `src/main/java/com/rightpath/api/v2/auth/dto/MessageResponse.java`
- `src/main/java/com/rightpath/api/v2/auth/entity/RefreshTokenV2.java`
- `src/main/java/com/rightpath/api/v2/auth/exception/InvalidAccessTokenException.java`
- `src/main/java/com/rightpath/api/v2/auth/exception/InvalidRefreshTokenException.java`
- `src/main/java/com/rightpath/api/v2/auth/exception/RefreshTokenExpiredException.java`
- `src/main/java/com/rightpath/api/v2/auth/exception/RefreshTokenReuseException.java`
- `src/main/java/com/rightpath/api/v2/auth/repository/RefreshTokenV2Repository.java`
- `src/main/java/com/rightpath/api/v2/auth/security/config/SecurityConfigV2.java`
- `src/main/java/com/rightpath/api/v2/auth/security/filter/JwtV2AuthenticationFilter.java`
- `src/main/java/com/rightpath/api/v2/auth/service/AccessTokenServiceV2.java`
- `src/main/java/com/rightpath/api/v2/auth/service/RefreshTokenServiceV2.java`
- `src/main/java/com/rightpath/api/v2/auth/service/V2TokenFacade.java`
- `src/main/java/com/rightpath/api/v2/auth/util/RefreshCookieFactory.java`
- `src/main/java/com/rightpath/api/v2/auth/util/TokenHashing.java`
- `src/test/java/com/rightpath/api/v2/auth/AccessTokenServiceV2Test.java`
- `src/test/java/com/rightpath/api/v2/auth/RefreshTokenServiceV2Test.java`
- `docs/adr/0001-v2-auth-access-jwt-rotating-refresh.md`

**Files modified**

- `src/main/resources/application.properties` (added V2 security properties)
- `pom.xml` (build config tweaks during Lombok/compile triage)
- `src/main/java/com/rightpath/entity/Users.java` (DTO constructor mapping fix)
- `src/main/java/com/rightpath/dto/UsersDto.java` (removed invalid import/cleanup)

**Model / mode used**

- GitHub Copilot (agent mode)
