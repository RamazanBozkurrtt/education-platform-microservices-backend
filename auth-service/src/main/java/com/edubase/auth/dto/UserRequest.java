package com.edubase.auth.dto;

import com.edubase.auth.entity.UserStatus;
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

    @NotNull(message = "Sifre bos olamaz")
    @NotEmpty(message = "Sifre bos olamaz")
    @Size(min = 6, max = 20, message = "Sifre en az 6 karakter olmali")
    private String passwordHash;

    @Email(message = "Gecerli bir e-posta formati girin")
    @NotEmpty
    @Size(max = 80)
    private String email;

    @NotNull(message = "Kullanici durumu bos olamaz")
    private UserStatus userStatus;
}