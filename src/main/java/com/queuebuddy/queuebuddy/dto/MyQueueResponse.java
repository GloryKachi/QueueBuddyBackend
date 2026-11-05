package com.queuebuddy.queuebuddy.dto;

import java.util.List;

public record MyQueueResponse(String patientName, String clinicName, QueueItem entry,
                              Integer nowServingTicket, long waitingCount, long avgConsultMinutes,
                              List<NotificationItem> updates, List<DoctorOption> doctors) {}