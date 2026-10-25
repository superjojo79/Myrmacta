package be.myrmacta.domain.document.port.out;

import be.myrmacta.domain.document.model.DocumentRenderException;

@FunctionalInterface
public interface PdfRenderer {

    /**
     * Renders HTML (HTML5 accepted; adapter may convert to XHTML) into PDF bytes.
     *
     * @throws DocumentRenderException when rendering fails
     */
    byte[] render(String html) throws DocumentRenderException;
}
