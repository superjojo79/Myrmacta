package be.myrmacta.domain.document.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.document.model.DocumentTemplate;
import be.myrmacta.domain.document.model.TemplateNotFoundException;
import be.myrmacta.domain.document.model.TemplateResolutionRequest;
import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.document.model.TemplateValidationException;
import be.myrmacta.domain.document.port.in.ResolveDocumentTemplateUseCase;
import be.myrmacta.domain.document.port.out.DocumentTemplateRepository;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Résolution en cascade des templates de document (docs/specs/08-templates-documents.md,
 * « Résolution en cascade ») — fiabilisation-tests-plateforme, trou confirmé en Phase 1
 * (aucun test dédié n'existait). La cascade elle-même est implémentée par le classement SQL de
 * {@code findBestActive} ({@code SpringDataDocumentTemplateRepository}), donc ce test passe par
 * le vrai repository JPA (PostgreSQL embarqué) plutôt que par un mock, pour exercer le classement réel : match
 * exact (variant+locale) > repli locale (variant seul) > repli variant (locale seule) > défaut
 * (ni variant ni locale) > {@link TemplateNotFoundException}.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class ResolveDocumentTemplateUseCaseImplTest {

    @Autowired
    private ResolveDocumentTemplateUseCase resolveTemplate;

    @Autowired
    private DocumentTemplateRepository repository;

    @Test
    void resolve_exactVariantAndLocaleMatch_winsOverEveryOtherCandidate() {
        String key = uniqueKey();
        DocumentTemplate exact = save(key, "email", "fr-FR");
        save(key, "email", null);
        save(key, null, "fr-FR");
        save(key, null, null);

        DocumentTemplate resolved = resolveTemplate.resolve(new TemplateResolutionRequest(key, "email", "fr-FR"));

        assertThat(resolved.id()).isEqualTo(exact.id());
    }

    @Test
    void resolve_noExactMatch_fallsBackToVariantWithNullLocale() {
        String key = uniqueKey();
        DocumentTemplate variantFallback = save(key, "email", null);
        save(key, null, "fr-FR");
        save(key, null, null);

        DocumentTemplate resolved = resolveTemplate.resolve(new TemplateResolutionRequest(key, "email", "fr-FR"));

        assertThat(resolved.id()).isEqualTo(variantFallback.id());
    }

    @Test
    void resolve_noVariantMatch_fallsBackToLocaleWithNullVariant() {
        String key = uniqueKey();
        DocumentTemplate localeFallback = save(key, null, "fr-FR");
        save(key, null, null);

        DocumentTemplate resolved = resolveTemplate.resolve(new TemplateResolutionRequest(key, "email", "fr-FR"));

        assertThat(resolved.id()).isEqualTo(localeFallback.id());
    }

    @Test
    void resolve_neitherVariantNorLocaleMatch_fallsBackToDefaultTemplate() {
        String key = uniqueKey();
        DocumentTemplate defaultTemplate = save(key, null, null);

        DocumentTemplate resolved = resolveTemplate.resolve(new TemplateResolutionRequest(key, "email", "fr-FR"));

        assertThat(resolved.id()).isEqualTo(defaultTemplate.id());
    }

    @Test
    void resolve_noActiveTemplateAtAll_throwsTemplateNotFound() {
        String key = uniqueKey();

        assertThatThrownBy(() -> resolveTemplate.resolve(new TemplateResolutionRequest(key, "email", "fr-FR")))
                .isInstanceOf(TemplateNotFoundException.class);
    }

    @Test
    void resolve_blankTemplateKey_throwsTemplateValidationException() {
        assertThatThrownBy(() -> resolveTemplate.resolve(new TemplateResolutionRequest(" ", null, null)))
                .isInstanceOf(TemplateValidationException.class);
    }

    private static String uniqueKey() {
        return "cascade-" + UUID.randomUUID();
    }

    private DocumentTemplate save(String templateKey, String variantKey, String locale) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        return repository.save(new DocumentTemplate(
                id,
                templateKey,
                variantKey,
                locale,
                "Label " + templateKey,
                1,
                TemplateStatus.ACTIVE,
                "<html><body>content</body></html>",
                "tenant_dev",
                now,
                now,
                "creator"));
    }
}
