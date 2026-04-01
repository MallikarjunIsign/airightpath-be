# ADR 0001: API V2 Auth — short-lived JWT access tokens + rotating refresh tokens

- Date: 2026-01-02
- Status: Accepted

## Context

The existing authentication approach (V1) issues a single JWT for authentication. This has a few practical gaps for a web-only monolith:

- Long-lived access tokens increase risk if a token is leaked.
- No refresh token mechanism exists, so the only way to extend sessions is to mint longer-lived access tokens.
- JWT validation needs to be stricter (issuer/audience/clock skew handling) to support future hardening.
- We want to introduce a new design without disturbing existing V1 logic.

Constraints:

- Spring Boot + Spring Security monolith.
- V2 endpoints should be isolated under `/api/v2/**`.
- Web-only usage under the same site (e.g., `arightpath.com`).

## Decision

Introduce API V2 authentication with:

1) **Short-lived access tokens (JWT)**
- Signed JWT used for normal API authorization.
- Tight TTL (default minutes, configurable).
- Strict validation of issuer/audience and standard time claims with allowed clock skew.
- Subject identifies the user (email).

2) **Opaque refresh tokens with rotation**
- Refresh tokens are **opaque random values**, not JWTs.
- Stored **hashed** in the database (hash-only storage) with metadata: user email, session id, expiry, revoked state.
- **Rotation on every refresh**: successful refresh revokes the old token and issues a new one.
- **Reuse detection**: if a revoked token is presented again, revoke the whole session chain.

3) **Web-safe delivery of refresh tokens**
- Refresh tokens are returned via an **HttpOnly** cookie for browser flows.
- Cookie is scoped to the refresh endpoint and marked `Secure` when configured.
- SameSite set for web use to reduce CSRF risk while supporting top-level navigation.

4) **V2-only security chain**
- Add a dedicated `SecurityFilterChain` that matches only `/api/v2/**`.
- Add a V2 JWT filter that authenticates requests under `/api/v2/**` but skips `/api/v2/auth/**`.
- Keep V1 security configuration unchanged.

## Why this approach

- Separates concerns: access token for frequent API calls and refresh token for session continuity.
- Limits blast radius: leaked access token expires quickly.
- Reduces refresh token theft risk: opaque tokens + DB hashing prevent server-side disclosure.
- Rotation + reuse detection is a proven pattern for mitigating replay attacks.
- Isolating security on `/api/v2/**` reduces the chance of breaking V1 endpoints.

## Alternatives considered

1) **Single long-lived JWT**
- Simpler, but higher risk and harder to revoke.

2) **Refresh token as JWT**
- Can be stateless, but revocation/rotation/reuse detection gets harder.
- If stored client-side, theft/replay is harder to detect.

3) **Server-side sessions (stateful)**
- Strong control, but more operational complexity and can be a larger redesign.

4) **Third-party identity provider (OIDC)**
- Best for larger organizations, but out of scope for this incremental V2 improvement.

## Consequences / trade-offs

- Adds a database dependency for refresh token validation and rotation.
- Requires periodic cleanup of expired/revoked refresh token records.
- Cookie-based refresh flows must be carefully scoped (path, samesite, csrf posture).
- Requires consistent configuration for issuer/audience/clock skew to avoid client clock issues.

Operational notes:

- Logging must avoid printing raw refresh tokens.
- Secrets/keys must be rotated safely; issuer/audience should be treated as contracts with clients.
