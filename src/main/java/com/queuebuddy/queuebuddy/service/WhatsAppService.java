package com.queuebuddy.queuebuddy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Component
public class WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String sid;
    private final String token;
    private final String from;

    public WhatsAppService(@Value("${app.whatsapp.account-sid:}") String sid,
                           @Value("${app.whatsapp.auth-token:}") String token,
                           @Value("${app.whatsapp.from:whatsapp:+14155238886}") String from) {
        this.sid = sid;
        this.token = token;
        this.from = from;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMessage(PatientMessageEvent event) {
        send(event.phone(), event.message());
    }

    private void send(String phone, String message) {
        if (phone == null || phone.isBlank()) return;
        String tail = phone.length() > 3 ? phone.substring(phone.length() - 3) : phone;

        if (sid.isBlank() || token.isBlank()) {
            log.info("WHATSAPP (not configured, not sent) to=***{} message={}", tail, message);
            return;
        }
        try {
            String to = "whatsapp:" + (phone.startsWith("+") ? phone : "+" + phone);
            String form = "From=" + enc(from) + "&To=" + enc(to) + "&Body=" + enc(message);
            String auth = Base64.getEncoder().encodeToString((sid + ":" + token).getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("https://api.twilio.com/2010-04-01/Accounts/" + sid + "/Messages.json"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.error("WhatsApp to ***{} failed: HTTP {} {}", tail, response.statusCode(), response.body());
            } else {
                log.info("WhatsApp sent to ***{}", tail);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("WhatsApp to ***{} failed: {}", tail, e.getMessage());
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}