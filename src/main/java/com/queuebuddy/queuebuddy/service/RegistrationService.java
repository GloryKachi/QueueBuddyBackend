package com.queuebuddy.queuebuddy.service;

import com.queuebuddy.queuebuddy.dto.*;
import com.queuebuddy.queuebuddy.entity.*;
import com.queuebuddy.queuebuddy.exception.ApiException;
import com.queuebuddy.queuebuddy.repository.AppUserRepository;
import com.queuebuddy.queuebuddy.repository.ClinicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    // No 0/O/1/I so codes are easy to read and type
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final AppUserRepository users;
    private final ClinicRepository clinics;
    private final PasswordEncoder encoder;
    private final EmailVerificationService verification;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public SignupResponse registerClinic(ClinicSignupRequest r) {
        String email = r.email().trim().toLowerCase();
        String clinicPhone = normalizePhone(r.clinicWhatsapp());

        if (users.existsByEmail(email)) {
            throw conflict("EMAIL_TAKEN", "This email is already registered.");
        }
        if (clinics.existsByWhatsappNumber(clinicPhone)) {
            throw conflict("CLINIC_PHONE_TAKEN", "A clinic with this WhatsApp number is already registered.");
        }

        Clinic clinic = new Clinic();
        clinic.setName(r.clinicName().trim());
        clinic.setWhatsappNumber(clinicPhone);
        clinic.setCity(r.city().trim());
        clinic.setJoinCode(newJoinCode());
        clinics.save(clinic);

        AppUser admin = users.save(newUser(clinic, r.fullName(), email, null, r.password(),
                Role.ADMIN, AccountStatus.PENDING_VERIFICATION));
        verification.issueToken(admin);

        return new SignupResponse(ClinicSummary.from(clinic),
                UserResponse.from(admin, "Check your email to verify your account."));
    }

    @Transactional
    public UserResponse registerPatient(PatientRegistrationRequest r) {
        Clinic clinic = findClinic(r.clinicCode());
        String email = r.email().trim().toLowerCase();
        String phone = normalizePhone(r.phone());
        checkAvailable(email, phone);

        AppUser u = newUser(clinic, r.fullName(), email, phone, r.password(), Role.PATIENT, AccountStatus.ACTIVE);
        return UserResponse.from(users.save(u), "Account created. You can now join the queue.");
    }

    @Transactional
    public UserResponse registerDoctor(DoctorRegistrationRequest r) {
        Clinic clinic = findClinic(r.clinicCode());
        String email = r.email().trim().toLowerCase();
        String phone = normalizePhone(r.phone());
        checkAvailable(email, phone);

        AppUser u = newUser(clinic, r.fullName(), email, phone, r.password(),
                Role.DOCTOR, AccountStatus.PENDING_APPROVAL);
        u.setSpecialization(r.specialization().trim());
        u.setLicenseNumber(r.licenseNumber().trim());
        return UserResponse.from(users.save(u), "Account created. The clinic admin must approve it before you can log in.");
    }

    private void checkAvailable(String email, String phone) {
        if (users.existsByEmail(email)) {
            throw conflict("EMAIL_TAKEN", "This email is already registered.");
        }
        if (users.existsByPhone(phone)) {
            throw conflict("PHONE_TAKEN", "An account with this phone number already exists.");
        }
    }

    private Clinic findClinic(String code) {
        return clinics.findByJoinCode(code.trim().toUpperCase())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLINIC_NOT_FOUND",
                        "We couldn't find a clinic with that code."));
    }

    private AppUser newUser(Clinic clinic, String fullName, String email, String phone, String password,
                            Role role, AccountStatus status) {
        AppUser u = new AppUser();
        u.setClinic(clinic);
        u.setFullName(fullName.trim());
        u.setEmail(email);
        u.setPhone(phone);
        u.setPasswordHash(encoder.encode(password));
        u.setRole(role);
        u.setStatus(status);
        u.setEmailVerified(false);
        return u;
    }

    private String newJoinCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (clinics.existsByJoinCode(code));
        return code;
    }

    // 08031234567 / 2348031234567 / +2348031234567  ->  +2348031234567
    private String normalizePhone(String raw) {
        String p = raw.trim();
        if (p.startsWith("+234")) return p;
        if (p.startsWith("234")) return "+" + p;
        return "+234" + p.substring(1);
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}