package com.edubase.auth.service.concretes;

import com.edubase.auth.service.abstracts.EmailService;
import com.edubase.auth.service.abstracts.GmailMailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final GmailMailService gmailMailService;

    @Override
    public void sendAccountReactivationEmail(String toEmail, String reactivationLink) {
        gmailMailService.sendReactivateAccountEmail(toEmail, reactivationLink);
        log.info("Account reactivation email flow completed.");
    }
}
