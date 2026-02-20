package com.edubase.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class UserRequest {

    @NotNull(message = "Şifre boş olamaz")
    @NotEmpty(message = "Şifre boş olamaz")
    @Size(min = 6, max = 20, message = "Şifre en az 6 karakter olmalı")
    private String passwordHash;

    @Email(message = "Geçerli bir e-posta formatı girin")
    @NotEmpty
    @Size(max = 80)
    private String email;
}
