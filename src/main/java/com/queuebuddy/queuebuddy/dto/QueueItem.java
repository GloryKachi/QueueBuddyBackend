package com.queuebuddy.queuebuddy.dto;

import com.queuebuddy.queuebuddy.entity.QueueStatus;
import java.time.Instant;

public record QueueItem(
        Long id, Integer ticketNumber, String patientName, String maskedPhone,
        QueueStatus status, String displayStatus,
        long waitingMinutes, long estimatedWaitMinutes, int position,
        Instant joinedAt, Instant calledAt, String servedByName, String rejectionReason,
        Long doctorId, String doctorName, Long preferredDoctorId, String preferredDoctorName) {}