package com.denizcelikhalat.katalog.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Düz metin e-postaları ARKA PLANDA (@Async) gönderir.
 * Çağıran tarafa (ör. checkout) yalnızca hazır String'ler geçilir; böylece bu sınıf
 * hiçbir JPA entity'sine dokunmaz ve ayrı thread'de LazyInitializationException olmaz.
 *
 * NOT: @Async'in çalışması için @EnableAsync gerekir (bkz. AsyncConfig).
 */
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    // Gönderen adres = SMTP'de kimlik doğrulanan Gmail hesabı (Gmail "from"u buna zorlar).
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
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            // Mail hatası siparişi ASLA bozmamalı: yut + logla.
            System.err.println("[EmailService] E-posta gonderilemedi -> " + to + " : " + e.getMessage());
        }
    }
}
