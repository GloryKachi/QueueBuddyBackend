package com.queuebuddy.queuebuddy.dto;

public record LoginResponse(String token, long expiresInSeconds, UserResponse user, ClinicSummary clinic) {}