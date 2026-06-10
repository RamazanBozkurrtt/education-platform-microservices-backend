package com.edubase.auth.service.abstracts;

public interface PasswordResetService {

    void forgotPassword(String email);

    void validateResetToken(String rawToken);

    void resetPassword(String rawToken, String newPassword, String confirmPassword);
}
