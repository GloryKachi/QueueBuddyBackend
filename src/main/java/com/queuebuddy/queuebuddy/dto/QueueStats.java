package com.queuebuddy.queuebuddy.dto;

public record QueueStats(int waiting, int pendingRequests, int avgConsultMinutes,
                         int longestWaitMinutes, int servedToday) {}