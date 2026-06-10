package com.edubase.auth.service.abstracts;

public interface GmailMailService {

    void sendPasswordResetEmail(String toEmail, String resetLink);

    void sendReactivateAccountEmail(String toEmail, String reactivateLink);
}
