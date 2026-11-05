package com.queuebuddy.queuebuddy.dto;

import jakarta.validation.constraints.NotNull;

public record AssignRequest(@NotNull Long doctorId) {}