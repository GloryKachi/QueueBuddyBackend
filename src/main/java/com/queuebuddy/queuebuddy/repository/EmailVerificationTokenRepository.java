package com.queuebuddy.queuebuddy.repository;

import com.queuebuddy.queuebuddy.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);
    Optional<EmailVerificationToken> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
    void deleteByUserId(Long userId);
}