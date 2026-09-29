package com.flowforge.domain.signature.usecase;

import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.signature.SignatureNotifier;
import com.flowforge.domain.signature.model.SignatureBlockDefinition;
import com.flowforge.domain.signature.model.SignatureBlockDefinition.SignerDefinition;
import com.flowforge.domain.signature.model.SignatureMode;
import com.flowforge.domain.signature.model.SignatureOutcome;
import com.flowforge.domain.signature.model.SignatureProgression;
import com.flowforge.domain.signature.model.SignatureRequest;
import com.flowforge.domain.signature.model.SignatureStatus;
import com.flowforge.domain.signature.model.Signer;
import com.flowforge.domain.signature.model.SignerKind;
import com.flowforge.domain.signature.model.SignerStatus;
import com.flowforge.domain.signature.port.in.RequestSignatureUseCase;
import com.flowforge.domain.signature.port.out.SignatureAvailabilityPort;
import com.flowforge.domain.signature.port.out.SignatureConflictException;
import com.flowforge.domain.signature.port.out.SignatureMetricsPort;
import com.flowforge.domain.signature.port.out.SignatureOutcomeListener;
import com.flowforge.domain.signature.port.out.SignaturePort;
import com.flowforge.domain.signature.port.out.SignatureRequestRepository;
import com.flowforge.domain.workflow.portal.model.PortalEmails;
import com.flowforge.domain.workflow.portal.port.out.PortalRecipientResolverPort;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * TODO §72 — émission d'une demande. Ordre imposé par le stub (qui relit l'état FlowForge) : la
 * demande est insérée {@code PENDING} <strong>avant</strong> l'appel à {@link SignaturePort#submit},
 * puis mise à jour avec la référence du fournisseur. Toute erreur métier ou fournisseur ferme la
 * demande {@code FAILED} (motif consigné) au lieu de lever : le process ne doit jamais bloquer.
 */
public final class RequestSignatureUseCaseImpl implements RequestSignatureUseCase {

    static final String CREATED_BY = "flowable-delegate";
    private static final int MAX_USER_ID = 128;

    private final String tenantId;
    private final SignatureRequestRepository repository;
    private final SignaturePort port;
    private final SignatureAvailabilityPort availability;
    private final PortalRecipientResolverPort resolver;
    private final DossierElementRepository elementRepository;
    private final DocumentStoragePort storage;
    private final SignatureNotifier notifier;
    private final SignatureMetricsPort metrics;
    private final SignatureOutcomeListener outcomeListener;
    private final Clock clock;

