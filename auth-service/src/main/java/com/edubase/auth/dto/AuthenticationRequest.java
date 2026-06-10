package com.edubase.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AuthenticationRequest(
        @NotBlank(message = "Email boş olamaz")
        @Email
        String email,

        @NotBlank(message = "Şifre boş olamaz")
        String password
) {
}
