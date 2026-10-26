package be.myrmacta.domain.precheck.usecase;

import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.precheck.PrecheckBudget;
import be.myrmacta.domain.precheck.model.PrecheckConflictException;
import be.myrmacta.domain.precheck.model.PrecheckExceptions.PrecheckDisabledException;
import be.myrmacta.domain.precheck.model.PrecheckFinding;
import be.myrmacta.domain.precheck.model.PrecheckNotFoundException;
import be.myrmacta.domain.precheck.model.PrecheckRateLimitedException;
import be.myrmacta.domain.precheck.model.PrecheckRun;
import be.myrmacta.domain.precheck.port.in.ManageDossierPrechecksUseCase;
import be.myrmacta.domain.precheck.port.out.PrecheckAvailabilityPort;
import be.myrmacta.domain.precheck.port.out.PrecheckRunRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** TODO §106 (issue #211, lot B, P9b, R7, H7, HC10). */
public final class ManageDossierPrechecksUseCaseImpl implements ManageDossierPrechecksUseCase {

    private final String tenantId;
    private final DossierRepository dossierRepository;
    private final DossierAccessPolicy accessPolicy;
    private final PrecheckRunRepository runRepository;
    private final PrecheckAvailabilityPort availability;
    private final PrecheckBudget budget;
    private final int maxRerunsPerDossierPerDay;
    private final Clock clock;

    public ManageDossierPrechecksUseCaseImpl(String tenantId, DossierRepository dossierRepository,
            DossierAccessPolicy accessPolicy, PrecheckRunRepository runRepository, PrecheckAvailabilityPort availability,
            PrecheckBudget budget, int maxRerunsPerDossierPerDay, Clock clock) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository");
        this.availability = Objects.requireNonNull(availability, "availability");
        this.budget = Objects.requireNonNull(budget, "budget");
        if (maxRerunsPerDossierPerDay < 1) {
            throw new IllegalArgumentException("maxRerunsPerDossierPerDay must be positive");
        }
        this.maxRerunsPerDossierPerDay = maxRerunsPerDossierPerDay;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PrecheckRun request(UUID dossierId, AccessContext.User actor, PrecheckRun.Trigger trigger) {
        Objects.requireNonNull(actor, "actor");
        if (trigger == PrecheckRun.Trigger.WORKFLOW) {
            throw new IllegalArgumentException("a relaunch is MANUAL or MCP");
        }
        if (!availability.enabled()) {
            throw new PrecheckDisabledException();
        }
        Dossier dossier = editable(dossierId, actor);
        PrecheckRun last = runRepository.listByDossier(dossier.id(), 1).stream().findFirst()
                .orElseThrow(() -> new PrecheckConflictException(
                        "no previous pre-check to rerun on this dossier (the workflow brick runs the first one)"));
        // F3 — un agent ou un client MCP ne doit pas pouvoir épuiser le plafond de l'instance par des relances en boucle.
        Instant now = clock.instant();
        if (runRepository.countRequestsSince(dossier.id(), now.minus(Duration.ofDays(1))) >= maxRerunsPerDossierPerDay) {
            throw new PrecheckRateLimitedException("at most " + maxRerunsPerDossierPerDay
                    + " pre-check reruns per dossier in 24 hours");
        }
        if (!budget.rerunAllowed()) {
            throw new PrecheckRateLimitedException("today's AI pre-check budget is nearly used: reruns are suspended, "
                    + "the rest is kept for the workflows");
        }
        PrecheckRun run = PrecheckRun.pending(dossier.id(), trigger, actor.userId(), null, last.spec(), now);
        if (!runRepository.insert(run)) {
            throw new PrecheckConflictException("a pre-check is already queued or running on this dossier");
        }
        return run;
    }

    @Override
    public List<PrecheckRun> list(UUID dossierId, AccessContext actor) {
        Dossier dossier = dossier(dossierId);
        accessPolicy.assertCanView(dossier, actor);
        return runRepository.listByDossier(dossier.id(), LIST_LIMIT);
    }

    @Override
    public PrecheckRun export(UUID dossierId, UUID runId, AccessContext.User actor) {
        Objects.requireNonNull(actor, "actor");
        if (!actor.hasRole("TENANT_ADMIN")) {
            throw new AccessDeniedException("exporting a pre-check log requires TENANT_ADMIN");
        }
        return runOf(dossier(dossierId), runId);
    }

    @Override
    public Decided decide(UUID dossierId, UUID runId, UUID findingId, PrecheckFinding.Decision decision,
            AccessContext.User actor) {
        Dossier dossier = editable(dossierId, actor);
        PrecheckRun run = runOf(dossier, runId);
        if (!run.finished()) {
            throw new PrecheckConflictException("the pre-check is still running");
        }
        PrecheckFinding.Decision previous = run.findings().stream()
                .filter(f -> f.id().equals(findingId))
                .findFirst()
                .map(PrecheckFinding::decision)
                .orElse(null);
        if (!runRepository.decide(run.id(), findingId, decision, actor.userId(), clock.instant())) {
            throw new PrecheckNotFoundException("finding not found: " + findingId);
        }
        PrecheckFinding decided = runOf(dossier, runId).findings().stream()
                .filter(f -> f.id().equals(findingId))
                .findFirst()
                .orElseThrow(() -> new PrecheckNotFoundException("finding not found: " + findingId));
        return new Decided(decided, previous);
    }

    private Dossier editable(UUID dossierId, AccessContext.User actor) {
        Dossier dossier = dossier(dossierId);
        accessPolicy.assertCanEdit(dossier, actor);
        if (dossier.status().blocksMutation()) {
            throw new PrecheckConflictException("the dossier is closed (" + dossier.status() + ")");
        }
        return dossier;
    }

    private Dossier dossier(UUID dossierId) {
        Objects.requireNonNull(dossierId, "dossierId");
        return dossierRepository.findById(dossierId)
                .filter(d -> tenantId.equals(d.tenantId()))
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + dossierId));
    }

    private PrecheckRun runOf(Dossier dossier, UUID runId) {
        return runRepository.findById(runId)
                .filter(run -> run.dossierId().equals(dossier.id()))
                .orElseThrow(() -> new PrecheckNotFoundException("pre-check not found: " + runId));
    }
}
