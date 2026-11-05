package com.queuebuddy.queuebuddy.dto;

public record DoctorOption(Long id, String name, String specialization, boolean onDuty, long waiting) {}