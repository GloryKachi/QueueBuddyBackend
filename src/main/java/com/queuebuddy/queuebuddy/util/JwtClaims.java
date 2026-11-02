package com.queuebuddy.queuebuddy.util;

import org.springframework.security.oauth2.jwt.Jwt;

public final class JwtClaims {

    private JwtClaims() {}

    public static Long clinicId(Jwt jwt) {
        return jwt.<Number>getClaim("clinicId").longValue();
    }

    public static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}