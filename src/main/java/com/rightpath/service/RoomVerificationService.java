package com.rightpath.service;

import org.springframework.web.multipart.MultipartFile;

import com.rightpath.entity.RoomVerificationSession;

public interface RoomVerificationService {

    String createSession();

    void verifyRoom(String sessionId, MultipartFile image);

    RoomVerificationSession getStatus(String sessionId);

}