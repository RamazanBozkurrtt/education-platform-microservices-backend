package com.edubase.auth.repository;

import com.edubase.auth.entity.RefreshToken;
import com.edubase.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken,Long> {

    @Modifying
    void deleteByUser(User user);

    void deleteByUserEmail(String email);

    Optional<RefreshToken> findByRefreshToken(String refreshToken);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = :userId AND rt.revoked = false")
    int revokeAllValidTokensByUserId(@Param("userId") Long userId);

    void deleteByExpiryDateBefore(Instant expiryDateBefore);
}
