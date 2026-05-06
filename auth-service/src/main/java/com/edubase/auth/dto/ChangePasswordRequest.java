package com.edubase.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Mevcut sifre bos olamaz")
        String oldPassword,

        @NotBlank(message = "Yeni sifre bos olamaz")
        @Size(min = 6, message = "Yeni sifre en az 6 karakter olmali")
        String newPassword
) {
}
