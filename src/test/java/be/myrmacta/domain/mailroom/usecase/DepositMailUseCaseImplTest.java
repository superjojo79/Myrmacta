package be.myrmacta.domain.mailroom.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorInstanceNotFoundException;
import be.myrmacta.domain.connector.model.WorkflowTriggerEvent;
import be.myrmacta.domain.connector.port.in.ConnectorTriggerRuntime;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.mailroom.model.DepositFile;
import be.myrmacta.domain.mailroom.model.DepositRefusedException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** TODO §113 (D-MAILROOM-DEPOSIT) — points de dépôt filtrés par ACL ; un PDF contrôlé devient un courrier du point de dépôt. */
class DepositMailUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final byte[] PDF = "%PDF-1.4 lettre".getBytes(StandardCharsets.US_ASCII);

    private final ConnectorInstanceRepository instances = mock(ConnectorInstanceRepository.class);
    private final DossierAccessPolicy policy = mock(DossierAccessPolicy.class);
    private final ConnectorTriggerRuntime runtime = mock(ConnectorTriggerRuntime.class);
    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final AccessContext.User agent = new AccessContext.User("agent", Set.of("DOSSIER_MANAGER"));
    private final ConnectorInstanceData point = instance("mail-deposit", true, "COURRIER");
    private DepositMailUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new DepositMailUseCaseImpl(TENANT, instances, policy, runtime, dossiers, 1024);
        when(instances.findByIdAndTenantId(point.id(), TENANT)).thenReturn(Optional.of(point));
    }

    @Test
    void onlyActiveDepositPointsWhoseTypeTheAgentMayCreateAreOffered() {
        ConnectorInstanceData disabled = instance("mail-deposit", false, "COURRIER");
        ConnectorInstanceData imap = instance("imap-inbound", true, "COURRIER");
        ConnectorInstanceData forbidden = instance("mail-deposit", true, "RH");
        when(instances.listByTenantId(TENANT)).thenReturn(List.of(point, disabled, imap, forbidden));
        when(policy.canEditType(TENANT, "COURRIER", agent)).thenReturn(true);
        when(policy.canEditType(TENANT, "RH", agent)).thenReturn(false);

        assertThat(useCase.points(agent)).extracting(p -> p.id()).containsExactly(point.id());
    }

    @Test
    void aPdfBecomesAMailWithTheDefaults_theSender_andTheFileNameAsSubjectWhenNoneIsGiven() {
        UUID created = UUID.randomUUID();
        when(runtime.deliver(eq(point), any())).thenReturn(new ConnectorTriggerRuntime.Delivery(created, 0));

        var result = useCase.deposit(point.id(), new DepositFile("scan-0042.pdf", PDF), " greffe@tribunal.example ", " ", agent);

        assertThat(result.dossierId()).isEqualTo(created);
        ArgumentCaptor<WorkflowTriggerEvent> event = ArgumentCaptor.forClass(WorkflowTriggerEvent.class);
        org.mockito.Mockito.verify(runtime).deliver(eq(point), event.capture());
        assertThat(event.getValue().processKey()).isEqualTo("triCourrier");
        assertThat(event.getValue().dossierFields()).containsEntry("canal", "PAPIER")
                .containsEntry("expediteur", "greffe@tribunal.example").containsEntry("objet", "scan-0042");
        assertThat(event.getValue().variables()).containsEntry("courrierFichier", "scan-0042.pdf").containsEntry("courrierDeposePar", "agent");
        assertThat(event.getValue().attachments()).singleElement().satisfies(a -> assertThat(a.contentType()).isEqualTo("application/pdf"));
    }

    /** Revue de clôture : un PDF écarté par les contrôles (antivirus…) est refusé, ce qui annule la transaction du fichier. */
    @Test
    void aPdfRejectedByTheAttachmentChecksIsRefused_notReportedAsCreated() {
        when(runtime.deliver(eq(point), any())).thenReturn(new ConnectorTriggerRuntime.Delivery(UUID.randomUUID(), 1));

        assertThatThrownBy(() -> useCase.deposit(point.id(), new DepositFile("infecte.pdf", PDF), null, null, agent))
                .isInstanceOf(DepositRefusedException.class).hasMessageContaining("antivirus");
    }

    @Test
    void aFileThatIsNotAPdf_tooLarge_orBadlyNamedIsRefusedBeforeAnyWrite() {
        assertThatThrownBy(() -> useCase.deposit(point.id(), new DepositFile("x.pdf", "MZ".getBytes(StandardCharsets.US_ASCII)), null,
                null, agent)).isInstanceOf(DepositRefusedException.class).hasMessage("pas un PDF");
        assertThatThrownBy(() -> useCase.deposit(point.id(), new DepositFile("x.pdf", new byte[2048]), null, null, agent))
                .hasMessage("trop volumineux");
        assertThatThrownBy(() -> useCase.deposit(point.id(), new DepositFile("a\nb.pdf", PDF), null, null, agent))
                .hasMessage("nom de fichier invalide");
        verifyNoInteractions(runtime);
    }

    @Test
    void anUnknownOrDisabledPoint_orATypeTheAgentMayNotCreate_isRefused() {
        UUID unknown = UUID.randomUUID();
        when(instances.findByIdAndTenantId(unknown, TENANT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.point(unknown, agent)).isInstanceOf(ConnectorInstanceNotFoundException.class);

        ConnectorInstanceData disabled = instance("mail-deposit", false, "COURRIER");
        when(instances.findByIdAndTenantId(disabled.id(), TENANT)).thenReturn(Optional.of(disabled));
        assertThatThrownBy(() -> useCase.point(disabled.id(), agent)).isInstanceOf(ConnectorInstanceNotFoundException.class);

        doThrow(new AccessDeniedException("no")).when(policy).assertCanEditType(TENANT, "COURRIER", agent);
        assertThatThrownBy(() -> useCase.deposit(point.id(), new DepositFile("x.pdf", PDF), null, null, agent))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(runtime);
    }

    private static ConnectorInstanceData instance(String type, boolean enabled, String dossierType) {
        return new ConnectorInstanceData(UUID.randomUUID(), TENANT, "Point " + dossierType, type, enabled,
                Map.of("processKey", "triCourrier", "dossierTypeCode", dossierType, "defaultFields", Map.of("canal", "PAPIER")));
    }
}
