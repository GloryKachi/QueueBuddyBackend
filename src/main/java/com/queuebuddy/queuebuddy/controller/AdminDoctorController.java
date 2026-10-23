package com.queuebuddy.queuebuddy.controller;

import com.queuebuddy.queuebuddy.dto.DoctorSummary;
import com.queuebuddy.queuebuddy.service.DoctorManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/doctors")
@RequiredArgsConstructor
public class AdminDoctorController {

    private final DoctorManagementService doctors;

    @GetMapping
    public List<DoctorSummary> list(@AuthenticationPrincipal Jwt jwt) {
        return doctors.list(clinicId(jwt));
    }

    @PostMapping("/{id}/approve")
    public DoctorSummary approve(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return doctors.approve(clinicId(jwt), id);
    }

    @PostMapping("/{id}/suspend")
    public DoctorSummary suspend(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return doctors.suspend(clinicId(jwt), id);
    }

    private static Long clinicId(Jwt jwt) {
        return jwt.<Number>getClaim("clinicId").longValue();
    }
}