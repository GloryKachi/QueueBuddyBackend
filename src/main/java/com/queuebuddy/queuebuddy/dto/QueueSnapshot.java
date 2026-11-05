package com.queuebuddy.queuebuddy.dto;

import java.time.LocalDate;
import java.util.List;

public record QueueSnapshot(LocalDate date, String clinicName, String viewerRole, Boolean onDuty,
                            List<QueueItem> requests, List<QueueItem> waiting, List<QueueItem> serving,
                            QueueStats stats, List<DoctorOption> doctors) {}