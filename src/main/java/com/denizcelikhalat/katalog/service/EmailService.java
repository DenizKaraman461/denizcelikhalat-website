package com.denizcelikhalat.katalog.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendPlainText(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            return;
        }

        String[] recipients = Arrays.stream(to.split("[,;]"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toArray(String[]::new);

        if (recipients.length == 0) {
            return;
        }

        // Her alıcıya AYRI mail gönder: bir adres reddedilse/başarısız olsa bile
        // diğerleri etkilenmesin (tek setTo[] yerine alıcı başına gönderim).
        for (String recipient : recipients) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromAddress);
                message.setTo(recipient);
                message.setSubject(subject);
                message.setText(body);
                mailSender.send(message);
            } catch (Exception e) {
                System.err.println("[EmailService] E-posta gonderilemedi -> " + recipient + " : " + e.getMessage());
            }
        }
    }
}