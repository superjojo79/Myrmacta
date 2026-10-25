package com.flowforge.domain.workflow.portal.usecase;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierConflictException;
import com.flowforge.domain.dossier.model.DossierFieldValidationException;
import com.flowforge.domain.dossier.port.in.UpdateDossierFieldsUseCase;
import com.flowforge.domain.workflow.UserQuestionAnswers;
import com.flowforge.domain.workflow.WorkflowVariables;
import com.flowforge.domain.workflow.model.TaskQuestionSchema;
import com.flowforge.domain.workflow.port.out.TaskQuestionSchemaRepository;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import com.flowforge.domain.workflow.portal.PortalAccessResolver;
import com.flowforge.domain.workflow.portal.PortalIdentityFieldValue;
import com.flowforge.domain.workflow.portal.PortalParticipantNotifier;
import com.flowforge.domain.workflow.portal.model.IdentityMatch;
import com.flowforge.domain.workflow.portal.model.NationalRegisterNumber;
import com.flowforge.domain.workflow.portal.model.PortalAccessDeniedException;
import com.flowforge.domain.workflow.portal.model.PortalCredential;
import com.flowforge.domain.workflow.portal.model.PortalLink;
import com.flowforge.domain.workflow.portal.model.PortalLinkIdentity;
import com.flowforge.domain.workflow.portal.port.in.SubmitPortalTaskUseCase;
import com.flowforge.domain.workflow.portal.port.out.PortalIdentityRepository;
import com.flowforge.domain.workflow.portal.port.out.PortalLinkRepository;
import com.flowforge.domain.workflow.portal.port.out.PortalSecretCipherPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * TODO §68 — ordre volontaire, dans <strong>une</strong> transaction (appelant transactionnel) :
 * <ol>
 *   <li>validation des réponses par le schéma (400 : le lien reste {@code ACTIVE}, T-SUBMIT-2) ;</li>
 *   <li>{@code ACTIVE → COMPLETED} en compare-and-set : une seconde soumission concurrente perd et
 *       reçoit le refus neutre (T-SUBMIT-3) ;</li>
 *   <li>trace {@code TaskQuestionAnswer} ({@code submittedBy = portal:<linkId>}) ;</li>
 *   <li>complétion de la tâche — le lien n'étant plus vivant, le listener d'invalidation ne le
 *       révoque pas ;</li>
 *   <li>notification des participants internes (US-6).</li>
 * </ol>
 *
 * <p>TODO §104 — tâche à authentification forte : l'accès exige la preuve d'identité ; la correspondance avec le champ
 * d'identité est recalculée sur le dossier relu dans cette transaction (le champ a pu changer depuis l'authentification,
 * T6). Premier enregistrement : le NRN vérifié est écrit par le use case de correction des champs, en contexte système
 * et à version attendue, après le passage du lien en {@code COMPLETED} ; la preuve et le NRN chiffré sont effacés, la
 * trace reste. Tout échec annule la transaction : rien n'est écrit, le lien reste {@code ACTIVE}.
 */
public final class SubmitPortalTaskUseCaseImpl implements SubmitPortalTaskUseCase {

    private final String tenantId;
    private final PortalAccessResolver accessResolver;
    private final TaskQuestionSchemaRepository schemaRepository;
    private final UserQuestionAnswers userQuestionAnswers;
    private final WorkflowEnginePort workflowEnginePort;
    private final PortalLinkRepository repository;
    private final PortalParticipantNotifier notifier;
    private final Clock clock;
    private final PortalIdentityRepository identities;
    private final PortalSecretCipherPort cipher;
    private final UpdateDossierFieldsUseCase updateFields;

    public SubmitPortalTaskUseCaseImpl(
            String tenantId,
            PortalAccessResolver accessResolver,
            TaskQuestionSchemaRepository schemaRepository,
            UserQuestionAnswers userQuestionAnswers,
            WorkflowEnginePort workflowEnginePort,
            PortalLinkRepository repository,
            PortalParticipantNotifier notifier,
            Clock clock) {
        this(tenantId, accessResolver, schemaRepository, userQuestionAnswers, workflowEnginePort, repository, notifier,
                clock, null, null, null);
    }

