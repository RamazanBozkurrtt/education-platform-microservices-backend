package com.edubase.auth.repository;

import com.edubase.auth.entity.PasswordResetToken;
import com.edubase.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update PasswordResetToken t
               set t.usedAt = :usedAt
             where t.user = :user
               and t.usedAt is null
            """)
    int markAllUnusedAsUsedByUser(@Param("user") User user, @Param("usedAt") Instant usedAt);
}
