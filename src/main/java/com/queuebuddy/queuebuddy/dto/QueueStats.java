package com.queuebuddy.queuebuddy.dto;

public record QueueStats(long waiting, long pendingRequests, long avgConsultMinutes,
                         long longestWaitMinutes, long servedToday) {}