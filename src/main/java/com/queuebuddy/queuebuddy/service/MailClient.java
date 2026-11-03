package com.queuebuddy.queuebuddy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class MailClient {

    private static final Logger log = LoggerFactory.getLogger(MailClient.class);

    private final RestClient http;
    private final String apiKey;
    private final String from;

    public MailClient(@Value("${app.mail.api-key:}") String apiKey,
                      @Value("${app.mail.from-email:onboarding@resend.dev}") String fromEmail,
                      @Value("${app.mail.from-name:QueueBuddy}") String fromName) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .requestFactory(factory)
                .build();
        this.apiKey = apiKey;
        this.from = fromName + " <" + fromEmail + ">";
    }

    /** Sends an email. Never throws, so a mail problem can't break sign-up. */
    public void send(String toEmail, String subject, String html) {
        if (apiKey.isBlank()) {
            log.info("MAIL (no API key set, not sent) to={} subject={}\n{}", toEmail, subject, html);
            return;
        }
        try {
            http.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "from", from,
                            "to", List.of(toEmail),
                            "subject", subject,
                            "html", html))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
            log.info("MAIL fallback to={} subject={}\n{}", toEmail, subject, html);
        }
    }
}