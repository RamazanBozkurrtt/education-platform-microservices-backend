package com.edubase.user.repository;

import com.edubase.user.entity.InstructorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InstructorProfileRepository extends JpaRepository<InstructorProfile, Long> {
    Optional<InstructorProfile> findByAuthUserId(Long authUserId);
}
