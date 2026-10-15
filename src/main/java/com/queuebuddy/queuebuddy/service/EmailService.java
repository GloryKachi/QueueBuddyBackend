package com.queuebuddy.queuebuddy.service;

public interface EmailService {
    void sendVerificationEmail(String to, String name, String link);
}