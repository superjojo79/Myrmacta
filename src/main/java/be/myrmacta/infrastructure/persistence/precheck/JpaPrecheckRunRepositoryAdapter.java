package be.myrmacta.infrastructure.persistence.precheck;

import be.myrmacta.domain.precheck.model.PrecheckFinding;
import be.myrmacta.domain.precheck.model.PrecheckRun;
import be.myrmacta.domain.precheck.port.out.PrecheckRunRepository;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §106 (issue #211, lot B, HC5, HC9, C2, D-PRECHECK-STORE) — tables {@code precheck_run} et
 * {@code precheck_finding} (changeSet 040). Chaque écriture a sa propre transaction ({@code REQUIRES_NEW}) : le résultat
 * d'une exécution reste rangé même quand le job du workflow qui l'a lancée échoue ensuite (incident rejouable).
 */
@Component
class JpaPrecheckRunRepositoryAdapter implements PrecheckRunRepository {

    private final SpringDataPrecheckRunRepository runs;
    private final SpringDataPrecheckFindingRepository findings;
    private final ObjectMapper mapper;
    private final String tenantId;

    JpaPrecheckRunRepositoryAdapter(SpringDataPrecheckRunRepository runs, SpringDataPrecheckFindingRepository findings,
            ObjectMapper mapper, TenantProperties tenantProperties) {
        this.runs = runs;
        this.findings = findings;
        this.mapper = mapper;
        this.tenantId = tenantProperties.id();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean insert(PrecheckRun run) {
        if (run.trigger() != PrecheckRun.Trigger.WORKFLOW) {
            // C2 : au plus une relance en file par dossier. Le verrou par dossier sérialise contrôle et insertion
            // (l'index unique partiel reste le filet) sans passer par une violation, qui avorterait la transaction.
            runs.lockDossier(run.dossierId());
            if (runs.hasActiveRequest(run.dossierId())) {
                return false;
            }
        }
        runs.saveAndFlush(PrecheckRunJpaEntity.fromDomain(run, tenantId, mapper));
        saveFindings(run.id(), run.findings());
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean complete(PrecheckRun run) {
        int updated = runs.complete(run.id(), run.attempts(), run.status().name(), run.failure(), run.provider(),
                run.model(), run.promptVersion(), run.inputSha256(), run.summary(), run.tokensIn(), run.tokensOut(),
                run.discarded(), run.finishedAt());
        if (updated == 0) {
            return false; // F4 : une tentative plus récente détient l'exécution
        }
        findings.deleteByRun(run.id());
        saveFindings(run.id(), run.findings());
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PrecheckRun> findById(UUID runId) {
        return withFindings(runs.findByIdAndTenantId(runId, tenantId).stream().toList()).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PrecheckRun> listByDossier(UUID dossierId, int limit) {
        return withFindings(runs.listByDossier(dossierId, tenantId, Limit.of(limit)));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<PrecheckRun> lockNext(Instant now, Instant leaseUntil) {
        return runs.lockNext(tenantId, now, leaseUntil).map(entity -> entity.toDomain(mapper, List.of()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean decide(UUID runId, UUID findingId, PrecheckFinding.Decision decision, String by, Instant at) {
        return findings.decide(runId, findingId, decision == null ? null : decision.name(),
                decision == null ? null : by, decision == null ? null : at) == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public int countRequestsSince(UUID dossierId, Instant since) {
        return runs.countRequestsSince(dossierId, tenantId, since);
    }

    @Override
    @Transactional(readOnly = true)
    public long tokensSince(Instant since) {
        return runs.tokensSince(tenantId, since);
    }

    private void saveFindings(UUID runId, List<PrecheckFinding> toSave) {
        findings.saveAll(toSave.stream().map(f -> PrecheckFindingJpaEntity.fromDomain(runId, f)).toList());
    }

    private List<PrecheckRun> withFindings(List<PrecheckRunJpaEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<PrecheckFinding>> byRun = new LinkedHashMap<>();
        entities.forEach(e -> byRun.put(e.id(), new ArrayList<>()));
        findings.findByRunIdInOrderByOrdinal(byRun.keySet()).forEach(f -> byRun.get(f.runId()).add(f.toDomain()));
        return entities.stream().map(e -> e.toDomain(mapper, byRun.get(e.id()))).toList();
    }
}
