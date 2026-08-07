package com.flowforge.infrastructure.notification;

import com.flowforge.domain.notification.port.out.EmailPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailAdapter implements EmailPort {

    private final JavaMailSender mailSender;

    public SmtpEmailAdapter(JavaMailSender mailSender) {
        this.mailSender = Objects.requireNonNull(mailSender, "mailSender");
    }

    @Override
    public void send(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
        } catch (MessagingException ex) {
            throw new IllegalStateException("Failed to build email message", ex);
        }
    }
}
