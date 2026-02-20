package com.edubase.user.dto.response;

import com.edubase.user.entity.UserStatus;
import lombok.*;


import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    private String email;

    private String firstName;
    private String lastName;
    private String headline;
    private String biography;
    private String avatarUrl;

    private Map<String, String> socialLinks; // LinkedIn, GitHub vb.

    private UserStatus status; // ACTIVE, BANNED, PENDING


}