    public RequestSignatureUseCaseImpl(
            String tenantId,
            SignatureRequestRepository repository,
            SignaturePort port,
            SignatureAvailabilityPort availability,
            PortalRecipientResolverPort resolver,
            DossierElementRepository elementRepository,
            DocumentStoragePort storage,
            SignatureNotifier notifier,
            SignatureMetricsPort metrics,
            SignatureOutcomeListener outcomeListener,
            Clock clock) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.port = Objects.requireNonNull(port, "port");
        this.availability = Objects.requireNonNull(availability, "availability");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.elementRepository = Objects.requireNonNull(elementRepository, "elementRepository");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.outcomeListener = Objects.requireNonNull(outcomeListener, "outcomeListener");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Result request(Command command) {
        SignatureBlockDefinition block = command.block();
        Optional<SignatureRequest> existing =
                repository.findPendingByProcessAndActivity(command.processInstanceId(), block.activityId());
        if (existing.isPresent()) {
            return new Result(existing.get().id(), SignatureStatus.PENDING, null);
        }
        Instant now = clock.instant();
        UUID requestId = UUID.randomUUID();
        // Bornes tenant revérifiées à l'exécution (elles ont pu baisser depuis le déploiement du BPMN).
        int deadlineDays = Math.min(
                block.deadlineDays() != null ? block.deadlineDays() : availability.defaultDeadlineDays(),
                availability.maxDeadlineDays());
        Instant deadline = now.plus(Duration.ofDays(deadlineDays));
        if (block.signers().size() > availability.maxSigners()) {
            return fail(command, requestId, now, deadline, null, null, null, "TOO_MANY_SIGNERS");
        }

        SignaturePort.SignatureProviderCapabilities capabilities = port.capabilities();
        if (block.mode() == SignatureMode.SEQUENTIAL && !capabilities.orderedSigning()) {
            return fail(command, requestId, now, deadline, null, null, null, "MODE_UNSUPPORTED");
        }
        if (block.signers().stream().anyMatch(s -> !s.required()) && !capabilities.optionalSigners()) {
            return fail(command, requestId, now, deadline, null, null, null, "OPTIONAL_UNSUPPORTED");
        }

        Optional<Object> documentRef = resolver.resolve(block.document(), command.dossierId(), command.processInstanceId());
        Optional<DossierElement> source = documentRef
                .map(Object::toString)
                .filter(ref -> !ref.isBlank())
                .flatMap(ref -> findProducedDoc(command.dossierId(), ref));
        if (source.isEmpty()) {
            return fail(command, requestId, now, deadline, null, null, null, "SOURCE_NOT_FOUND");
        }
        DossierElement element = source.get();
        byte[] content;
        try {
            content = storage.retrieve(tenantId, element.storageRef());
        } catch (RuntimeException ex) {
            return fail(command, requestId, now, deadline, element, null, null, "SOURCE_NOT_FOUND");
        }
        if (content == null || content.length == 0) {
            return fail(command, requestId, now, deadline, element, null, null, "SOURCE_NOT_FOUND");
        }
        if (content.length > availability.maxDocumentBytes()) {
            return fail(command, requestId, now, deadline, element, null, null, "SOURCE_TOO_LARGE");
        }
        // Seul un PDF est servi au signataire (en ligne, depuis l'origine de l'application) : tout autre
        // contenu — HTML, SVG… — est refusé dès l'émission (revue de sécurité, Phase 9).
        if (!isPdf(content)) {
            return fail(command, requestId, now, deadline, element, null, null, "SOURCE_NOT_PDF");
        }
        String digest = sha256(content);

        List<Signer> drafts = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        int position = 0;
        for (SignerDefinition definition : block.signers()) {
            Optional<Signer> signer = resolveSigner(command, requestId, position++, definition);
            if (signer.isEmpty()) {
                return fail(command, requestId, now, deadline, element, digest, null, "NO_ADDRESS");
            }
            if (!identities.add(signer.get().identityKey())) {
                return fail(command, requestId, now, deadline, element, digest, null, "DUPLICATE_SIGNER");
            }
            drafts.add(signer.get());
        }

        SignatureProgression.Result started = SignatureProgression.start(block.mode(), drafts);
        SignatureRequest pending = new SignatureRequest(requestId, tenantId, command.dossierId(),
                command.processInstanceId(), block.activityId(), element.id(), element.storageRef(), digest,
                port.code(), null, block.mode(), SignatureStatus.PENDING, null, false, null, deadline, null, 0, now,
                null, CREATED_BY, 0L);
        try {
            repository.insert(pending, started.signers());
        } catch (SignatureConflictException ex) {
            return repository.findPendingByProcessAndActivity(command.processInstanceId(), block.activityId())
                    .map(r -> new Result(r.id(), SignatureStatus.PENDING, null))
                    .orElseThrow(() -> ex);
        }

        SignaturePort.SignatureSubmission submission;
        try {
            submission = port.submit(new SignaturePort.SubmitCommand(
                    requestId,
                    element.label(),
                    PDF_CONTENT_TYPE,
                    content,
                    digest,
                    started.signers().stream()
                            .map(s -> new SignaturePort.SubmitSigner(
                                    s.id(), s.position(), s.kind(), s.required(), s.displayName(), s.email()))
                            .toList(),
                    block.mode(),
                    null,
                    deadline,
                    null));
        } catch (RuntimeException ex) {
            return closeFailed(pending, started.signers(), "PROVIDER_ERROR", now);
        }
        SignatureRequest submitted = pending.withProviderReference(submission.providerReference())
                .withSimulation(submission.simulated(), submission.assuranceLevel());
        repository.updateRequest(submitted);
        metrics.requestCreated(port.code(), submission.simulated());
        for (Signer internal : started.newlyPending()) {
            if (internal.kind() == SignerKind.INTERNAL) {
                notifier.notifyInternalSigner(submitted, internal.userId());
            }
        }
        return new Result(requestId, SignatureStatus.PENDING, null);
    }

