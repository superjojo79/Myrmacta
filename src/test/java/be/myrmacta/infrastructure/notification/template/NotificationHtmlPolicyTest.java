package be.myrmacta.infrastructure.notification.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NotificationHtmlPolicyTest {

    private static final String CLEAN = """
            <html><head><title>Accusé</title><meta charset="utf-8"></head>
            <body>
              <h1 class="t" style="color: #333; width:auto">Bonjour <span th:text="${dossier.reference}">REF</span></h1>
              <p th:if="${dossier.status}" th:with="x=${dossier.label}" align="left" title="t">Votre dossier</p>
              <ul><li th:each="v : ${variables.items}" th:text="${v}">item</li></ul>
              <th:block th:switch="${dossier.status}"><b th:case="'OPEN'">ouvert</b><i th:case="*">autre</i></th:block>
              <p th:remove="all">prototype</p>
              <table width="100%"><thead><tr><th colspan="2">T</th></tr></thead><tbody><tr><td rowspan="1">a</td><td>b</td></tr></tbody></table>
              <p><a href="https://exemple.be/suivi">suivi</a> <a href="http://exemple.be">http</a>
                 <a href="MAILTO:agent@exemple.be">mail</a></p>
              <pre>x</pre><blockquote>q</blockquote><hr><br>
            </body></html>
            """;

    @Test
    void aCleanTemplate_hasNoViolation() {
        assertThat(NotificationHtmlPolicy.violations(CLEAN)).isEmpty();
    }

    @Test
    void plainFragmentsAndComments_areAccepted() {
        assertThat(NotificationHtmlPolicy.violations("<p>Bonjour</p><!-- note -->")).isEmpty();
        assertThat(NotificationHtmlPolicy.violations("Texte [[${dossier.reference}]] seul")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "img", "script", "iframe", "object", "embed", "form", "input", "link", "base", "svg", "math", "style",
            "video", "audio", "button", "textarea", "select", "marquee", "custom-tag"})
    void aForbiddenTag_isRefused_byName(String tag) {
        List<String> violations = NotificationHtmlPolicy.violations("<p>ok</p><" + tag + ">x</" + tag + ">");

        assertThat(violations).anyMatch(v -> v.contains("<" + tag + ">") && v.contains("not allowed"));
    }

    @Test
    void anImageWithAnExternalSource_isRefused() {
        assertThat(NotificationHtmlPolicy.violations("<p>x</p>\n<img src=\"https://tracker.example/p.gif\">"))
                .anyMatch(v -> v.contains("<img>") && v.contains("line 2"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "onclick=\"alert(1)\"", "onerror=\"x\"", "ONMOUSEOVER=\"x\"", "id=\"a\"", "data-x=\"1\"", "src=\"x\"",
            "target=\"_blank\"", "rel=\"noopener\"", "name=\"a\"", "lang=\"fr\"", "dir=\"rtl\""})
    void aForbiddenAttribute_isRefused_byName(String attribute) {
        String name = attribute.substring(0, attribute.indexOf('=')).toLowerCase();

        assertThat(NotificationHtmlPolicy.violations("<p " + attribute + ">x</p>"))
                .anyMatch(v -> v.contains("attribute " + name) && v.contains("<p>"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "th:href=\"${x}\"", "th:attr=\"href=${x}\"", "th:style=\"${x}\"", "th:src=\"${x}\"", "th:utext=\"${x}\"",
            "th:inline=\"text\"", "th:replace=\"x\"", "th:insert=\"x\"", "th:include=\"x\"", "th:fragment=\"f\"",
            "th:classappend=\"${x}\"", "th:object=\"${x}\"", "th:onclick=\"${x}\"", "th:action=\"${x}\"",
            "TH:HREF=\"${x}\"", "data-th-href=\"${x}\"", "DATA-TH-ATTR=\"${x}\""})
    void aForbiddenThymeleafAttribute_isRefused(String attribute) {
        List<String> violations = NotificationHtmlPolicy.violations("<a " + attribute + ">x</a>");

        assertThat(violations).isNotEmpty().anyMatch(v -> v.toLowerCase().contains("th"));
    }

    @Test
    void aThymeleafAttributeHiddenBeyondTheDomView_isStillRefused() {
        // Écart d'analyse possible entre jsoup et le parseur de Thymeleaf : le texte brut est aussi balayé.
        assertThat(NotificationHtmlPolicy.violations("<p title=\"a\"th:href=\"${x}\">x</p>"))
                .anyMatch(v -> v.contains("th:href"));
        assertThat(NotificationHtmlPolicy.violations("<th:foo>x</th:foo>")).anyMatch(v -> v.contains("th:foo"));
    }

    @Test
    void thymeleafWordsInsideCssOrWords_areNotMistakenForAttributes() {
        assertThat(NotificationHtmlPolicy.violations("<p style=\"width:auto; max-width:50%\">longueur:5</p>"))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "background:url(https://x.example/a.png)", "background: URL( 'x' )", "width:expression(alert(1))",
            "behavior:EXPRESSION (x)", "@import 'x.css'", "background:url(javascript:alert(1))", "x:javascript:alert(1)",
            "background:\\75rl(x)", "color:red; u rl(x)", "background:&#x75;rl(x)"})
    void aStyleThatCanLoadOrRunSomething_isRefused(String style) {
        String attribute = style.contains("\"") ? "style='" + style + "'" : "style=\"" + style + "\"";

        assertThat(NotificationHtmlPolicy.violations("<p " + attribute + ">x</p>")).as(style).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)", "JaVaScRiPt:alert(1)", " javascript:alert(1)", "java\tscript:alert(1)",
            "data:text/html,<b>x</b>", "vbscript:x", "/relatif", "#ancre", "//hote.example/x", "ftp://x.example",
            "file:///etc/passwd", "tel:+3212345678", ""})
    void aLinkOutsideHttpHttpsMailto_isRefused(String href) {
        assertThat(NotificationHtmlPolicy.violations("<a href=\"" + href + "\">x</a>"))
                .as(href)
                .anyMatch(v -> v.contains("href"));
    }

    @Test
    void metaIsLimitedToCharset() {
        assertThat(NotificationHtmlPolicy.violations("<meta charset=\"utf-8\">")).isEmpty();
        assertThat(NotificationHtmlPolicy.violations("<meta http-equiv=\"refresh\" content=\"0;url=https://x\">"))
                .anyMatch(v -> v.contains("http-equiv"));
    }

    @Test
    void everyViolationIsReported_notOnlyTheFirst() {
        List<String> violations = NotificationHtmlPolicy.violations(
                "<p onclick=\"x\">a</p><img src=\"y\"><script>z</script>");

        assertThat(violations).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void violationMessages_neverEchoAnAttributeValue() {
        List<String> violations = NotificationHtmlPolicy.violations("<p onclick=\"SECRET-PAYLOAD\">a</p>");

        assertThat(violations).isNotEmpty().noneMatch(v -> v.contains("SECRET-PAYLOAD"));
    }

    @Test
    void clean_keepsAllowedMarkup_andDropsEverythingElse() {
        String cleaned = NotificationHtmlPolicy.clean(
                "<html><head><style>p{}</style><title>t</title></head><body>"
                        + "<p onclick=\"x\" class=\"c\" th:text=\"y\">Bonjour</p>"
                        + "<img src=\"https://t.example/p.gif\"><script>alert(1)</script>"
                        + "<a href=\"javascript:alert(1)\">piège</a><a href=\"https://ok.example\">ok</a>"
                        + "<p style=\"background:url(x)\">s</p><p style=\"color:red\">r</p>"
                        + "</body></html>");

        assertThat(cleaned)
                .contains("<p class=\"c\">Bonjour</p>")
                .contains("href=\"https://ok.example\"")
                .contains("style=\"color:red\"")
                .doesNotContain("onclick", "<img", "<script", "alert(1)", "javascript:", "url(", "<style", "th:text");
    }

    @Test
    void clean_doesNotReformatPreformattedText() {
        String cleaned = NotificationHtmlPolicy.clean("<pre>a\n  b</pre>");

        assertThat(cleaned).contains("<pre>a\n  b</pre>");
    }

    @Test
    void clean_ofAFragment_returnsADocument() {
        assertThat(NotificationHtmlPolicy.clean("<p>x</p>")).contains("<p>x</p>").contains("<body>");
    }
}
