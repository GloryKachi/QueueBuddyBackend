package com.queuebuddy.queuebuddy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TimeConfig {

    @Bean
    Clock clock(@Value("${app.timezone:Africa/Lagos}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}