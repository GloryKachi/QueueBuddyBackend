package com.queuebuddy.queuebuddy.dto;

import com.queuebuddy.queuebuddy.entity.NotificationType;

public record NotificationItem(Long id, NotificationType type, String message, String createdAt) {}