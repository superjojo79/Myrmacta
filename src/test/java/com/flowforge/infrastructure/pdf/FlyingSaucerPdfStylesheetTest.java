package com.flowforge.infrastructure.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

/**
 * Recette de l'exemple riverain (2026-10-23) — le contenu de {@code <head>} (titre, feuille de style) ne doit jamais être
 * rendu comme texte, et la feuille de style doit s'appliquer, quelle que soit la balise racine écrite par l'auteur du modèle.
 */
class FlyingSaucerPdfStylesheetTest {

    private final FlyingSaucerPdfAdapter adapter = new FlyingSaucerPdfAdapter(new SimpleMeterRegistry());

    @ParameterizedTest
    @ValueSource(strings = {
            "<html>",
            "<html xmlns:th=\"http://www.thymeleaf.org\">",
            "<html xmlns=\"http://www.w3.org/1999/xhtml\">"})
    void headContentIsNeverRenderedAsText(String root) throws IOException {
        String text = text(adapter.render(root + """
                <head><meta charset="UTF-8"/><title>Titre du document</title>
                <style> .masque { display: none; } </style></head>
                <body><p>Visible</p><p class="masque">Caché</p></body></html>
                """));
        assertThat(text).contains("Visible").doesNotContain("Titre du document").doesNotContain("display")
                .doesNotContain("Caché");
    }

    @Test
    void exampleTemplateHeadIsNotRenderedAsText() throws IOException {
        String html = java.nio.file.Files.readString(
                java.nio.file.Path.of("exemples/autorisation-riverain/template-decision.html"));
        String text = text(adapter.render(html.replaceAll("th:[a-z]+=\"[^\"]*\"", "")));
        assertThat(text).doesNotContain("font-family").doesNotContain("Décision — autorisation riverain");
    }

    private static String text(byte[] pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        try {
            StringBuilder out = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                out.append(new PdfTextExtractor(reader).getTextFromPage(page));
            }
            return out.toString();
        } finally {
            reader.close();
        }
    }
}
