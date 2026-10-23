package com.queuebuddy.queuebuddy.service;

import com.queuebuddy.queuebuddy.dto.*;
import com.queuebuddy.queuebuddy.entity.AppUser;
import com.queuebuddy.queuebuddy.exception.ApiException;
import com.queuebuddy.queuebuddy.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;

    @Value("${app.jwt.expiry-hours:8}")
    private long expiryHours;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest r) {
        // Same error for "no such email" and "wrong password" so emails can't be probed
        AppUser u = users.findByEmail(r.email().trim().toLowerCase())
                .filter(x -> encoder.matches(r.password(), x.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                        "INVALID_CREDENTIALS", "Incorrect email or password."));

        switch (u.getStatus()) {
            case PENDING_VERIFICATION -> throw new ApiException(HttpStatus.FORBIDDEN,
                    "EMAIL_NOT_VERIFIED", "Please verify your email before signing in.");
            case PENDING_APPROVAL -> throw new ApiException(HttpStatus.FORBIDDEN,
                    "PENDING_APPROVAL", "Your clinic admin hasn't approved your account yet.");
            case SUSPENDED -> throw new ApiException(HttpStatus.FORBIDDEN,
                    "ACCOUNT_SUSPENDED", "This account has been suspended. Contact your clinic.");
            default -> { }
        }

        Instant now = Instant.now();
        Duration lifetime = Duration.ofHours(expiryHours);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("queuebuddy")
                .issuedAt(now)
                .expiresAt(now.plus(lifetime))
                .subject(String.valueOf(u.getId()))
                .claim("role", u.getRole().name())
                .claim("clinicId", u.getClinic().getId())
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        return new LoginResponse(token, lifetime.toSeconds(),
                UserResponse.from(u, null), ClinicSummary.from(u.getClinic()));
    }

    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        AppUser u = users.findById(userId).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Please sign in again."));
        return new MeResponse(UserResponse.from(u, null), ClinicSummary.from(u.getClinic()));
    }
}