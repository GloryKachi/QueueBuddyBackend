package com.queuebuddy.queuebuddy.dto;

import jakarta.validation.constraints.*;

public record ClinicSignupRequest(
        @NotBlank(message = "Clinic name is required")
        @Size(max = 120, message = "Clinic name is too long") String clinicName,

        @NotBlank(message = "Clinic WhatsApp number is required")
        @Pattern(regexp = Patterns.NG_PHONE, message = "Enter a valid Nigerian number, e.g. 08031234567") String clinicWhatsapp,

        @NotBlank(message = "City is required")
        @Size(max = 120, message = "City is too long") String city,

        @NotBlank(message = "Your full name is required")
        @Size(max = 100, message = "Name is too long") String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email") String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String password,

        @AssertTrue(message = "You must agree to the Terms and Privacy Policy") boolean acceptedTerms) {
}