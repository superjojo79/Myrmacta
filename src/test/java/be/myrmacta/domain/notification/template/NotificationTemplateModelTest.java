package be.myrmacta.domain.notification.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.notification.template.model.NotificationTemplateLimits;
import be.myrmacta.domain.notification.template.model.NotificationTemplateValidationException;
import be.myrmacta.domain.notification.template.model.RenderedNotification;
import org.junit.jupiter.api.Test;

class NotificationTemplateModelTest {

    @Test
    void limits_mustAllBePositive() {
        assertThatCode(() -> new NotificationTemplateLimits(1, 1, 1)).doesNotThrowAnyException();
        assertThatThrownBy(() -> new NotificationTemplateLimits(0, 1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limits must be positive");
        assertThatThrownBy(() -> new NotificationTemplateLimits(1, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationTemplateLimits(1, 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationTemplateLimits(-1, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationTemplateLimits(1, -1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationTemplateLimits(1, 1, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void renderedNotification_toString_countsButNeverPrintsTheContent() {
        assertThat(new RenderedNotification("Objet", "Corps", "<p>SECRET</p>").toString())
                .isEqualTo("RenderedNotification[subjectChars=5, textChars=5, html=true]");
        assertThat(new RenderedNotification("Obj", "Corps du message", null).toString())
                .isEqualTo("RenderedNotification[subjectChars=3, textChars=16, html=false]");
    }

    @Test
    void renderedNotification_requiresASubjectAndATextBody() {
        assertThatThrownBy(() -> new RenderedNotification(null, "t", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RenderedNotification("s", null, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void label_isBoundedTo200Characters() {
        assertThat(NotificationTemplateSupport.optionalLabel("a".repeat(200))).hasSize(200);
        assertThatThrownBy(() -> NotificationTemplateSupport.optionalLabel("a".repeat(201)))
                .isInstanceOfSatisfying(NotificationTemplateValidationException.class, e -> {
                    assertThat(e.part()).isEqualTo("label");
                    assertThat(e.getMessage()).contains("exceeds 200 characters");
                });
        assertThat(NotificationTemplateSupport.optionalLabel("  ")).isNull();
    }

    @Test
    void locale_acceptsShortTagsOnly() {
        assertThat(NotificationTemplateSupport.optionalLocale(" fr-BE ")).isEqualTo("fr-BE");
        assertThat(NotificationTemplateSupport.optionalLocale("nld")).isEqualTo("nld");
        assertThat(NotificationTemplateSupport.optionalLocale(null)).isNull();
        assertThatThrownBy(() -> NotificationTemplateSupport.optionalLocale("f"))
                .isInstanceOf(NotificationTemplateValidationException.class);
        assertThatThrownBy(() -> NotificationTemplateSupport.optionalLocale("fr_BE"))
                .isInstanceOf(NotificationTemplateValidationException.class);
        assertThatThrownBy(() -> NotificationTemplateSupport.optionalLocale("fr-" + "a".repeat(40)))
                .isInstanceOf(NotificationTemplateValidationException.class);
    }
}
