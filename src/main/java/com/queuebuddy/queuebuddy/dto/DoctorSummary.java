package com.queuebuddy.queuebuddy.dto;

import com.queuebuddy.queuebuddy.entity.AccountStatus;
import com.queuebuddy.queuebuddy.entity.AppUser;

public record DoctorSummary(
        Long id,
        String fullName,
        String email,
        String phone,
        String specialization,
        String licenseNumber,
        AccountStatus status,
        String createdAt) {

    public static DoctorSummary from(AppUser u) {
        return new DoctorSummary(
                u.getId(),
                u.getFullName(),
                u.getEmail(),
                u.getPhone(),
                u.getSpecialization(),
                u.getLicenseNumber(),
                u.getStatus(),
                u.getCreatedAt() == null ? null : u.getCreatedAt().toString());
    }
}