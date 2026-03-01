package com.edubase.auth.service.concretes;

import com.edubase.auth.service.abstracts.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    @Override
    public void sendAccountReactivationEmail(String toEmail, String reactivationLink) {
        log.info("Reactivation link generated for {}: {}", toEmail, reactivationLink);
    }
}
