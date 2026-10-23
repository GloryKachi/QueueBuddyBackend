package com.queuebuddy.queuebuddy.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Enter your email") @Email(message = "Enter a valid email") String email,
        @NotBlank(message = "Enter your password") String password) {}