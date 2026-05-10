package com.edubase.auth.dto;


import com.edubase.auth.entity.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class UserResponse {

    private Long id;

    private String email;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private boolean locked;

    private UserStatus userStatus;

    private List<String> roles;


}
