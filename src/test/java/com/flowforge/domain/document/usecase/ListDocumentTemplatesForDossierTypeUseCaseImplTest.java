package com.flowforge.domain.document.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.document.model.DocumentTemplate;
import com.flowforge.domain.document.model.TemplateStatus;
import com.flowforge.domain.document.port.out.DocumentTemplateRepository;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.port.in.GetDossierTypeUseCase;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListDocumentTemplatesForDossierTypeUseCaseImplTest {

    private static final String TENANT = "tenant_dev";

    private final GetDossierTypeUseCase getDossierTypeUseCase = mock(GetDossierTypeUseCase.class);
    private final DocumentTemplateRepository repository = mock(DocumentTemplateRepository.class);
    private final ListDocumentTemplatesForDossierTypeUseCaseImpl useCase =
            new ListDocumentTemplatesForDossierTypeUseCaseImpl(TENANT, getDossierTypeUseCase, repository);

    private static final AccessContext.User ACTOR = AccessContext.user("manager", Set.of("DOSSIER_MANAGER"));

    @Test
    void list_emptyDocumentTemplateKeys_returnsEmptyWithoutRepositoryCall() {
        when(getDossierTypeUseCase.getByCode(TENANT, "CLAIM", ACTOR)).thenReturn(type(List.of()));

        List<DocumentTemplate> result = useCase.list(TENANT, "CLAIM", ACTOR);

        assertThat(result).isEmpty();
        verify(repository, never()).findActiveByTemplateKeysIn(any(), any());
    }

    @Test
    void list_delegatesToRepositoryWithConfiguredKeys() {
        when(getDossierTypeUseCase.getByCode(TENANT, "CLAIM", ACTOR))
                .thenReturn(type(List.of("invoice", "contract")));
        DocumentTemplate template = template("invoice");
        when(repository.findActiveByTemplateKeysIn(TENANT, List.of("invoice", "contract")))
                .thenReturn(List.of(template));

        List<DocumentTemplate> result = useCase.list(TENANT, "CLAIM", ACTOR);

        assertThat(result).containsExactly(template);
    }

    @Test
    void list_dossierTypeNotFound_propagatesException() {
        when(getDossierTypeUseCase.getByCode(TENANT, "MISSING", ACTOR))
                .thenThrow(new DossierNotFoundException("Dossier type not found"));

        assertThatThrownBy(() -> useCase.list(TENANT, "MISSING", ACTOR))
                .isInstanceOf(DossierNotFoundException.class);
        verify(repository, never()).findActiveByTemplateKeysIn(any(), any());
    }

    @Test
    void list_accessDenied_propagatesExceptionFromViewCheck() {
        when(getDossierTypeUseCase.getByCode(TENANT, "CLAIM", ACTOR))
                .thenThrow(new AccessDeniedException("Access denied for permission VIEW"));

        assertThatThrownBy(() -> useCase.list(TENANT, "CLAIM", ACTOR))
                .isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).findActiveByTemplateKeysIn(any(), any());
    }

    private static DossierType type(List<String> documentTemplateKeys) {
        return DossierType.inherit(UUID.randomUUID(), TENANT, "CLAIM", "Claim", "wf-claim", true, false, List.of())
                .withUpdate("Claim", "wf-claim", true, false, List.of(), null, false, null,
                        documentTemplateKeys, "system");
    }

    private static DocumentTemplate template(String templateKey) {
        Instant now = Instant.parse("2026-09-03T00:00:00Z");
        return new DocumentTemplate(
                UUID.randomUUID(), templateKey, null, null, "Label", 1, TemplateStatus.ACTIVE,
                "<p>content</p>", TENANT, now, now, "admin");
    }
}
