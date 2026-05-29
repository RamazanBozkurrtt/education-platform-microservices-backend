package com.edubase.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Mevcut sifre bos olamaz")
        String oldPassword,

        @NotBlank(message = "Yeni sifre bos olamaz")
        @Size(min = 8, message = "Yeni sifre en az 8 karakter olmali")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z]).{8,}$",
                message = "Yeni sifre en az bir buyuk ve bir kucuk harf icermeli"
        )
        String newPassword
) {
}
