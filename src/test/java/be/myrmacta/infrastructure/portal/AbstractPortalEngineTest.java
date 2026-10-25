package be.myrmacta.infrastructure.portal;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.notification.port.out.EmailPort;
import be.myrmacta.domain.workflow.model.TaskQuestionSchema;
import be.myrmacta.domain.workflow.port.out.TaskQuestionSchemaRepository;
import be.myrmacta.domain.workflow.portal.port.in.SendPortalInvitesUseCase;
import be.myrmacta.domain.workflow.portal.port.out.PortalLinkRepository;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * TODO §68 — contexte Spring avec le portail activé, BPMN {@code portal-smoke.bpmn20.xml} (tâche
 * externe) et {@link EmailPort} capturé : l'e-mail capturé est le seul chemin légitime vers le
 * jeton en clair.
 */
@SpringBootTest(properties = {
        "app.portal.enabled=true",
        "app.portal.public-base-url=https://ff.example.org/tenant-a",
        "app.portal.send.poll-delay=1h",
        "app.portal.expiry.poll-delay=1h"
})
@Import(OidcClientTestConfiguration.class)
abstract class AbstractPortalEngineTest {

    static final String SCHEMA_KEY = "portal-smoke";
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("/portal/#([A-Za-z0-9_-]{43})");

    @Autowired
    protected RuntimeService runtimeService;

    @Autowired
    protected TaskService taskService;

    @Autowired
    protected DossierRepository dossierRepository;

    @Autowired
    protected TaskQuestionSchemaRepository schemaRepository;

    @Autowired
    protected PortalLinkRepository portalLinkRepository;

    @Autowired
    protected SendPortalInvitesUseCase sendPortalInvitesUseCase;

    @MockitoBean
    protected EmailPort emailPort;

    protected void ensureSchema() {
        if (schemaRepository.findByKey("tenant_dev", SCHEMA_KEY).isEmpty()) {
            schemaRepository.save(new TaskQuestionSchema(UUID.randomUUID(), "tenant_dev", SCHEMA_KEY, "Avis",
                    List.of(new FieldDefinition("disponible", "Disponible ?", FieldType.BOOLEAN, true, null,
                                    List.of(), null),
                            new FieldDefinition("commentaire", "Commentaire", FieldType.STRING, false, null,
                                    List.of(), null)),
                    "test", "test"));
        }
    }

    protected Dossier dossier(Map<String, Object> fields) {
        Instant now = Instant.now();
        return dossierRepository.save(new Dossier(UUID.randomUUID(), "tenant_dev", "REF-" + System.nanoTime(),
                "SINISTRE", "Dossier portail", DossierStatus.OUVERT, fields, null, 0L, "test", now, null, null, null,
                null, null));
    }

    protected Task startPortalSmoke(UUID dossierId) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("dossierId", dossierId.toString());
        String processInstanceId = runtimeService.startProcessInstanceByKey("portalSmoke", variables).getId();
        return taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();
    }

    /** Envoie les invitations en attente et renvoie le jeton envoyé au destinataire donné. */
    protected String sendAndCaptureToken(String recipient) {
        sendPortalInvitesUseCase.sendPending();
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailPort, atLeastOnce()).send(eq(recipient), anyString(), body.capture());
        Matcher matcher = TOKEN_IN_LINK.matcher(body.getValue());
        if (!matcher.find()) {
            throw new AssertionError("no portal link in e-mail body");
        }
        return matcher.group(1);
    }
}
