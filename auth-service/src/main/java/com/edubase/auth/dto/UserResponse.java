package com.edubase.auth.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    private boolean isActive;


}
