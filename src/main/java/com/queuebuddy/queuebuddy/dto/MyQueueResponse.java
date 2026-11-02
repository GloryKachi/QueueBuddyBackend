package com.queuebuddy.queuebuddy.dto;

import java.util.List;

public record MyQueueResponse(
        String clinicName,
        QueueItem entry,               // the patient's latest request today, or null
        Integer nowServingTicket,
        int waitingCount,
        int avgConsultMinutes,
        List<NotificationItem> updates) {}