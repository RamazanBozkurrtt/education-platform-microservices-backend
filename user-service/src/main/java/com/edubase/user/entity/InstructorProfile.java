package com.edubase.user.entity;

import com.edubase.commonJpa.entity.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.util.List;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "instructor_profiles")
@AttributeOverride(
        name = "id",
        column = @Column(name = "instructor_profile_id")
)
public class InstructorProfile extends BaseEntity {

    @Column(nullable = false, unique = true)
    private Long authUserId;

    @Column(nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false, length = 2000)
    private String biography;

    @Type(JsonBinaryType.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> expertise;

    @Column(length = 500)
    private String profileImageUrl;

    @Column(length = 500)
    private String websiteUrl;

    @Column(length = 500)
    private String linkedinUrl;

    @Column(length = 500)
    private String githubUrl;
}
