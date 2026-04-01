package com.rightpath.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailAsyncService {

    private static final Logger log = LoggerFactory.getLogger(EmailAsyncService.class);

    private final EmailService emailService;

    public EmailAsyncService(EmailService emailService) {
        this.emailService = emailService;
    }

    @Async
    public void sendInterviewEmail(String to, String subject, String htmlBody) {
        try {
            emailService.sendHtmlEmail(to, subject, htmlBody);
        } catch (Exception e) {
            log.warn("Async email send failed to {}: {}", to, e.getMessage());
        }
    }
}
