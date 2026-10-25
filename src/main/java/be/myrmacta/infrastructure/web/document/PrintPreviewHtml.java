package be.myrmacta.infrastructure.web.document;

final class PrintPreviewHtml {

    static final String CSS =
            """
            @media screen {
                body    { background: #e0e0e0; padding: 20px; }
                .page   { background: white; width: 210mm; min-height: 297mm;
                          margin: 0 auto 20px; padding: 20mm 15mm;
                          box-shadow: 0 2px 8px rgba(0,0,0,0.2); }
            }
            """;

    private PrintPreviewHtml() {}

    static String wrap(String html) {
        String source = html == null ? "" : html;
        String leading = source.stripLeading();
        if (isFullDocument(leading)) {
            return injectStyle(source);
        }
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
                + CSS
                + "</style></head><body><div class=\"page\">"
                + source
                + "</div></body></html>";
    }

    private static boolean isFullDocument(String leading) {
        return startsIgnoreCase(leading, "<!DOCTYPE") || startsIgnoreCase(leading, "<html");
    }

    private static boolean startsIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static String injectStyle(String html) {
        String style = "<style>" + CSS + "</style>";
        int headClose = indexOfIgnoreCase(html, "</head>");
        if (headClose >= 0) {
            return html.substring(0, headClose) + style + html.substring(headClose);
        }
        int htmlOpenEnd = endOfOpeningTag(html, "html");
        if (htmlOpenEnd >= 0) {
            return html.substring(0, htmlOpenEnd) + "<head>" + style + "</head>" + html.substring(htmlOpenEnd);
        }
        return "<head>" + style + "</head>" + html;
    }

    private static int endOfOpeningTag(String html, String tag) {
        int start = indexOfIgnoreCase(html, "<" + tag);
        if (start < 0) {
            return -1;
        }
        int end = html.indexOf('>', start);
        return end < 0 ? -1 : end + 1;
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase().indexOf(needle.toLowerCase());
    }
}
