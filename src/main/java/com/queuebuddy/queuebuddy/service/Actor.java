package com.queuebuddy.queuebuddy.service;

import org.springframework.security.oauth2.jwt.Jwt;

public record Actor(Long id, Long clinicId, String role) {

    public static Actor from(Jwt jwt) {
        Object clinic = jwt.getClaim("clinicId");
        Long clinicId = clinic instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(clinic));
        return new Actor(Long.valueOf(jwt.getSubject()), clinicId, jwt.getClaimAsString("role"));
    }

    public boolean isDoctor() { return "DOCTOR".equals(role); }
    public boolean isAdmin() { return "ADMIN".equals(role); }
}