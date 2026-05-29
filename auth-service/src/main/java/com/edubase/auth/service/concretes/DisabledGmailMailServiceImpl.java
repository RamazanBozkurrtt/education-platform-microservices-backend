package com.edubase.auth.service.concretes;

import com.edubase.auth.service.abstracts.GmailMailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnProperty(prefix = "app.gmail", name = "enabled", havingValue = "false")
public class DisabledGmailMailServiceImpl implements GmailMailService {

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        log.warn("Skipping password reset email because app.gmail.enabled=false. target={}", toEmail);
    }

    @Override
    public void sendReactivateAccountEmail(String toEmail, String reactivateLink) {
        log.warn("Skipping account reactivation email because app.gmail.enabled=false. target={}", toEmail);
    }
}
