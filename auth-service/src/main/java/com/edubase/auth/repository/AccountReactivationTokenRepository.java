package com.edubase.auth.repository;

import com.edubase.auth.entity.AccountReactivationToken;
import com.edubase.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface AccountReactivationTokenRepository extends JpaRepository<AccountReactivationToken, Long> {

    Optional<AccountReactivationToken> findByTokenHash(String tokenHash);

    @Modifying
    void deleteByUser(User user);

    @Modifying
    void deleteByExpiresAtBefore(Instant threshold);
}
