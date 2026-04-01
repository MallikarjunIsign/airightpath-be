package com.rightpath.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rightpath.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findBySessionIdAndRevokedAtIsNull(UUID sessionId);

    List<RefreshToken> findByUserEmailAndRevokedAtIsNull(String userEmail);

    long deleteByExpiresAtBefore(Instant cutoff);
}
