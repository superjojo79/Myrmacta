package be.myrmacta.infrastructure.signature;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.notification.port.out.EmailPort;
import be.myrmacta.domain.signature.port.in.SendSignatureInvitesUseCase;
import be.myrmacta.domain.signature.port.out.SignatureRequestRepository;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * TODO §72 — contexte Spring avec le stub de signature (profil {@code test}, garde
 * D-SIGNATURE-STUB-GUARD), le portail activé, le BPMN {@code signature-smoke.bpmn20.xml} et
 * {@link EmailPort} capturé : l'e-mail est le seul chemin légitime vers le jeton en clair.
 */
@SpringBootTest(properties = {
        "app.signature.provider=stub",
        "app.signature.send.poll-delay=1h",
        "app.signature.expiry.poll-delay=1h",
        "app.signature.dispatch.poll-delay=1h",
        "app.signature.send.resend-min-interval=0s",
        "app.portal.enabled=true",
        "app.portal.public-base-url=https://ff.example.org/tenant-a",
        "app.portal.send.poll-delay=1h",
        "app.portal.expiry.poll-delay=1h"
})
@ActiveProfiles("test")
@Import(OidcClientTestConfiguration.class)
abstract class AbstractSignatureEngineTest {

    static final String TENANT = "tenant_dev";
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("/portal/#signature:([A-Za-z0-9_-]{43})");

    @Autowired
    protected RuntimeService runtimeService;

    @Autowired
    protected TaskService taskService;

    @Autowired
    protected DossierRepository dossierRepository;

    @Autowired
    protected DossierElementRepository elementRepository;

    @Autowired
    protected DocumentStoragePort storage;

    @Autowired
    protected SignatureRequestRepository signatureRepository;

    @Autowired
    protected SendSignatureInvitesUseCase sendInvites;

    @MockitoBean
    protected EmailPort emailPort;

    protected Dossier dossier(Map<String, Object> fields) {
        Instant now = Instant.now();
        return dossierRepository.save(new Dossier(UUID.randomUUID(), TENANT, "REF-" + System.nanoTime(), "SINISTRE",
                "Dossier signature", DossierStatus.OUVERT, fields, null, 0L, "test", now, null, null, null, null,
                null));
    }

    /** Ajoute un {@code PRODUCED_DOC} PDF au dossier et renvoie son {@code storageRef}. */
    protected String producedDoc(UUID dossierId) {
        String ref = storage.store(TENANT, "application/pdf", TestPdfs.pdf(1, "Contrat de test"));
        elementRepository.save(new DossierElement(UUID.randomUUID(), dossierId, TENANT, ElementType.PRODUCED_DOC,
                "Contrat", ref, Map.of("contentType", "application/pdf"), "system", Instant.now(), null));
        return ref;
    }

    protected String startSignatureSmoke(UUID dossierId, String storageRef) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("dossierId", dossierId.toString());
        if (storageRef != null) {
            variables.put("generatedDocumentId", storageRef);
        }
        return runtimeService.startProcessInstanceByKey("signatureSmoke", variables).getId();
    }

    /** Envoie les invitations en attente et renvoie le jeton envoyé au destinataire donné. */
    protected String sendAndCaptureToken(String recipient) {
        sendInvites.sendPending();
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailPort, atLeastOnce()).send(eq(recipient), anyString(), body.capture());
        Matcher matcher = TOKEN_IN_LINK.matcher(body.getValue());
        if (!matcher.find()) {
            throw new AssertionError("no signature link in e-mail body: " + body.getValue());
        }
        return matcher.group(1);
    }
}
