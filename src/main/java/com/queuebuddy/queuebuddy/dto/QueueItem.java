package com.queuebuddy.queuebuddy.dto;

import com.queuebuddy.queuebuddy.entity.QueueStatus;
import com.queuebuddy.queuebuddy.entity.VisitType;

public record QueueItem(
        Long id,
        Integer ticketNumber,          // null until the request is accepted
        String patientName,
        String maskedPhone,
        VisitType visitType,
        QueueStatus status,
        String displayStatus,
        long waitingMinutes,
        Integer estimatedWaitMinutes,
        Integer position,
        String joinedAt,
        String calledAt,
        String servedByName,
        String rejectionReason) {}