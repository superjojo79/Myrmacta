package be.myrmacta.infrastructure.persistence.precheck;

import be.myrmacta.domain.precheck.model.FindingKind;
import be.myrmacta.domain.precheck.model.PrecheckFinding;
import be.myrmacta.domain.precheck.model.PrecheckRun;
import be.myrmacta.domain.precheck.model.PrecheckSpec;
import be.myrmacta.domain.precheck.port.out.PrecheckRunRepository;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
class JdbcPrecheckRunRepository implements PrecheckRunRepository {

    private static final String RUN_COLUMNS = """
            id, dossier_id, trigger_kind, requested_by, workflow_link_id, spec_json, status, attempts, failure, provider,
            model, prompt_version, input_sha256, summary, tokens_in, tokens_out, discarded, created_at, finished_at""";
    private static final String ACTIVE = "status in ('PENDING', 'RUNNING') and trigger_kind <> 'WORKFLOW'";

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final ObjectMapper mapper;
    private final String tenantId;

    JdbcPrecheckRunRepository(JdbcTemplate jdbc, ObjectMapper mapper, TenantProperties tenantProperties) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
        this.mapper = mapper;
        this.tenantId = tenantProperties.id();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean insert(PrecheckRun run) {
        Object[] values = {run.id(), tenantId, run.dossierId(), run.trigger().name(), run.requestedBy(),
                run.workflowLinkId(), mapper.writeValueAsString(run.spec()), run.status().name(), run.attempts(),
                run.failure(), run.provider(), run.model(), run.promptVersion(), run.inputSha256(), run.summary(),
                run.tokensIn(), run.tokensOut(), run.discarded(), ts(run.createdAt()), ts(run.finishedAt())};
        int inserted;
        try {
            if (run.trigger() == PrecheckRun.Trigger.WORKFLOW) {
                inserted = jdbc.update("insert into precheck_run (id, tenant_id, " + RUN_COLUMNS.substring(4)
                        + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", values);
            } else {
                Object[] guarded = java.util.Arrays.copyOf(values, values.length + 1);
                guarded[values.length] = run.dossierId();
                inserted = jdbc.update("insert into precheck_run (id, tenant_id, " + RUN_COLUMNS.substring(4)
                        + ") select ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? where not exists "
                        + "(select 1 from precheck_run where dossier_id = ? and " + ACTIVE + ")", guarded);
            }
        } catch (DuplicateKeyException concurrent) {
            return false; // index unique partiel : une relance concurrente l'a emporté
        }
        if (inserted == 1) {
            insertFindings(run.id(), run.findings());
        }
        return inserted == 1;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(PrecheckRun run) {
        jdbc.update("""
                update precheck_run set status = ?, failure = ?, provider = ?, model = ?, prompt_version = ?,
                    input_sha256 = ?, summary = ?, tokens_in = ?, tokens_out = ?, discarded = ?, finished_at = ?,
                    lease_until = null
                where id = ?
                """, run.status().name(), run.failure(), run.provider(), run.model(), run.promptVersion(),
                run.inputSha256(), run.summary(), run.tokensIn(), run.tokensOut(), run.discarded(), ts(run.finishedAt()),
                run.id());
        jdbc.update("delete from precheck_finding where run_id = ?", run.id());
        insertFindings(run.id(), run.findings());
    }

    @Override
    public Optional<PrecheckRun> findById(UUID runId) {
        List<PrecheckRun> runs = jdbc.query("select " + RUN_COLUMNS + " from precheck_run where id = ? and tenant_id = ?",
                (rs, i) -> run(rs, List.of()), runId, tenantId);
        return withFindings(runs).stream().findFirst();
    }

    @Override
    public List<PrecheckRun> listByDossier(UUID dossierId, int limit) {
        return withFindings(jdbc.query("select " + RUN_COLUMNS + " from precheck_run where dossier_id = ? and tenant_id = ? "
                + "order by created_at desc, id limit ?", (rs, i) -> run(rs, List.of()), dossierId, tenantId, limit));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<PrecheckRun> lockNext(Instant now, Instant leaseUntil) {
        return jdbc.query("update precheck_run set status = 'RUNNING', attempts = attempts + 1, lease_until = ? "
                + "where id = (select id from precheck_run where tenant_id = ? and trigger_kind <> 'WORKFLOW' and "
                + "(status = 'PENDING' or (status = 'RUNNING' and lease_until < ?)) order by created_at limit 1 "
                + "for update skip locked) returning " + RUN_COLUMNS, (rs, i) -> run(rs, List.of()),
                ts(leaseUntil), tenantId, ts(now)).stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean decide(UUID runId, UUID findingId, PrecheckFinding.Decision decision, String by, Instant at) {
        return jdbc.update("update precheck_finding set decision = ?, decided_by = ?, decided_at = ? where id = ? and run_id = ?",
                decision == null ? null : decision.name(), decision == null ? null : by,
                decision == null ? null : ts(at), findingId, runId) == 1;
    }

    @Override
    public long tokensSince(Instant since) {
        Long total = jdbc.queryForObject("select coalesce(sum(tokens_in + tokens_out), 0) from precheck_run "
                + "where tenant_id = ? and finished_at >= ?", Long.class, tenantId, ts(since));
        return total == null ? 0 : total;
    }

    private void insertFindings(UUID runId, List<PrecheckFinding> findings) {
        for (PrecheckFinding f : findings) {
            jdbc.update("""
                    insert into precheck_finding (id, run_id, ordinal, kind, origin, source_type, source_ref, message,
                        confidence, decision, decided_by, decided_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, f.id(), runId, f.ordinal(), f.kind().name(), f.origin().name(), f.sourceType().name(),
                    PrecheckFinding.truncate(f.sourceRef(), 100), f.message(), f.confidence(),
                    f.decision() == null ? null : f.decision().name(), f.decidedBy(), ts(f.decidedAt()));
        }
    }

    private List<PrecheckRun> withFindings(List<PrecheckRun> runs) {
        if (runs.isEmpty()) {
            return runs;
        }
        Map<UUID, List<PrecheckFinding>> byRun = new LinkedHashMap<>();
        runs.forEach(run -> byRun.put(run.id(), new ArrayList<>()));
        named.query("select * from precheck_finding where run_id in (:ids) order by ordinal",
                new MapSqlParameterSource("ids", byRun.keySet()),
                rs -> {
                    byRun.get(rs.getObject("run_id", UUID.class)).add(finding(rs));
                });
        return runs.stream().map(run -> withFindings(run, byRun.get(run.id()))).toList();
    }

    private static PrecheckRun withFindings(PrecheckRun run, List<PrecheckFinding> findings) {
        return new PrecheckRun(run.id(), run.dossierId(), run.trigger(), run.requestedBy(), run.workflowLinkId(),
                run.spec(), run.status(), run.attempts(), run.failure(), run.provider(), run.model(), run.promptVersion(),
                run.inputSha256(), run.summary(), run.tokensIn(), run.tokensOut(), run.discarded(), run.createdAt(),
                run.finishedAt(), findings);
    }

    private PrecheckRun run(ResultSet rs, List<PrecheckFinding> findings) throws SQLException {
        return new PrecheckRun(rs.getObject("id", UUID.class), rs.getObject("dossier_id", UUID.class),
                PrecheckRun.Trigger.valueOf(rs.getString("trigger_kind")), rs.getString("requested_by"),
                rs.getObject("workflow_link_id", UUID.class),
                mapper.readValue(rs.getString("spec_json"), PrecheckSpec.class),
                PrecheckRun.Status.valueOf(rs.getString("status")), rs.getInt("attempts"), rs.getString("failure"),
                rs.getString("provider"), rs.getString("model"), rs.getString("prompt_version"),
                rs.getString("input_sha256"), rs.getString("summary"), rs.getLong("tokens_in"), rs.getLong("tokens_out"),
                rs.getInt("discarded"), instant(rs, "created_at"), instant(rs, "finished_at"), findings);
    }

    private static PrecheckFinding finding(ResultSet rs) throws SQLException {
        String decision = rs.getString("decision");
        double raw = rs.getDouble("confidence");
        Double confidence = rs.wasNull() ? null : raw;
        return new PrecheckFinding(rs.getObject("id", UUID.class), rs.getInt("ordinal"),
                FindingKind.valueOf(rs.getString("kind")), PrecheckFinding.Origin.valueOf(rs.getString("origin")),
                PrecheckFinding.SourceType.valueOf(rs.getString("source_type")), rs.getString("source_ref"),
                rs.getString("message"), confidence,
                decision == null ? null : PrecheckFinding.Decision.valueOf(decision), rs.getString("decided_by"),
                instant(rs, "decided_at"));
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
