package com.edubase.auth.repository;

import com.edubase.auth.entity.RefreshToken;
import com.edubase.auth.entity.User;
import io.hypersistence.tsid.TSID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken,Integer> {

    @Modifying
    void deleteByUser(User user);

    Optional<RefreshToken> findByRefreshToken(String refreshToken);

    @Query("SELECT rt FROM RefreshToken rt INNER JOIN rt.user u WHERE u.id = :userId AND rt.revoked = false")
    List<RefreshToken> findAllValidRefreshTokenByUser(@Param("userId") Long userId);

    void deleteByExpiryDateBefore(Instant expiryDateBefore);
}
