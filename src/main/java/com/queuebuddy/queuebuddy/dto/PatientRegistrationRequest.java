package com.queuebuddy.queuebuddy.dto;

import jakarta.validation.constraints.*;

public record PatientRegistrationRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Name is too long") String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email") String email,

        @NotBlank(message = "WhatsApp number is required")
        @Pattern(regexp = Patterns.NG_PHONE, message = "Enter a valid Nigerian number, e.g. 08031234567") String phone,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String password,

        @NotBlank(message = "Clinic code is required") String clinicCode) {
}