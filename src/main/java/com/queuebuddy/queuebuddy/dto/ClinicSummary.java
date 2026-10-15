package com.queuebuddy.queuebuddy.dto;

import com.queuebuddy.queuebuddy.entity.Clinic;

public record ClinicSummary(Long id, String name, String whatsappNumber, String city, String joinCode) {
    public static ClinicSummary from(Clinic c) {
        return new ClinicSummary(c.getId(), c.getName(), c.getWhatsappNumber(), c.getCity(), c.getJoinCode());
    }
}