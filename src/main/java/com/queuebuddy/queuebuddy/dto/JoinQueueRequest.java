package com.queuebuddy.queuebuddy.dto;

import com.queuebuddy.queuebuddy.entity.VisitType;
import jakarta.validation.constraints.NotNull;

public record JoinQueueRequest(@NotNull(message = "Choose a visit type") VisitType visitType) {}