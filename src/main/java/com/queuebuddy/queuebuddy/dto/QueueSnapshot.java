package com.queuebuddy.queuebuddy.dto;

import java.util.List;

public record QueueSnapshot(
        String date,
        String clinicName,
        List<QueueItem> requests,      // patients asking to join, oldest first
        List<QueueItem> waiting,
        List<QueueItem> serving,
        QueueStats stats) {}