package com.edubase.user.repository;


import com.edubase.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile,Long> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<UserProfile> findByEmailIgnoreCase(String email);

    Optional<UserProfile> findByAuthUserId(Long authUserId);
}
