package be.myrmacta.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.port.in.CreateDossierUseCase;
import be.myrmacta.domain.dossier.port.in.UpdateDossierStatusUseCase;
import be.myrmacta.domain.dossier.port.in.UpdateDossierTypeUseCase;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.workflow.model.DmnDraft;
import be.myrmacta.domain.workflow.port.in.ProposeDraftUseCase;
import be.myrmacta.domain.workflow.port.out.DmnDraftRepository;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import tools.jackson.databind.json.JsonMapper;

/**
 * TODO §135 (D-DEMO-INSTALL) — un paquet « états » interrompu au brouillon (dernier effet) n'est pas tenu pour installé,
 * même si l'objectif SLA de FACTURE (premier effet) est déjà écrit.
 */
class StatesDemoPackInstallTest {

    private static final String TENANT = "tenant_dev";

    private final DossierTypeRepository types = mock(DossierTypeRepository.class);
    private final UpdateDossierTypeUseCase updateType = mock(UpdateDossierTypeUseCase.class);
    private final CreateDossierUseCase createDossier = mock(CreateDossierUseCase.class);
    private final UpdateDossierStatusUseCase updateStatus = mock(UpdateDossierStatusUseCase.class);
    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final ProposeDraftUseCase drafts = mock(ProposeDraftUseCase.class);
    private final DmnDraftRepository draftRepository = mock(DmnDraftRepository.class);
    private final OnboardingDemoPack onboarding = mock(OnboardingDemoPack.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final List<Dossier> factures = new ArrayList<>();
    private final AtomicReference<DossierType> facture = new AtomicReference<>(type("FACTURE", null));
    private StatesDemoPack pack;

    @BeforeEach
    void setUp() {
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(types.findByCode(eq(TENANT), eq("FACTURE"))).thenAnswer(invocation -> Optional.of(facture.get()));
        when(types.findByCode(eq(TENANT), eq("COURRIER"))).thenReturn(Optional.of(type("COURRIER", null)));
        when(updateType.update(any(), any())).thenAnswer(invocation -> {
            UpdateDossierTypeUseCase.UpdateDossierTypeCommand command = invocation.getArgument(0);
            DossierType updated = type(command.code(), command.slaTargetHours());
            if ("FACTURE".equals(command.code())) {
                facture.set(updated);
            }
            return updated;
        });
        when(createDossier.create(any(), any())).thenAnswer(invocation -> {
            CreateDossierUseCase.CreateDossierCommand command = invocation.getArgument(0);
            Dossier created = dossier(command.label());
            factures.add(created);
            return created;
        });
        when(dossiers.findById(any())).thenAnswer(invocation -> factures.stream()
                .filter(item -> item.id().equals(invocation.getArgument(0))).findFirst());
        when(dossiers.findByDossierTypeCode(TENANT, "FACTURE")).thenAnswer(invocation -> List.copyOf(factures));
        when(dossiers.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(draftRepository.findByTenantIdAndDecisionKey(TENANT, StatesDemoPack.ROUTING)).thenReturn(Optional.empty());
        pack = new StatesDemoPack("exemples/digital-mail-room", "fr", JsonMapper.builder().build(), new TenantProperties(TENANT),
                types, updateType, createDossier, updateStatus, dossiers, drafts, draftRepository, onboarding, transactions);
    }

    @Test
    void aPackInterruptedAtTheDraftIsNotInstalled_evenThoughTheInvoiceSlaIsAlreadySet() {
        when(drafts.proposeDmn(any(), any(), any(), any(), any(), anyBoolean()))
                .thenThrow(new IllegalStateException("draft failed"));

        assertThatThrownBy(pack::install).hasMessageContaining("draft failed");

        assertThat(facture.get().slaTargetHours()).as("le premier effet a bien été écrit").isEqualTo(72);
        assertThat(pack.installed()).isFalse();

        int created = factures.size();
        assertThatThrownBy(pack::install).hasMessageContaining("draft failed");
        assertThat(factures).as("rejouer ne duplique pas les factures déjà créées").hasSize(created);
    }

    @Test
    void installedIsTrueOnlyWhenTheRoutingDraftExists() {
        assertThat(pack.installed()).isFalse();
        when(draftRepository.findByTenantIdAndDecisionKey(TENANT, StatesDemoPack.ROUTING))
                .thenReturn(Optional.of(new DmnDraft(TENANT, StatesDemoPack.ROUTING, "<dmn/>", Instant.parse("2026-11-12T12:00:00Z"),
                        "demo")));
        assertThat(pack.installed()).isTrue();
    }

    private static DossierType type(String code, Integer slaHours) {
        return new DossierType(UUID.randomUUID(), TENANT, code, code, "processus", true, false, List.of(), null, false, slaHours,
                "demo", "demo");
    }

    private static Dossier dossier(String label) {
        Instant now = Instant.parse("2026-11-12T12:00:00Z");
        return new Dossier(UUID.randomUUID(), TENANT, "DOS-" + UUID.randomUUID().toString().substring(0, 8), "FACTURE", label,
                DossierStatus.OUVERT, java.util.Map.of(), null, 0L, "demo", now, "demo", now, null, null, null);
    }
}
