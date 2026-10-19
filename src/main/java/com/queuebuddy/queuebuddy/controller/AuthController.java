package com.queuebuddy.queuebuddy.controller;

import com.queuebuddy.queuebuddy.dto.*;
import com.queuebuddy.queuebuddy.service.EmailVerificationService;
import com.queuebuddy.queuebuddy.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RegistrationService registration;
    private final EmailVerificationService verification;

    @PostMapping("/register/clinic")
    public ResponseEntity<SignupResponse> registerClinic(@Valid @RequestBody ClinicSignupRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registration.registerClinic(req));
    }

    @PostMapping("/register/patient")
    public ResponseEntity<UserResponse> registerPatient(@Valid @RequestBody PatientRegistrationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registration.registerPatient(req));
    }

    @PostMapping("/register/doctor")
    public ResponseEntity<UserResponse> registerDoctor(@Valid @RequestBody DoctorRegistrationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registration.registerDoctor(req));
    }

    @PostMapping("/verify-email")
    public SignupResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
        return verification.verify(req.token());
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resend(@Valid @RequestBody ResendVerificationRequest req) {
        verification.resend(req.email());
        return ResponseEntity.accepted().build();
    }
}