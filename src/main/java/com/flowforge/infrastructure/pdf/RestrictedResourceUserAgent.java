package com.flowforge.infrastructure.pdf;

import java.io.InputStream;
import java.util.Locale;
import org.xhtmlrenderer.pdf.ITextOutputDevice;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.pdf.ITextUserAgent;

/**
 * Blocks network and filesystem resource fetches (images, stylesheets, XML) during PDF
 * rendering. Flying Saucer's default user agent happily follows {@code http(s)://} and
 * {@code file://} URIs found in the rendered HTML — since that HTML is an admin-authored
 * template merged with caller-supplied variables (any {@code DOSSIER_MANAGER} can call
 * {@code POST /api/documents/generate}), an {@code <img src="${variable}">}-style binding
 * would let a caller turn PDF generation into SSRF or local file disclosure. Only
 * self-contained {@code data:} URIs are resolved; everything else is treated as
 * unavailable (Flying Saucer already handles fetch failures gracefully — broken
 * image/missing stylesheet — since that is also the normal outcome of a network error).
 */
class RestrictedResourceUserAgent extends ITextUserAgent {

    RestrictedResourceUserAgent(ITextOutputDevice outputDevice) {
        super(outputDevice, ITextRenderer.DEFAULT_DOTS_PER_PIXEL);
    }

    @Override
    protected InputStream resolveAndOpenStream(String uri) {
        if (uri != null && uri.toLowerCase(Locale.ROOT).startsWith("data:")) {
            return super.resolveAndOpenStream(uri);
        }
        return null;
    }
}
