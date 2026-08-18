package com.flowforge.domain.document.port.in;

import java.util.Map;

public interface PreviewDocumentUseCase {

    String previewHtml(PreviewCommand command);

    byte[] previewPdf(PreviewCommand command);

    record PreviewCommand(
            String htmlContent,
            String templateKey,
            String variantKey,
            String locale,
            Map<String, Object> variables) {

        public boolean isLive() {
            return htmlContent != null && !htmlContent.isBlank();
        }
    }
}
