package com.flowforge.infrastructure.pdf;

import java.io.InputStream;
import java.net.URL;
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
 *
 * <p>D-PDF-UA-DEFAULT-CSS (TODO §98) — seule exception : la feuille de style par défaut de Flying Saucer ({@code head} masqué, tableaux, titres…), que le
 * moteur charge lui-même par son URL de classpath ({@code jar:…!/resources/css/XhtmlNamespaceHandler.css}). Bloquée, elle
 * rendait le titre et la feuille de style du modèle comme du texte et perdait toute mise en page (recette de l'exemple
 * riverain, 2026-10-23). L'URL est comparée à l'identique : aucune autre ressource du classpath n'est lisible.
 */
class RestrictedResourceUserAgent extends ITextUserAgent {

    /** Même chemin que {@code XhtmlCssOnlyNamespaceHandler} (clé {@code xr.css.user-agent-default-css}, valeur par défaut). */
    private static final URL DEFAULT_STYLESHEET =
            ITextRenderer.class.getResource("/resources/css/XhtmlNamespaceHandler.css");

    RestrictedResourceUserAgent(ITextOutputDevice outputDevice) {
        super(outputDevice, ITextRenderer.DEFAULT_DOTS_PER_PIXEL);
    }

    @Override
    protected InputStream resolveAndOpenStream(String uri) {
        if (uri != null && uri.toLowerCase(Locale.ROOT).startsWith("data:")) {
            return super.resolveAndOpenStream(uri);
        }
        if (uri != null && DEFAULT_STYLESHEET != null && uri.equals(DEFAULT_STYLESHEET.toString())) {
            return super.resolveAndOpenStream(uri);
        }
        return null;
    }
}
