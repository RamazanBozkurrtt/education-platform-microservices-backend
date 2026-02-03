package com.edubase.auth.entity;


import com.edubase.common.entity.BaseEntity;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Getter
@Table(name = "refresh_token")
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken{

    @Id
    @Tsid
    private Long id;

    @Column(nullable = false, unique = true)
    private String refreshToken;

    @Column(nullable = false)
    private Instant expiryDate;

    @Column(nullable = false)
    private boolean revoked;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id",referencedColumnName = "user_id")
    @ToString.Exclude
    private User user;

}
