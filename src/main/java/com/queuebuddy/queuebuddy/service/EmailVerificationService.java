package com.queuebuddy.queuebuddy.service;

import com.queuebuddy.queuebuddy.dto.ClinicSummary;
import com.queuebuddy.queuebuddy.dto.SignupResponse;
import com.queuebuddy.queuebuddy.dto.UserResponse;
import com.queuebuddy.queuebuddy.entity.AccountStatus;
import com.queuebuddy.queuebuddy.entity.AppUser;
import com.queuebuddy.queuebuddy.entity.EmailVerificationToken;
import com.queuebuddy.queuebuddy.exception.ApiException;
import com.queuebuddy.queuebuddy.repository.AppUserRepository;
import com.queuebuddy.queuebuddy.repository.EmailVerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(30);

    private final EmailVerificationTokenRepository tokens;
    private final AppUserRepository users;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Transactional
    public void issueToken(AppUser user) {
        tokens.deleteByUserId(user.getId());

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        EmailVerificationToken t = new EmailVerificationToken();
        t.setUserId(user.getId());
        t.setTokenHash(hash(rawToken));
        t.setExpiresAt(Instant.now().plus(TOKEN_LIFETIME));
        tokens.save(t);

        emailService.sendVerificationEmail(user.getEmail(), user.getFullName(),
                frontendUrl + "/verify-email?token=" + rawToken);
    }

    @Transactional
    public SignupResponse verify(String rawToken) {
        EmailVerificationToken t = tokens.findByTokenHash(hash(rawToken))
                .filter(x -> x.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(this::invalidLink);
        AppUser user = users.findById(t.getUserId()).orElseThrow(this::invalidLink);

        user.setEmailVerified(true);
        user.setStatus(AccountStatus.ACTIVE);
        tokens.delete(t);

        return new SignupResponse(ClinicSummary.from(user.getClinic()),
                UserResponse.from(user, "Your clinic is ready!"));
    }

    @Transactional
    public void resend(String email) {
        users.findByEmail(email.trim().toLowerCase())
                .filter(u -> !u.isEmailVerified() && u.getStatus() == AccountStatus.PENDING_VERIFICATION)
                .ifPresent(u -> {
                    tokens.findFirstByUserIdOrderByCreatedAtDesc(u.getId())
                            .filter(last -> last.getCreatedAt().isAfter(Instant.now().minus(RESEND_COOLDOWN)))
                            .ifPresent(last -> {
                                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RESEND_TOO_SOON",
                                        "Please wait a moment before requesting another link.");
                            });
                    issueToken(u);
                });
    }

    private ApiException invalidLink() {
        return new ApiException(HttpStatus.BAD_REQUEST, "TOKEN_INVALID", "This link is invalid or has expired.");
    }

    private static String hash(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}