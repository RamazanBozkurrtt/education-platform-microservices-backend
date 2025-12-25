package com.edubase.auth.repository;

import com.edubase.auth.entity.RefreshToken;
import com.edubase.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken,Integer> {

    @Modifying
    void deleteByUser(User user);

    void deleteByUserEmail(String email);

    Optional<RefreshToken> findByRefreshToken(String refreshToken);

    @Query("SELECT rt FROM RefreshToken rt INNER JOIN rt.user u WHERE u.id = :userId AND rt.revoked = false")
    List<RefreshToken> findAllValidRefreshTokenByUser(@Param("userId") Long userId);

    void deleteByExpiryDateBefore(Instant expiryDateBefore);
}
