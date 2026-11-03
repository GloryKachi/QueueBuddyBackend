package com.queuebuddy.queuebuddy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class MailClient {

    private static final Logger log = LoggerFactory.getLogger(MailClient.class);

    // Google answers with a redirect, so redirects must be followed.
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String scriptUrl;
    private final String secret;
    private final String fromName;

    public MailClient(@Value("${app.mail.script-url:}") String scriptUrl,
                      @Value("${app.mail.secret:}") String secret,
                      @Value("${app.mail.from-name:QueueBuddy}") String fromName) {
        this.scriptUrl = scriptUrl;
        this.secret = secret;
        this.fromName = fromName;
    }

    /** Sends an email. Never throws, so a mail problem can't break sign-up. */
    public void send(String toEmail, String subject, String html) {
        if (scriptUrl.isBlank() || secret.isBlank()) {
            log.info("MAIL (mailer not configured, not sent) to={} subject={}\n{}", toEmail, subject, html);
            return;
        }
        try {
            String body = "{\"secret\":" + q(secret)
                    + ",\"to\":" + q(toEmail)
                    + ",\"subject\":" + q(subject)
                    + ",\"html\":" + q(html)
                    + ",\"fromName\":" + q(fromName) + "}";

            HttpRequest request = HttpRequest.newBuilder(URI.create(scriptUrl))
                    .timeout(Duration.ofSeconds(25))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 300 || !response.body().contains("\"ok\":true")) {
                log.error("Mailer rejected email to {}: HTTP {} {}", toEmail, response.statusCode(), response.body());
                log.info("MAIL fallback to={} subject={}\n{}", toEmail, subject, html);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while sending email to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
            log.info("MAIL fallback to={} subject={}\n{}", toEmail, subject, html);
        }
    }

    /** Wraps a string as a JSON string literal. */
    private static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}