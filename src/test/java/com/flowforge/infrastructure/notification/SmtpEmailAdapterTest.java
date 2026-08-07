package com.flowforge.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpEmailAdapterTest {

    private JavaMailSender mailSender;
    private MimeMessage mimeMessage;
    private SmtpEmailAdapter adapter;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        adapter = new SmtpEmailAdapter(mailSender);
    }

    @Test
    void send_usesTextPlainWithToSubjectBody() throws Exception {
        adapter.send("user@example.com", "Hello", "Body text");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("user@example.com");
        assertThat(sent.getSubject()).isEqualTo("Hello");
        assertThat(sent.getContent()).isEqualTo("Body text");
        assertThat(sent.getContentType()).contains("text/plain");
    }

    @Test
    void send_propagatesTransportFailure() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> adapter.send("user@example.com", "Hello", "Body"))
                .isInstanceOf(MailSendException.class)
                .hasMessageContaining("smtp down");
    }
}
