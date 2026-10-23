package com.flowforge.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.notification.port.out.EmailPort;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
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

    /** Issue #183 — multipart/mixed : le texte, puis le PDF sous le nom choisi ; l'en-tête anti-boucle est conservé. */
    @Test
    void send_withAttachment_buildsAMultipartMessageWithTheNamedPdf() throws Exception {
        byte[] pdf = "%PDF-1.7 contenu".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        adapter.send("user@example.com", "Décision", "Ci-joint votre autorisation.",
                new EmailPort.EmailAttachmentContent("Décision riverain.pdf", "application/pdf", pdf));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = new MimeMessage(Session.getInstance(new Properties()), toStream(captor.getValue()));
        assertThat(sent.getHeader("Auto-Submitted")).containsExactly("auto-generated");
        assertThat(sent.getContentType()).startsWith("multipart/mixed");
        List<BodyPart> parts = leafParts((Multipart) sent.getContent());
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).getContent()).isEqualTo("Ci-joint votre autorisation.");
        BodyPart attachment = parts.get(1);
        assertThat(attachment.getDisposition()).isEqualTo(Part.ATTACHMENT);
        assertThat(MimeUtility.decodeText(attachment.getFileName())).isEqualTo("Décision riverain.pdf");
        assertThat(attachment.getContentType()).startsWith("application/pdf");
        assertThat(attachment.getInputStream().readAllBytes()).isEqualTo(pdf);
    }

    private static java.io.ByteArrayInputStream toStream(MimeMessage message) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        message.writeTo(out);
        return new java.io.ByteArrayInputStream(out.toByteArray());
    }

    /** Parties feuilles (MimeMessageHelper imbrique le texte dans un multipart/related). */
    private static List<BodyPart> leafParts(Multipart multipart) throws Exception {
        List<BodyPart> leaves = new ArrayList<>();
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            if (part.getContent() instanceof Multipart nested) {
                leaves.addAll(leafParts(nested));
            } else {
                leaves.add(part);
            }
        }
        return leaves;
    }

    @Test
    void send_propagatesTransportFailure() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> adapter.send("user@example.com", "Hello", "Body"))
                .isInstanceOf(MailSendException.class)
                .hasMessageContaining("smtp down");
    }
}
