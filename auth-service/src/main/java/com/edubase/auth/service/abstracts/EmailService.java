package com.edubase.auth.service.abstracts;

public interface EmailService {

    void sendAccountReactivationEmail(String toEmail, String reactivationLink);

}
