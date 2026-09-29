package com.flowforge.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.signature.model.SignatureRequest;
import com.flowforge.domain.signature.model.SignatureStatus;
import com.flowforge.domain.signature.model.Signer;
import com.flowforge.domain.signature.model.SignerStatus;
import com.flowforge.domain.workflow.portal.port.out.PortalTokenPort;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/** TODO §72, M4 — émission et invitations sur le vrai moteur Flowable (S-EMIT-1/2/5, S-DEC-7, S-ACCESS-6). */
@ExtendWith(OutputCaptureExtension.class)
class SignatureEmissionEngineTest extends AbstractSignatureEngineTest {

    @Autowired
    private PortalTokenPort tokenPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private org.flowable.engine.ManagementService managementService;

    private List<SignatureRequest> requestsOf(UUID dossierId) {
        return signatureRepository.findByDossierId(dossierId);
    }

    @Test
    void signatureBlockCreatesAPendingRequestAndTheProcessWaitsForTheOutcome() {
        String a = "client-" + System.nanoTime() + "@example.org";
        String b = "garant-" + System.nanoTime() + "@example.org";
        UUID dossierId = dossier(Map.of("signerA", a, "signerB", b)).id();
        String ref = producedDoc(dossierId);

        String processInstanceId = startSignatureSmoke(dossierId, ref);

        SignatureRequest request = requestsOf(dossierId).getFirst();
        assertThat(request.status()).isEqualTo(SignatureStatus.PENDING);
        assertThat(request.processInstanceId()).isEqualTo(processInstanceId);
        assertThat(request.activityId()).isEqualTo("sigRequest");
        assertThat(request.providerCode()).isEqualTo("stub");
        assertThat(request.simulated()).isTrue();
        assertThat(signatureRepository.findSigners(request.id()))
                .extracting(Signer::status).containsOnly(SignerStatus.PENDING);
        // le process attend le message signatureCompleted (pas d'incident, pas de tâche) ; la variable de
        // corrélation est locale à l'exécution en attente (revue adversariale n°6)
        var waiting = runtimeService.createExecutionQuery()
                .processInstanceId(processInstanceId)
                .messageEventSubscriptionName("signatureCompleted")
                .singleResult();
        assertThat(waiting).isNotNull();
        assertThat(runtimeService.getVariableLocal(waiting.getId(), "signatureRequestId"))
                .isEqualTo(request.id().toString());
        assertThat(taskService.createTaskQuery().processInstanceId(processInstanceId).count()).isZero();
    }

    @Test
    void corruptPdfFailsAtEmissionInsteadOfBlockingTheLastSignature() {
        // revue adversariale n°1 : le process reprend tout de suite avec FAILED, aucune invitation ne part
        String a = "client-" + System.nanoTime() + "@example.org";
        UUID dossierId = dossier(Map.of("signerA", a, "signerB", "garant-" + System.nanoTime() + "@example.org")).id();
        String corrupt = storage.store(TENANT, "application/pdf", "%PDF-1.4 garbage".getBytes());
        elementRepository.save(new com.flowforge.domain.dossier.model.DossierElement(UUID.randomUUID(), dossierId,
                TENANT, com.flowforge.domain.dossier.model.ElementType.PRODUCED_DOC, "Contrat", corrupt,
                Map.of("contentType", "application/pdf"), "system", java.time.Instant.now(), null));

        String processInstanceId = startSignatureSmoke(dossierId, corrupt);

        SignatureRequest request = requestsOf(dossierId).getFirst();
        assertThat(request.status()).isEqualTo(SignatureStatus.FAILED);
        assertThat(request.statusReason()).isEqualTo("PROVIDER_ERROR");
        assertThat(taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult().getName())
                .isEqualTo("Signature non obtenue");
    }

    @Test
    void invitationsAreSentOnceWithATokenThatIsNeverStoredNorLogged(CapturedOutput output) {
        // S-DEC-7 (parallèle), S-ACCESS-6 (partie stockage / journaux)
        String a = "client-" + System.nanoTime() + "@example.org";
        String b = "garant-" + System.nanoTime() + "@example.org";
        UUID dossierId = dossier(Map.of("signerA", a, "signerB", b)).id();
        startSignatureSmoke(dossierId, producedDoc(dossierId));

        String tokenA = sendAndCaptureToken(a);
        String tokenB = sendAndCaptureToken(b);

        assertThat(tokenA).isNotEqualTo(tokenB);
        SignatureRequest request = requestsOf(dossierId).getFirst();
        List<Signer> signers = signatureRepository.findSigners(request.id());
        assertThat(signers).extracting(Signer::inviteSentAt).doesNotContainNull();
        assertThat(signers.get(0).tokenHash()).isEqualTo(tokenPort.hash(tokenA));
        assertThat(signers.get(1).tokenHash()).isEqualTo(tokenPort.hash(tokenB));
        assertThat(jdbcTemplate.queryForList("select * from signature_signer").toString())
                .doesNotContain(tokenA)
                .doesNotContain(tokenB);
        assertThat(jdbcTemplate.queryForList("select * from notification_outbox").toString())
                .doesNotContain(tokenA);
        assertThat(output.getAll()).doesNotContain(tokenA).doesNotContain(tokenB).doesNotContain(a).doesNotContain(b);
        // une seconde exécution du job n'invite personne à nouveau
        assertThat(sendInvites.sendPending()).isZero();
    }

    @Test
    void missingAddressFailsTheRequestWithoutBlockingTheProcess() {
        // S-EMIT-2 : pas d'incident moteur
        UUID dossierId = dossier(Map.of("signerA", "client-" + System.nanoTime() + "@example.org")).id();

        String processInstanceId = startSignatureSmoke(dossierId, producedDoc(dossierId));

        SignatureRequest request = requestsOf(dossierId).getFirst();
        assertThat(request.status()).isEqualTo(SignatureStatus.FAILED);
        assertThat(request.statusReason()).isEqualTo("NO_ADDRESS");
        // D-SIGNATURE-DISPATCH : l'issue FAILED est reprise après validation de la transaction du moteur
        // (le process est alors abonné au message) ; sinon le job la reprendrait. Aucun incident.
        assertThat(managementService.createDeadLetterJobQuery().processInstanceId(processInstanceId).count()).isZero();
        assertThat(runtimeService.getVariable(processInstanceId, "signatureOutcome")).isEqualTo("FAILED");
        assertThat(taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult().getName())
                .isEqualTo("Signature non obtenue");
        assertThat(signatureRepository.findById(request.id()).orElseThrow().outcomeDispatchedAt()).isNotNull();
    }

    @Test
    void missingDocumentVariableFailsTheRequest() {
        UUID dossierId = dossier(Map.of(
                        "signerA", "client-" + System.nanoTime() + "@example.org",
                        "signerB", "garant-" + System.nanoTime() + "@example.org"))
                .id();

        startSignatureSmoke(dossierId, null);

        assertThat(requestsOf(dossierId).getFirst().statusReason()).isEqualTo("SOURCE_NOT_FOUND");
    }
}
