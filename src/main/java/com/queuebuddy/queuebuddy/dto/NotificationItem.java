package com.queuebuddy.queuebuddy.dto;

import java.time.Instant;

public record NotificationItem(Long id, String type, String message, Instant createdAt) {}