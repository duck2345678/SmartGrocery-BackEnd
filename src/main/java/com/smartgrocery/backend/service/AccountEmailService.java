package com.smartgrocery.backend.service;

import com.smartgrocery.backend.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AccountEmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    public AccountEmailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    public void sendBanStatusEmail(User user, String reason, boolean active) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.info("Mail sender not configured. Skip ban email for user {}", user.getEmail());
            return;
        }

        String subject = active ? "Tai khoan da duoc mo khoa" : "Thong bao vo hieu hoa tai khoan";
        String body = active
                ? "Tai khoan cua ban da duoc kich hoat lai.\nLy do: " + safe(reason)
                : "Tai khoan cua ban da bi vo hieu hoa.\nLy do: " + safe(reason);

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(user.getEmail());
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("Could not send status email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private String safe(String s) {
        return s == null || s.isBlank() ? "N/A" : s.trim();
    }
}
