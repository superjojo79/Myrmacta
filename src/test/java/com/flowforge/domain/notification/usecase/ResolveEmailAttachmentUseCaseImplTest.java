package com.flowforge.domain.notification.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.document.model.DocumentStorageException;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.notification.model.EmailAttachment;
import com.flowforge.domain.notification.model.NotificationValidationException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Issue #183 — D-NOTIF-ATTACH-PRODUCED-DOC, D-NOTIF-ATTACH-MAX, D-NOTIF-ATTACH-NAME. */
class ResolveEmailAttachmentUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final String REF = "tenant_dev/2026/doc-1.pdf";
    private static final String ORIGIN = "notificationAttachment source variable:generatedDocumentId";
    private static final byte[] PDF = "%PDF-1.7\nbody".getBytes(StandardCharsets.US_ASCII);

    private final DossierElementRepository elements = mock(DossierElementRepository.class);
    private final DocumentStoragePort storage = mock(DocumentStoragePort.class);
    private final ResolveEmailAttachmentUseCaseImpl useCase =
            new ResolveEmailAttachmentUseCaseImpl(TENANT, elements, storage, 64);
    private final UUID dossierId = UUID.randomUUID();

    @Test
    void producedPdfOfTheDossier_becomesAnAttachmentNamedAfterTheRequestedName() {
        stubElements(element(ElementType.PRODUCED_DOC, REF, "Document généré — Décision"));
        when(storage.retrieve(TENANT, REF)).thenReturn(PDF);

        EmailAttachment attachment = useCase.resolve(dossierId, REF, "autorisation", ORIGIN);

        assertThat(attachment).isEqualTo(new EmailAttachment(REF, "autorisation.pdf", "application/pdf"));
    }

    @Test
    void withoutRequestedName_theElementLabelIsSanitisedIntoAFileName() {
        stubElements(element(ElementType.PRODUCED_DOC, REF, "Document généré — Décision: \"riverain\"/2026"));
        when(storage.retrieve(TENANT, REF)).thenReturn(PDF);

        assertThat(useCase.resolve(dossierId, REF, null, ORIGIN).fileName())
                .isEqualTo("Document généré _ Décision_ _riverain__2026.pdf");
    }

    @Test
    void anExistingPdfExtensionIsKeptAndLeadingDotsAreDropped() {
        stubElements(element(ElementType.PRODUCED_DOC, REF, "..Rapport.PDF"));
        when(storage.retrieve(TENANT, REF)).thenReturn(PDF);

        assertThat(useCase.resolve(dossierId, REF, null, ORIGIN).fileName()).isEqualTo("Rapport.PDF");
        assertThat(useCase.resolve(dossierId, REF, "x".repeat(150), ORIGIN).fileName()).isEqualTo("x".repeat(100) + ".pdf");
    }

    @Test
    void aReferenceThatIsNotAProducedDocumentOfThisDossier_isRefusedWithoutReadingTheStorage() {
        stubElements(element(ElementType.ATTACHMENT, REF, "Pièce déposée"),
                element(ElementType.PRODUCED_DOC, "tenant_dev/autre.pdf", "Autre"));

        assertThatThrownBy(() -> useCase.resolve(dossierId, REF, null, ORIGIN))
                .isInstanceOf(NotificationValidationException.class)
                .hasMessageContaining(ORIGIN).hasMessageContaining("produced document of this dossier")
                .hasMessageNotContaining(REF);
        verify(storage, never()).retrieve(TENANT, REF);
    }

    @Test
    void missingEmptyTooLargeOrNonPdfContent_isRefusedWithoutTheValue() {
        stubElements(element(ElementType.PRODUCED_DOC, REF, "Décision"));

        doThrow(new DocumentStorageException("missing " + REF)).when(storage).retrieve(TENANT, REF);
        assertRefused("could not be read");

        doReturn(new byte[0]).when(storage).retrieve(TENANT, REF);
        assertRefused("empty");

        byte[] big = new byte[65];
        System.arraycopy(PDF, 0, big, 0, 5);
        doReturn(big).when(storage).retrieve(TENANT, REF);
        assertRefused("exceeds 64 bytes");

        doReturn("<html>".getBytes(StandardCharsets.US_ASCII)).when(storage).retrieve(TENANT, REF);
        assertRefused("not a PDF");
    }

    private void assertRefused(String reason) {
        assertThatThrownBy(() -> useCase.resolve(dossierId, REF, null, ORIGIN))
                .isInstanceOf(NotificationValidationException.class)
                .hasMessageContaining(ORIGIN).hasMessageContaining(reason).hasMessageNotContaining(REF);
    }

    private void stubElements(DossierElement... list) {
        when(elements.findByDossierIdOrderByAddedAtAsc(dossierId)).thenReturn(List.of(list));
    }

    private DossierElement element(ElementType type, String storageRef, String label) {
        return new DossierElement(UUID.randomUUID(), dossierId, TENANT, type, label, storageRef,
                Map.of("contentType", "application/pdf"), "system", Instant.now(), "genererDecision");
    }
}
