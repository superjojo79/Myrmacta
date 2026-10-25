package be.myrmacta.domain.workflow.portal.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.AttachmentPieceTypes;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.dossier.model.InvalidDossierElementException;
import be.myrmacta.domain.dossier.model.PieceType;
import be.myrmacta.domain.dossier.port.in.AddDossierElementUseCase;
import be.myrmacta.domain.dossier.port.out.AttachmentScanPort;
import be.myrmacta.domain.dossier.port.out.AttachmentScanResult;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.workflow.portal.PortalAccessResolver;
import be.myrmacta.domain.workflow.portal.model.PortalCredential;
import be.myrmacta.domain.workflow.portal.model.PortalLink;
import be.myrmacta.domain.workflow.portal.model.PortalRecipientSource;
import be.myrmacta.domain.workflow.portal.model.PortalTaskDefinition;
import be.myrmacta.domain.workflow.portal.port.in.AddPortalAttachmentUseCase.AttachmentUpload;
import be.myrmacta.domain.workflow.portal.port.out.PortalTaskLookupPort;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.workflow.model.TaskQuestionSchema;
import be.myrmacta.domain.workflow.port.out.TaskQuestionSchemaRepository;
import be.myrmacta.domain.workflow.portal.port.in.GetPortalTaskUseCase;
import be.myrmacta.domain.workflow.portal.port.out.PortalIdentityProviderPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** TODO §106 (D-PIECE-PORTAL) — types de pièces proposés au tiers par une tâche externe. */
class PortalPieceTypesTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");
    private static final byte[] PDF = "%PDF-1.7".getBytes();
    private final PortalAccessResolver resolver = mock(PortalAccessResolver.class);
    private final AttachmentScanPort scan = mock(AttachmentScanPort.class);
    private final DocumentStoragePort storage = mock(DocumentStoragePort.class);
    private final AddDossierElementUseCase addElement = mock(AddDossierElementUseCase.class);
    private final DossierTypeRepository typeRepository = mock(DossierTypeRepository.class);
    private final AddPortalAttachmentUseCaseImpl useCase = new AddPortalAttachmentUseCaseImpl("tenant_dev", resolver,
            "application/pdf"::equals, scan, storage, addElement, new AttachmentPieceTypes(typeRepository));

    @BeforeEach
    void setUp() {
        PortalLink link = PortalLink.pendingSend(UUID.randomUUID(), "tenant_dev", UUID.randomUUID(), "t", "pi", "avis",
                "a@b.org", 7, NOW, "x").activated("h", NOW);
        Dossier dossier = new Dossier(link.dossierId(), "tenant_dev", "REF", "PERMIS", "L", DossierStatus.OUVERT,
                Map.of(), null, 0L, "u", NOW, null, null, null, null, null);
        when(resolver.resolve(PortalCredential.of("tok", null))).thenReturn(new PortalAccessResolver.ResolvedAccess(
                link, new PortalTaskLookupPort.OpenTask("t", "pi", "T", null), dossier, null, null,
                List.of("preuve", "retire")));
        when(typeRepository.findByCode("tenant_dev", "PERMIS")).thenReturn(Optional.of(new DossierType(
                UUID.randomUUID(), "tenant_dev", "PERMIS", "Permis", "wf", true, false, List.of(), null, false, null,
                List.of(), List.of(new PieceType("preuve", "Preuve", false), new PieceType("identite", "ID", true)),
                "u", "u")));
        when(scan.scan(any(), anyString())).thenReturn(new AttachmentScanResult(true, null));
        when(storage.store(anyString(), anyString(), any())).thenReturn("tenant_dev/x.pdf");
    }

    @Test
    void parsesKeysInOrderAndRefusesInvalidOnes() {
        assertThat(PortalTaskDefinition.parsePieceTypeKeys(" preuve , identite")).containsExactly("preuve", "identite");
        for (String raw : List.of("", "preuve,,identite", "preuve,preuve", "Preuve", "a,b,c,d,e,f,g,h,i,j,k")) {
            assertThatThrownBy(() -> PortalTaskDefinition.parsePieceTypeKeys(raw))
                    .as(raw)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(new PortalTaskDefinition("t", "s",
                new PortalRecipientSource(PortalRecipientSource.Kind.PROCESS_VARIABLE, "mail"), null).pieceTypeKeys())
                .isEmpty();
    }

    @Test
    void offeredAndDeclaredKeyIsStoredOnTheAttachment() {
        useCase.add("tok", new AttachmentUpload("Preuve", "p.pdf", "application/pdf", PDF, "preuve"));

        ArgumentCaptor<AddDossierElementUseCase.AddCommand> command =
                ArgumentCaptor.forClass(AddDossierElementUseCase.AddCommand.class);
        verify(addElement).add(command.capture(), any());
        assertThat(command.getValue().metadata()).containsEntry("pieceKey", "preuve");
    }

    @Test
    void keyNotOfferedByTheTaskOrNoLongerDeclaredIsRefusedBeforeStorage() {
        // « identite » est déclaré sur le type mais la tâche ne le propose pas ; « retire » est proposé mais plus déclaré.
        for (String key : List.of("identite", "retire")) {
            assertThatThrownBy(() -> useCase.add("tok", new AttachmentUpload("L", "p.pdf", "application/pdf", PDF, key)))
                    .as(key)
                    .isInstanceOf(InvalidDossierElementException.class);
        }
        verify(storage, never()).store(anyString(), anyString(), any());
    }

    @Test
    void withoutPieceTypesWiredAnyKeyIsRefused() {
        AddPortalAttachmentUseCaseImpl legacy = new AddPortalAttachmentUseCaseImpl("tenant_dev", resolver,
                "application/pdf"::equals, scan, storage, addElement);

        assertThatThrownBy(() -> legacy.add("tok", new AttachmentUpload("L", "p.pdf", "application/pdf", PDF, "preuve")))
                .isInstanceOf(InvalidDossierElementException.class);
        legacy.add("tok", new AttachmentUpload("L", "p.pdf", "application/pdf", PDF));
        verify(addElement).add(any(), any());
    }

    @Test
    void portalViewOffersOnlyDeclaredTypesOfTheTask() {
        PortalAccessResolver.ResolvedAccess access = resolver.resolve(PortalCredential.of("tok", null));
        when(resolver.resolveAllowingPendingIdentity(PortalCredential.of("tok", null))).thenReturn(access);
        TaskQuestionSchemaRepository schemas = mock(TaskQuestionSchemaRepository.class);
        TaskQuestionSchema schema = new TaskQuestionSchema(UUID.randomUUID(), "tenant_dev", "avis", "Avis",
                List.of(new FieldDefinition("avis", "Avis", FieldType.STRING, false, null, List.of(), null)), "u", "u");
        when(schemas.findByKey("tenant_dev", "avis")).thenReturn(Optional.of(schema));
        DossierElementRepository elements = mock(DossierElementRepository.class);

        GetPortalTaskUseCase.PortalTaskView view = new GetPortalTaskUseCaseImpl("tenant_dev", resolver, schemas,
                elements, PortalIdentityProviderPort.NONE, new AttachmentPieceTypes(typeRepository)).get("tok");

        assertThat(view.pieceTypes()).extracting(PieceType::key).containsExactly("preuve");
        assertThat(new GetPortalTaskUseCaseImpl("tenant_dev", resolver, schemas, elements).get("tok").pieceTypes())
                .isEmpty();
    }
}
