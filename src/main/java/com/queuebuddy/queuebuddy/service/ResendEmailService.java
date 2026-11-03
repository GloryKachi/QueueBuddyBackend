package com.queuebuddy.queuebuddy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
public class ResendEmailService implements EmailService {

    private final MailClient mail;

    @Override
    public void sendVerificationEmail(String to, String name, String link) {
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;padding:24px;color:#12263F">
              <h2 style="color:#1565C0;margin:0 0 16px">QueueBuddy</h2>
              <h3 style="margin:0 0 12px">Verify your email</h3>
              <p style="line-height:1.5">Hi %s, welcome to QueueBuddy. Confirm your email address to finish setting up your account.</p>
              <p style="margin:24px 0">
                <a href="%s" style="background:#1565C0;color:#fff;padding:12px 24px;border-radius:999px;text-decoration:none;font-weight:bold">Verify email</a>
              </p>
              <p style="font-size:13px;color:#5A6B7F">If the button doesn't work, copy this link into your browser:<br>%s</p>
              <p style="font-size:13px;color:#5A6B7F">This link works once and expires in 15 minutes. If you didn't sign up, you can ignore this email.</p>
            </div>
            """.formatted(HtmlUtils.htmlEscape(name), link, link);

        mail.send(to, "Verify your QueueBuddy email", html);
    }
}