    private Result closeFailed(SignatureRequest pending, List<Signer> signers, String reason, Instant now) {
        SignatureProgression.Result cancelled = SignatureProgression.cancel(signers, now);
        SignatureRequest failed = pending.closed(SignatureOutcome.FAILED, reason, now);
        repository.update(failed, cancelled.signers());
        recordFailure(failed);
        return new Result(pending.id(), SignatureStatus.FAILED, reason);
    }

    /** Demande créée directement {@code FAILED} : jamais de signataire, jamais d'appel au fournisseur. */
    private Result fail(
            Command command,
            UUID requestId,
            Instant now,
            Instant deadline,
            DossierElement element,
            String digest,
            String providerReference,
            String reason) {
        SignatureRequest failed = new SignatureRequest(requestId, tenantId, command.dossierId(),
                command.processInstanceId(), command.block().activityId(), element == null ? null : element.id(),
                element == null ? null : element.storageRef(), digest, port.code(), providerReference,
                command.block().mode(), SignatureStatus.FAILED, reason, false, null, deadline, null, 0, now, now,
                CREATED_BY, 0L);
        repository.insert(failed, List.of());
        recordFailure(failed);
        return new Result(requestId, SignatureStatus.FAILED, reason);
    }

    /** Demande fermée {@code FAILED} : métriques, notification des participants, reprise du process. */
    private void recordFailure(SignatureRequest failed) {
        metrics.requestCreated(failed.providerCode(), false);
        metrics.outcome(SignatureOutcome.FAILED);
        notifier.notifyOutcome(failed, SignatureOutcome.FAILED);
        outcomeListener.outcomeRecorded(failed.id());
    }

    private Optional<DossierElement> findProducedDoc(UUID dossierId, String storageRef) {
        return elementRepository.findByDossierIdOrderByAddedAtAsc(dossierId).stream()
                .filter(e -> e.type() == ElementType.PRODUCED_DOC && storageRef.equals(e.storageRef()))
                .findFirst();
    }

    private Optional<Signer> resolveSigner(Command command, UUID requestId, int position, SignerDefinition definition) {
        Optional<Object> raw = resolver.resolve(definition.source(), command.dossierId(), command.processInstanceId());
        if (definition.kind() == SignerKind.EXTERNAL) {
            return raw.flatMap(PortalEmails::normalize)
                    .map(email -> new Signer(UUID.randomUUID(), tenantId, requestId, position, SignerKind.EXTERNAL,
                            definition.required(), null, definition.name(), email, null, SignerStatus.WAITING, null, 0,
                            null, null, 0L));
        }
        return raw.filter(String.class::isInstance)
                .map(v -> ((String) v).trim())
                .filter(v -> !v.isEmpty() && v.length() <= MAX_USER_ID && v.chars().noneMatch(Character::isISOControl))
                .map(userId -> new Signer(UUID.randomUUID(), tenantId, requestId, position, SignerKind.INTERNAL,
                        definition.required(), userId, definition.name(), null, null, SignerStatus.WAITING, null, 0,
                        null, null, 0L));
    }

    static final String PDF_CONTENT_TYPE = "application/pdf";

    private static boolean isPdf(byte[] content) {
        return content.length >= 5 && content[0] == '%' && content[1] == 'P' && content[2] == 'D'
                && content[3] == 'F' && content[4] == '-';
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
