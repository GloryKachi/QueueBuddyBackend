package com.queuebuddy.queuebuddy.dto;

import com.queuebuddy.queuebuddy.entity.AccountStatus;
import com.queuebuddy.queuebuddy.entity.AppUser;
import com.queuebuddy.queuebuddy.entity.Role;

public record UserResponse(Long id, String fullName, String email, String phone,
                           Role role, AccountStatus status, String specialization, String message) {

    public static UserResponse from(AppUser u, String message) {
        return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(),
                u.getRole(), u.getStatus(), u.getSpecialization(), message);
    }
}