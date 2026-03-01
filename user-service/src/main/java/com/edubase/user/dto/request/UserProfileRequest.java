package com.edubase.user.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileRequest {


    @NotNull(message = "Email bilgisi boş olamaz")
    @NotEmpty(message = "Email bilgisi boş olamaz")
    @Size(min = 2, max = 30, message = "Email alanı 2-30 karakter olmalı")
    private String email;

    @NotNull(message = "İsim bilgisi boş olamaz")
    @NotEmpty(message = "İsim bilgisi boş olamaz")
    @Size(min = 2, max = 35, message = "İsim alanı 2-35 karakter olmalı")
    private String firstName;

    @NotNull(message = "Soyisim bilgisi boş olamaz")
    @NotEmpty(message = "Soysimi bilgisi boş olamaz")
    @Size(min = 2, max = 20, message = "Soyisim alanı 2-35 karakter olmalı")
    private String lastName;

    @Size(max = 50 , message = "Başlık alanı en fazla 50 karakter olmalı")
    private String headline;

    @Size(max = 250, message = "Biyografi alanı en fazla 250 karakter olmalıdır.")
    private String biography;


    private String avatarUrl;


    private Map<String, String> socialLinks; // LinkedIn, GitHub vb.



}
