package com.edubase.user.dto.request;

import jakarta.validation.constraints.Email;
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

    @Email(message = "Email format is invalid")
    @Size(max = 254, message = "Email must be at most 254 characters")
    private String email;

    @Size(min = 2, max = 35, message = "First name must be 2-35 characters")
    private String firstName;

    @Size(min = 2, max = 20, message = "Last name must be 2-20 characters")
    private String lastName;

    @Size(max = 50, message = "Headline must be at most 50 characters")
    private String headline;

    @Size(max = 250, message = "Biography must be at most 250 characters")
    private String biography;

    private String avatarUrl;

    private Map<String, String> socialLinks; // LinkedIn, GitHub etc.
}
