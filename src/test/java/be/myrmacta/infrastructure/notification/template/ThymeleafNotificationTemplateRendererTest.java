package be.myrmacta.infrastructure.notification.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.document.model.TemplateProcessingException;
import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.model.RenderedNotification;
import be.myrmacta.infrastructure.config.NotificationTemplateProperties;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class ThymeleafNotificationTemplateRendererTest {

    private final ThymeleafNotificationTemplateRenderer renderer =
            new ThymeleafNotificationTemplateRenderer(NotificationTemplateProperties.defaults());

    private static NotificationTemplate template(String subject, String textBody, String htmlBody) {
        Instant now = Instant.parse("2026-11-07T10:00:00Z");
        return new NotificationTemplate(
                UUID.randomUUID(), "accuse-reception", null, "fr", "Accusé", 1, TemplateStatus.ACTIVE, subject, textBody,
                htmlBody, "tenant_dev", now, now, "test");
    }

    private static Map<String, Object> model(String reference, String label) {
        Map<String, Object> dossier = new HashMap<>();
        dossier.put("reference", reference);
        dossier.put("label", label);
        dossier.put("status", "OPEN");
        dossier.put("fields", new HashMap<>(Map.of("objet", "Permis", "montant", 12)));
        Map<String, Object> variables = new HashMap<>();
        variables.put("motivation", "Pièce manquante");
        variables.put("items", List.of("a", "b", "c"));
        return new HashMap<>(Map.of("dossier", dossier, "variables", variables));
    }

    @Test
    void rendersSubjectTextAndHtml_withTheDossierAndVariables() {
        RenderedNotification result = renderer.render(
                template(
                        "Accusé de réception : [[${dossier.fields.objet}]]",
                        "Dossier [[${dossier.reference}]]\nMotif : [[${variables.motivation}]]",
                        "<p>Dossier <b th:text=\"${dossier.reference}\">REF</b></p>"),
                model("DOS-2026-001", "Mon permis"));

        assertThat(result.subject()).isEqualTo("Accusé de réception : Permis");
        assertThat(result.textBody()).isEqualTo("Dossier DOS-2026-001\nMotif : Pièce manquante");
        assertThat(result.htmlBody()).contains("<b>DOS-2026-001</b>");
    }

    @Test
    void withoutHtmlBody_theHtmlPartIsNull() {
        RenderedNotification result = renderer.render(template("S", "T", null), model("R", "L"));

        assertThat(result.htmlBody()).isNull();
    }

    @Test
    void textPartsDoNotKeepHtmlEntities() {
        // Thymeleaf échappe [[...]] en entités HTML même en mode TEXT : le rendu les défait pour le texte brut.
        Map<String, Object> model = model("R", "Dupont & Fils <SA> l'été \"x\"");

        RenderedNotification result = renderer.render(
                template("Objet : [[${dossier.label}]]", "Pour [[${dossier.label}]]", null), model);

        assertThat(result.subject()).isEqualTo("Objet : Dupont & Fils <SA> l'été \"x\"");
        assertThat(result.textBody()).isEqualTo("Pour Dupont & Fils <SA> l'été \"x\"");
    }

    @Test
    void aValueThatLooksLikeAnEntity_isRenderedAsTyped() {
        RenderedNotification result = renderer.render(
                template("S", "[[${dossier.label}]]", null), model("R", "a &amp; b &lt; c &#39;"));

        assertThat(result.textBody()).isEqualTo("a &amp; b &lt; c &#39;");
    }

    @Test
    void htmlPart_escapesValues() {
        RenderedNotification result = renderer.render(
                template("S", "T", "<p th:text=\"${dossier.label}\">x</p>"), model("R", "<script>alert(1)</script> & co"));

        assertThat(result.htmlBody()).contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; co").doesNotContain("<script");
    }

    @Test
    void htmlPart_isCleanedAgain_evenIfTheTemplateWasNotChecked() {
        RenderedNotification result = renderer.render(
                template(
                        "S",
                        "T",
                        "<p onclick=\"x()\" th:text=\"${dossier.reference}\">x</p><img src=\"https://t.example/p.gif\">"
                                + "<a href=\"javascript:alert(1)\">l</a>"),
                model("R1", "L"));

        assertThat(result.htmlBody()).contains(">R1</p>").doesNotContain("onclick", "<img", "javascript:");
    }

    @Test
    void htmlPart_doesNotRunUtextOrFragments() {
        RenderedNotification result = renderer.render(
                template("S", "T", "<p th:utext=\"${dossier.label}\">x</p><div th:replace=\"${dossier.label}\">y</div>"),
                model("R", "<b>RAW</b>"));

        assertThat(result.htmlBody()).doesNotContain("<b>RAW</b>");
    }

    @Test
    void conditionalsAndLoops_workInBothModes() {
        RenderedNotification result = renderer.render(
                template(
                        "S",
                        "[# th:each=\"i : ${variables.items}\"]- [[${i}]]\n[/][# th:if=\"${dossier.status} == 'OPEN'\"]x[/]",
                        "<ul><li th:each=\"i : ${variables.items}\" th:text=\"${i}\">x</li></ul>"),
                model("R", "L"));

        assertThat(result.textBody()).isEqualTo("- a\n- b\n- c\nx");
        assertThat(result.htmlBody()).contains("<li>a</li><li>b</li><li>c</li>");
    }

    @Test
    void subject_isASingleLine() {
        Map<String, Object> model = model("R", "ligne1\r\nligne2\nBcc: evil@example.org");

        RenderedNotification result = renderer.render(
                template("  A\r\n\tB   [[${dossier.label}]]\u0007 ", "T", null), model);

        assertThat(result.subject()).isEqualTo("A B ligne1 ligne2 Bcc: evil@example.org");
        assertThat(result.subject()).doesNotContain("\r", "\n", "\t", "\u0007");
    }

    @Test
    void subject_emptyAfterRendering_isRefused() {
        assertThatThrownBy(() -> renderer.render(template(" [[${dossier.label}]] ", "T", null), model("R", " \r\n ")))
                .isInstanceOf(TemplateProcessingException.class)
                .hasMessageContaining("subject")
                .hasMessageContaining("empty");
    }

    @Test
    void subject_longerThanTheLimit_isRefused() {
        assertThatThrownBy(() -> renderer.render(template("[[${dossier.label}]]", "T", null), model("R", "x".repeat(256))))
                .isInstanceOf(TemplateProcessingException.class)
                .hasMessageContaining("subject")
                .hasMessageContaining("255");
        assertThat(renderer.render(template("[[${dossier.label}]]", "T", null), model("R", "x".repeat(255))).subject())
                .hasSize(255);
    }

    @Test
    void anAbsentProperty_failsTheRendering_namingTheTemplateThePartAndTheExpression() {
        assertThatThrownBy(() -> renderer.render(
                        template("S", "Bonjour [[${dossier.fields.absent}]]", null), model("R", "L")))
                .isInstanceOf(TemplateProcessingException.class)
                .hasMessageContaining("accuse-reception")
                .hasMessageContaining("textBody")
                .hasMessageContaining("dossier.fields.absent");
    }

    @Test
    void anAbsentVariable_failsEachPart() {
        assertThatThrownBy(() -> renderer.render(template("[[${nope}]]", "T", null), model("R", "L")))
                .isInstanceOf(TemplateProcessingException.class)
                .hasMessageContaining("subject")
                .hasMessageContaining("nope");
        assertThatThrownBy(() -> renderer.render(
                        template("S", "T", "<p th:text=\"${variables.absent}\">x</p>"), model("R", "L")))
                .isInstanceOf(TemplateProcessingException.class)
                .hasMessageContaining("htmlBody")
                .hasMessageContaining("variables.absent");
    }

    @Test
    void aNullProperty_failsTheRendering() {
        Map<String, Object> model = model("R", null);

        assertThatThrownBy(() -> renderer.render(template("S", "[[${dossier.label}]]", null), model))
                .isInstanceOf(TemplateProcessingException.class)
                .hasMessageContaining("dossier.label");
    }

    @Test
    void aSensitiveFieldAbsentFromTheModel_failsLikeAnyAbsentProperty() {
        // Le modèle n'expose que les champs déclarés non sensibles : un champ sensible est une propriété absente.
        assertThatThrownBy(() -> renderer.render(
                        template("S", "NRN : [[${dossier.fields.nrn}]]", null), model("R", "L")))
                .isInstanceOf(TemplateProcessingException.class)
                .hasMessageContaining("dossier.fields.nrn");
    }

    @Test
    void errorMessages_neverCarryAValue() {
        Map<String, Object> model = model("R", "SECRET-VALUE-42");

        assertThatThrownBy(() -> renderer.render(
                        template("S", "[[${#numbers.formatInteger(dossier.label, 2)}]] [[${dossier.fields.absent}]]", null),
                        model))
                .isInstanceOf(TemplateProcessingException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("SECRET-VALUE-42"));
        assertThatThrownBy(() -> renderer.render(
                        template("S", "T", "<p th:each=\"c : ${dossier.label}\" th:text=\"${#lists.size(c)}\">x</p>"), model))
                .isInstanceOf(TemplateProcessingException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("SECRET-VALUE-42"));
    }

    @Test
    void anUnsupportedExpression_failsTheRendering() {
        assertThatThrownBy(() -> renderer.render(
                        template("S", "[[${T(java.lang.Runtime).getRuntime()}]]", null), model("R", "L")))
                .isInstanceOf(TemplateProcessingException.class);
    }

    @Test
    void theRenderedTotal_isBounded() {
        ThymeleafNotificationTemplateRenderer small =
                new ThymeleafNotificationTemplateRenderer(new NotificationTemplateProperties(0, 0, 0, 0, 100, 0, 0, 0));
        NotificationTemplate big =
                template("S", "[# th:each=\"i : ${variables.items}\"]" + "0123456789".repeat(5) + "[/]", null);

        assertThatThrownBy(() -> small.render(big, model("R", "L")))
                .isInstanceOf(TemplateProcessingException.class)
                .hasMessageContaining("100");
        assertThat(small.render(template("S", "T", null), model("R", "L")).textBody()).isEqualTo("T");
    }

    @Test
    void aTemplateWithoutAnyModel_rendersItsLiterals() {
        RenderedNotification result = renderer.render(template("Bonjour", "Texte", "<p>Texte</p>"), Map.of());

        assertThat(result.subject()).isEqualTo("Bonjour");
        assertThat(result.textBody()).isEqualTo("Texte");
        assertThat(result.htmlBody()).contains("<p>Texte</p>");
    }

    private static final String BIG_LOOP = "[# th:each=\"i : ${#numbers.sequence(1,4000)}\"]"
            + "[# th:each=\"j : ${#numbers.sequence(1,4000)}\"]xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx[/][/]";

    @Test
    void aHugeLoopNest_failsAsATemplateProcessingException_quickly_withoutOutOfMemory() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThatThrownBy(
                        () -> renderer.render(template("S", BIG_LOOP, null), model("R", "L")))
                .isInstanceOf(TemplateProcessingException.class));
    }

    @Test
    void theOutputIsBoundedWhileRendering_notAfterwards() {
        ThymeleafNotificationTemplateRenderer small =
                new ThymeleafNotificationTemplateRenderer(new NotificationTemplateProperties(0, 0, 0, 0, 1000, 0, 0, 0));
        NotificationTemplate big = template(
                "S", "[# th:each=\"i : ${#numbers.sequence(1,1000)}\"]xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx[/]", null);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThatThrownBy(
                        () -> small.render(big, model("R", "L")))
                .isInstanceOf(TemplateProcessingException.class)
                .hasMessageContaining("1000"));
    }

    @Test
    void theTotalOfTheParts_counts() {
        ThymeleafNotificationTemplateRenderer small =
                new ThymeleafNotificationTemplateRenderer(new NotificationTemplateProperties(0, 0, 0, 0, 100, 0, 0, 0));
        String sixty = "0123456789".repeat(6);

        assertThatThrownBy(() -> small.render(template(sixty, sixty, null), model("R", "L")))
                .isInstanceOf(TemplateProcessingException.class);
    }

    @Test
    void hugeUtilityArguments_areRefused_withoutAValueInTheMessage() {
        for (String expression : List.of(
                "${#numbers.sequence(1,2000000000)}",
                "${#strings.repeat('a',2000000000)}",
                "${#strings.repeat('abcdefghij',500)}",
                "${#strings.randomAlphanumeric(10)}",
                "${#numbers.formatInteger(1,2000000000)}")) {
            String body = "[# th:each=\"i : " + expression + "\"]x[/]";
            assertThatThrownBy(() -> renderer.render(template("S", "[[" + expression + "]]" + body, null), model("R", "L")))
                    .as(expression)
                    .isInstanceOf(TemplateProcessingException.class)
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain("2000000000"));
        }
    }

    @Test
    void nestedEmptyLoops_failByBudget() {
        String nest = "[# th:each=\"a : ${#numbers.sequence(1,1000)}\"][# th:each=\"b : ${#numbers.sequence(1,1000)}\"]"
                + "[# th:each=\"c : ${#numbers.sequence(1,1000)}\"][/][/][/]";

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThatThrownBy(
                        () -> renderer.render(template("S", nest, null), model("R", "L")))
                .isInstanceOf(TemplateProcessingException.class)
                .hasMessageContaining("budget"));
    }

    @Test
    void normalBoundedUtilitiesStillRender() {
        RenderedNotification result = renderer.render(
                template(
                        "S",
                        "[# th:each=\"i : ${#numbers.sequence(1,3)}\"][[${i}]][/] [[${#strings.repeat('ab',3)}]]",
                        null),
                model("R", "L"));

        assertThat(result.textBody()).isEqualTo("123 ababab");
    }

    @Test
    void preProcessing_isRefusedAtRendering_andNeverLeaksTheValue() {
        Map<String, Object> model = model("R", "L");
        model.put("a", "${variables.motivation}");

        for (NotificationTemplate t : List.of(
                template("S", "[[__${a}__]]", null),
                template("S", "T", "<p th:text=\"__${a}__\">x</p>"),
                template("__${a}__", "T", null))) {
            assertThatThrownBy(() -> renderer.render(t, model))
                    .isInstanceOf(TemplateProcessingException.class)
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain("Pièce manquante"));
        }
    }
}
