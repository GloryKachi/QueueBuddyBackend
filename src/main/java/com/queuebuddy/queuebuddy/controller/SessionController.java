package com.queuebuddy.queuebuddy.controller;

import com.queuebuddy.queuebuddy.dto.LoginRequest;
import com.queuebuddy.queuebuddy.dto.LoginResponse;
import com.queuebuddy.queuebuddy.dto.MeResponse;
import com.queuebuddy.queuebuddy.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessions;

    @PostMapping("/api/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return sessions.login(request);
    }

    @GetMapping("/api/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return sessions.me(Long.valueOf(jwt.getSubject()));
    }
}