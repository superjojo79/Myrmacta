package be.myrmacta.infrastructure.web.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PrintPreviewHtmlTest {

    @Test
    void wrapsFragmentWithPageAndPrintPreviewCss() {
        String wrapped = PrintPreviewHtml.wrap("<p>Hello</p>");

        assertThat(wrapped).contains("<div class=\"page\"><p>Hello</p></div>");
        assertThat(wrapped).contains("width: 210mm");
        assertThat(wrapped).contains("min-height: 297mm");
        assertThat(wrapped).contains("@media screen");
    }

    @Test
    void injectsStyleIntoFullDocumentWithoutPageWrapper() {
        String source = "<!DOCTYPE html><html><head><title>t</title></head><body><p>Hi</p></body></html>";
        String wrapped = PrintPreviewHtml.wrap(source);

        assertThat(wrapped).contains("<style>");
        assertThat(wrapped).contains("width: 210mm");
        assertThat(wrapped).contains("<p>Hi</p>");
        assertThat(wrapped).doesNotContain("<div class=\"page\">");
    }

    @Test
    void createsHeadWhenFullDocumentHasNone() {
        String wrapped = PrintPreviewHtml.wrap("<html><body><p>x</p></body></html>");

        assertThat(wrapped).contains("<head>");
        assertThat(wrapped).contains("<style>");
        assertThat(wrapped).contains("<p>x</p>");
        assertThat(wrapped).doesNotContain("<div class=\"page\">");
    }
}
