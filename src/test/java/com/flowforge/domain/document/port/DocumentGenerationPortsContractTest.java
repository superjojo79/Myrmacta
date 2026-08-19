package com.flowforge.domain.document.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.document.model.DocumentRenderException;
import com.flowforge.domain.document.model.DocumentStorageException;
import com.flowforge.domain.document.model.TemplateProcessingException;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.document.port.out.HtmlTemplateProcessor;
import com.flowforge.domain.document.port.out.PdfRenderer;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentGenerationPortsContractTest {

    @Test
    void pdfRenderer_htmlTemplateProcessor_and_storagePort_areCallable() {
        PdfRenderer pdfRenderer = html -> new byte[] {'%', 'P', 'D', 'F'};
        HtmlTemplateProcessor processor = (html, variables) -> html.replace("${title}", String.valueOf(variables.get("title")));
        DocumentStoragePort storage = new DocumentStoragePort() {
            private byte[] last;

            @Override
            public String store(String tenantId, String contentType, byte[] content) {
                last = content;
                return tenantId + "/doc.pdf";
            }

            @Override
            public byte[] retrieve(String tenantId, String storageRef) {
                return last;
            }

            @Override
            public boolean exists(String tenantId, String storageRef) {
                return last != null;
            }

            @Override
            public void delete(String tenantId, String storageRef) {
                last = null;
            }
        };

        byte[] pdf = pdfRenderer.render("<html/>");
        String html = processor.process("<span>${title}</span>", Map.of("title", "Hi"));
        String ref = storage.store("tenant_dev", "application/pdf", pdf);

        assertThat(pdf).startsWith("%PDF".getBytes());
        assertThat(html).contains("Hi");
        assertThat(ref).isEqualTo("tenant_dev/doc.pdf");
        assertThat(storage.exists("tenant_dev", ref)).isTrue();
        assertThat(storage.retrieve("tenant_dev", ref)).isEqualTo(pdf);

        storage.delete("tenant_dev", ref);
        assertThat(storage.exists("tenant_dev", ref)).isFalse();
        storage.delete("tenant_dev", "tenant_dev/already-gone.pdf");
    }

    @Test
    void domainExceptions_exposeMessages() {
        assertThatThrownBy(() -> {
                    throw new DocumentRenderException("render failed");
                })
                .isInstanceOf(DocumentRenderException.class)
                .hasMessageContaining("render failed");
        assertThatThrownBy(() -> {
                    throw new TemplateProcessingException("bad thymeleaf", null);
                })
                .isInstanceOf(TemplateProcessingException.class)
                .hasMessageContaining("bad thymeleaf");
        assertThatThrownBy(() -> {
                    throw new DocumentStorageException("missing object");
                })
                .isInstanceOf(DocumentStorageException.class)
                .hasMessageContaining("missing object");
    }
}
