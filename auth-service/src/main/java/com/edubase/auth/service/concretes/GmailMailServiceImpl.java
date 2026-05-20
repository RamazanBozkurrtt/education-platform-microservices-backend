package com.edubase.auth.service.concretes;

import com.edubase.auth.configuration.GmailCredentialsProvider;
import com.edubase.auth.service.abstracts.GmailMailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GmailMailServiceImpl implements GmailMailService {

    private static final String RESET_SUBJECT = "Reset your EduBase password";
    private static final String REACTIVATE_SUBJECT = "Reactivate your EduBase account";

    private final JavaMailSender javaMailSender;
    private final GmailCredentialsProvider credentialsProvider;

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(credentialsProvider.getUsername());
        message.setTo(toEmail);
        message.setSubject(RESET_SUBJECT);
        message.setText(buildResetBody(resetLink));
        javaMailSender.send(message);
        log.info("Password reset email sent.");
    }

    @Override
    public void sendReactivateAccountEmail(String toEmail, String reactivateLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(credentialsProvider.getUsername());
        message.setTo(toEmail);
        message.setSubject(REACTIVATE_SUBJECT);
        message.setText(buildReactivationBody(reactivateLink));
        javaMailSender.send(message);
        log.info("Account reactivation email sent.");
    }

    private String buildResetBody(String resetLink) {
        return """
                Hello,

                You requested to reset your EduBase password.

                Click the link below to set a new password:
                %s

                This link will expire in 15 minutes.

                If you did not request this, you can ignore this email.
                """.formatted(resetLink);
    }

    private String buildReactivationBody(String reactivateLink) {
        return """
                Hello,

                We received a request to reactivate your EduBase account.

                Click the link below to reactivate your account:
                %s

                This link will expire soon.

                If you did not request this, you can ignore this email.
                """.formatted(reactivateLink);
    }
}
