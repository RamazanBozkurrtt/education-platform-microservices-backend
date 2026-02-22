package com.edubase.user.entity;

import com.edubase.commonJpa.entity.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.Type;

import java.util.Map;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "profiles")
@AttributeOverride(
        name = "id",
        column = @Column(name = "profile_id")
)
public class UserProfile extends BaseEntity {

    @Column(nullable = false,unique = true)
    private String email;

    private String firstName;
    private String lastName;
    private String headline;
    private String biography;
    private String avatarUrl;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> socialLinks; // LinkedIn, GitHub vb.

    @Enumerated(EnumType.STRING)
    private UserStatus status; // ACTIVE, BANNED, PENDING

}
