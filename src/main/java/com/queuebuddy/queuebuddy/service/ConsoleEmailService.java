package com.queuebuddy.queuebuddy.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ConsoleEmailService implements EmailService {

    @Override
    public void sendVerificationEmail(String to, String name, String link) {
        log.info("\n--- VERIFICATION EMAIL (dev) ---\nTo: {}\nHi {}, verify your account (link expires in 15 minutes):\n{}\n--------------------------------",
                to, name, link);
    }
}