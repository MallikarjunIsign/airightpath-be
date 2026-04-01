package com.rightpath.service.impl;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rightpath.config.AuthProperties;
import com.rightpath.entity.RefreshToken;
import com.rightpath.exceptions.InvalidRefreshTokenException;
import com.rightpath.exceptions.RefreshTokenExpiredException;
import com.rightpath.exceptions.RefreshTokenReuseException;
import com.rightpath.repository.RefreshTokenRepository;
import com.rightpath.util.TokenHashing;

@Service
public class RefreshTokenService {

    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository repo;
    private final AuthProperties props;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repo, AuthProperties props) {
        this.repo = repo;
        this.props = props;
    }

    public record IssuedRefreshToken(UUID sessionId, String rawToken) {
    }

    public record RotatedRefreshToken(UUID sessionId, String userEmail, String rawToken) {
    }

    @Transactional
    public IssuedRefreshToken createNewSessionToken(String userEmail, String ip, String userAgent) {
        UUID sessionId = UUID.randomUUID();
        String raw = generateOpaqueToken();

        RefreshToken entity = new RefreshToken();
        entity.setId(UUID.randomUUID());
        entity.setUserEmail(userEmail);
        entity.setSessionId(sessionId);
        entity.setTokenHash(TokenHashing.sha256Hex(raw));
        entity.setCreatedAt(Instant.now());
        entity.setLastUsedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plus(props.getRefresh().getTtlDays(), ChronoUnit.DAYS));
        entity.setIpAddress(ip);
        entity.setUserAgent(userAgent);

        repo.save(entity);
        logger.info("Refresh token created for user={}, session={}", userEmail, sessionId);
        return new IssuedRefreshToken(sessionId, raw);
    }

    @Transactional
    public RotatedRefreshToken rotate(String rawToken, String ip, String userAgent) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException("Missing refresh token");
        }

        String hash = TokenHashing.sha256Hex(rawToken);
        RefreshToken current = repo.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));

        Instant now = Instant.now();

        if (current.isRevoked()) {
            logger.warn("Refresh token reuse detected for user={}, session={}", current.getUserEmail(), current.getSessionId());
            revokeSession(current.getSessionId(), now);
            throw new RefreshTokenReuseException("Refresh token reuse detected", current.getSessionId());
        }

        if (current.isExpired(now)) {
            revokeToken(current, now, null);
            throw new RefreshTokenExpiredException("Refresh token expired");
        }

        String newRaw = generateOpaqueToken();
        RefreshToken next = new RefreshToken();
        next.setId(UUID.randomUUID());
        next.setUserEmail(current.getUserEmail());
        next.setSessionId(current.getSessionId());
        next.setTokenHash(TokenHashing.sha256Hex(newRaw));
        next.setCreatedAt(now);
        next.setLastUsedAt(now);
        next.setExpiresAt(now.plus(props.getRefresh().getTtlDays(), ChronoUnit.DAYS));
        next.setIpAddress(ip);
        next.setUserAgent(userAgent);

        repo.save(next);

        revokeToken(current, now, next.getId());

        logger.info("Refresh token rotated for user={}, session={}", current.getUserEmail(), current.getSessionId());
        return new RotatedRefreshToken(current.getSessionId(), current.getUserEmail(), newRaw);
    }

    @Transactional
    public void revokeByRawTokenIfPresent(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String hash = TokenHashing.sha256Hex(rawToken);
        repo.findByTokenHash(hash).ifPresent(rt -> revokeToken(rt, Instant.now(), null));
    }

    @Transactional
    public void revokeSession(UUID sessionId, Instant now) {
        List<RefreshToken> active = repo.findBySessionIdAndRevokedAtIsNull(sessionId);
        for (RefreshToken token : active) {
            revokeToken(token, now, null);
        }
    }

    private void revokeToken(RefreshToken token, Instant now, UUID replacedBy) {
        if (token.getRevokedAt() == null) {
            token.setRevokedAt(now);
        }
        token.setLastUsedAt(now);
        token.setReplacedByTokenId(replacedBy);
        repo.save(token);
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