    /** {@code identities}, {@code cipher}, {@code updateFields} : requis pour une tâche à authentification forte. */
    public SubmitPortalTaskUseCaseImpl(
            String tenantId,
            PortalAccessResolver accessResolver,
            TaskQuestionSchemaRepository schemaRepository,
            UserQuestionAnswers userQuestionAnswers,
            WorkflowEnginePort workflowEnginePort,
            PortalLinkRepository repository,
            PortalParticipantNotifier notifier,
            Clock clock,
            PortalIdentityRepository identities,
            PortalSecretCipherPort cipher,
            UpdateDossierFieldsUseCase updateFields) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.accessResolver = Objects.requireNonNull(accessResolver, "accessResolver");
        this.schemaRepository = Objects.requireNonNull(schemaRepository, "schemaRepository");
        this.userQuestionAnswers = Objects.requireNonNull(userQuestionAnswers, "userQuestionAnswers");
        this.workflowEnginePort = Objects.requireNonNull(workflowEnginePort, "workflowEnginePort");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.identities = identities;
        this.cipher = cipher;
        this.updateFields = updateFields;
    }

    @Override
    public void submit(PortalCredential credential, Map<String, Object> submitted) {
        PortalAccessResolver.ResolvedAccess access = accessResolver.resolve(credential);
        PortalLink link = access.link();
        IdentityMatch match = access.identityRequired() ? identityMatch(access) : null;
        Map<String, Object> values = submitted == null ? Map.of() : submitted;
        WorkflowVariables.assertNoReservedKeys(values);
        TaskQuestionSchema schema = schemaRepository.findByKey(tenantId, link.schemaKey())
                .orElseThrow(() -> new PortalAccessDeniedException("schema gone for link " + link.id()));
        Map<String, Object> answers = userQuestionAnswers.validate(schema, values);

        Instant now = clock.instant();
        PortalLink completed = link.completed(now);
        if (!repository.transition(link, completed)) {
            throw new PortalAccessDeniedException("link " + link.id() + " already used");
        }
        if (match != null) {
            recordIdentity(access, match, now);
        }
        userQuestionAnswers.record(link.schemaKey(), link.dossierId(), link.taskId(), link.processInstanceId(), answers,
                link.actorId(), now);
        workflowEnginePort.completeTask(link.taskId(), answers);
        notifier.notifySubmitted(completed);
    }

    private IdentityMatch identityMatch(PortalAccessResolver.ResolvedAccess access) {
        if (identities == null || cipher == null || updateFields == null) {
            throw new PortalAccessDeniedException("strong authentication is not configured");
        }
        IdentityMatch match = IdentityMatch.of(PortalIdentityFieldValue.of(access.dossier(), access.identityField()),
                verifiedNumber(access.identity()));
        if (!match.allowsSubmission()) {
            throw new PortalAccessDeniedException("identity field changed since the authentication of link "
                    + access.link().id());
        }
        return match;
    }

    private NationalRegisterNumber verifiedNumber(PortalLinkIdentity identity) {
        return identity.nrnEnc() == null ? null
                : NationalRegisterNumber.parse(cipher.decrypt(identity.nrnEnc())).orElse(null);
    }

    private void recordIdentity(PortalAccessResolver.ResolvedAccess access, IdentityMatch match, Instant now) {
        PortalLink link = access.link();
        if (match.writesTheField()) {
            NationalRegisterNumber nrn = verifiedNumber(access.identity());
            try {
                updateFields.update(new UpdateDossierFieldsUseCase.UpdateDossierFieldsCommand(link.dossierId(),
                                Map.of(access.identityField().fieldName(), nrn.digits()), link.actorId(),
                                access.dossier().version()),
                        AccessContext.system("portal-identity"));
            } catch (DossierConflictException | DossierFieldValidationException ex) {
                // Dossier modifié depuis sa lecture, clos, ou valeur refusée (unicité) : refus neutre, rien n'est écrit.
                throw new PortalAccessDeniedException("identity field could not be written for link " + link.id()
                        + ": " + ex.getClass().getSimpleName());
            }
        }
        if (!identities.markSubmitted(link.id(), now)) {
            throw new PortalAccessDeniedException("identity of link " + link.id() + " already submitted");
        }
    }
}
