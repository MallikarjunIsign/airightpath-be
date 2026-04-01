package com.rightpath.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rightpath.entity.RoomVerificationSession;

public interface RoomVerificationRepository
        extends JpaRepository<RoomVerificationSession, Long> {

    Optional<RoomVerificationSession> findBySessionId(String sessionId);

}