package be.myrmacta.infrastructure.persistence.precheck;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.precheck.model.FindingKind;
import be.myrmacta.domain.precheck.model.PrecheckFinding;
import be.myrmacta.domain.precheck.model.PrecheckOutcome;
import be.myrmacta.domain.precheck.model.PrecheckRun;
import be.myrmacta.domain.precheck.model.PrecheckSpec;
import be.myrmacta.domain.precheck.port.out.PrecheckRunRepository;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** TODO §106 (lot B, C2, HC9, D-PRECHECK-STORE, D-PRECHECK-QUEUE) — tables 040 et file avec bail sur PostgreSQL. */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class JdbcPrecheckRunRepositoryIT {

    private static final PrecheckSpec SPEC = PrecheckSpec.parse(Map.of(
            PrecheckSpec.PIECES, "preuve : obligatoire, lire", PrecheckSpec.FIELDS, "commune",
            PrecheckSpec.QUESTIONS, "q = Question ?",
            PrecheckSpec.RULES, "variable:montant = \"élevé\" => A_VERIFIER : Montant à contrôler."), "check");

    @Autowired private PrecheckRunRepository runs;
    @Autowired private DossierRepository dossiers;
    @Autowired private DossierTypeRepository types;
    @Autowired private JdbcTemplate jdbc;

    private Dossier dossier;
    private Instant now;

    @BeforeEach
    void dossierExists() {
        // Une file par instance : les exécutions laissées par un autre test ne doivent pas être prises ici.
        jdbc.update("delete from precheck_run");
        String type = "PRQ" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        types.save(new DossierType(UUID.randomUUID(), "tenant_dev", type, "File", "wf", true, false, List.of(), null,
                false, null, "u", "u"));
        dossier = dossiers.save(new Dossier(UUID.randomUUID(), "tenant_dev", type + "-1", type, "D", DossierStatus.EN_COURS,
                Map.of(), null, 0L, "test", Instant.now(), null, null, null, null, null));
        now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private PrecheckRun manual(Instant createdAt) {
        return PrecheckRun.pending(dossier.id(), PrecheckRun.Trigger.MANUAL, "agent", null, SPEC, createdAt);
    }

    private static PrecheckOutcome outcome(long tokens) {
        return new PrecheckOutcome(PrecheckRun.Status.SUCCEEDED, null, "fake", "m", "v1", "a".repeat(64), "Synthèse",
                tokens, 0, 1, List.of(
                        PrecheckFinding.deterministic(0, FindingKind.MISSING, PrecheckFinding.SourceType.PIECE, "preuve",
                                "Pièce manquante : Preuve."),
                        new PrecheckFinding(UUID.randomUUID(), 1, FindingKind.INCONSISTENT, PrecheckFinding.Origin.MODEL,
                                PrecheckFinding.SourceType.QUESTION, "q", "Non.", 0.75, null, null, null)));
    }

    @Test
    void aRunRoundTripsWithItsSpecificationAndFindings() {
        PrecheckRun run = PrecheckRun.pending(dossier.id(), PrecheckRun.Trigger.WORKFLOW, "system", null, SPEC, now)
                .completed(outcome(42), now);
        assertThat(runs.insert(run)).isTrue();

        PrecheckRun read = runs.findById(run.id()).orElseThrow();
        assertThat(read.spec()).isEqualTo(SPEC);
        assertThat(read.status()).isEqualTo(PrecheckRun.Status.SUCCEEDED);
        assertThat(read.summary()).isEqualTo("Synthèse");
        assertThat(read.findings()).extracting(PrecheckFinding::message).containsExactly("Pièce manquante : Preuve.", "Non.");
        assertThat(read.findings().get(0).confidence()).isNull();
        assertThat(read.findings().get(1).confidence()).isEqualTo(0.75);
        assertThat(runs.tokensSince(now.minusSeconds(1))).isEqualTo(42);
        assertThat(runs.tokensSince(now.plusSeconds(1))).isZero();

        UUID finding = read.findings().get(1).id();
        assertThat(runs.decide(run.id(), finding, PrecheckFinding.Decision.DISMISSED, "agent", now)).isTrue();
        assertThat(runs.findById(run.id()).orElseThrow().findings().get(1)).satisfies(f -> {
            assertThat(f.decision()).isEqualTo(PrecheckFinding.Decision.DISMISSED);
            assertThat(f.decidedBy()).isEqualTo("agent");
        });
        assertThat(runs.decide(run.id(), finding, null, "agent", now)).isTrue();
        assertThat(runs.findById(run.id()).orElseThrow().findings().get(1).decidedBy()).isNull();
        assertThat(runs.decide(UUID.randomUUID(), finding, PrecheckFinding.Decision.ACCEPTED, "agent", now)).isFalse();
    }

    @Test
    void onlyOneQueuedRunPerDossierButWorkflowRunsAreNotQueued() {
        assertThat(runs.insert(manual(now))).isTrue();
        assertThat(runs.insert(manual(now.plusSeconds(1)))).isFalse();
        PrecheckRun workflow = PrecheckRun.pending(dossier.id(), PrecheckRun.Trigger.WORKFLOW, "system", null, SPEC, now)
                .completed(outcome(1), now);
        assertThat(runs.insert(workflow)).isTrue();
        assertThat(runs.listByDossier(dossier.id(), 10)).hasSize(2);
    }

    @Test
    void theQueueTakesARunWithALeaseAndRetakesItOnlyOnceTheLeaseHasExpired() {
        PrecheckRun queued = manual(now);
        runs.insert(queued);

        PrecheckRun taken = runs.lockNext(now, now.plusSeconds(300)).orElseThrow();
        assertThat(taken.id()).isEqualTo(queued.id());
        assertThat(taken.status()).isEqualTo(PrecheckRun.Status.RUNNING);
        assertThat(taken.attempts()).isEqualTo(1);
        assertThat(taken.spec()).isEqualTo(SPEC);
        assertThat(runs.lockNext(now.plusSeconds(10), now.plusSeconds(310))).isEmpty();

        PrecheckRun retaken = runs.lockNext(now.plusSeconds(301), now.plusSeconds(601)).orElseThrow();
        assertThat(retaken.id()).isEqualTo(queued.id());
        assertThat(retaken.attempts()).isEqualTo(2);

        // F4 : la première tentative, dépassée, ne peut plus ranger son résultat ; la seconde, si.
        assertThat(runs.complete(taken.completed(outcome(9), now.plusSeconds(302)))).isFalse();
        assertThat(runs.findById(queued.id()).orElseThrow().status()).isEqualTo(PrecheckRun.Status.RUNNING);
        assertThat(runs.complete(retaken.completed(outcome(5), now.plusSeconds(302)))).isTrue();
        assertThat(runs.complete(retaken.completed(outcome(5), now.plusSeconds(303)))).isFalse(); // déjà terminée
        assertThat(runs.lockNext(now.plusSeconds(10_000), now.plusSeconds(10_300))).isEmpty();
        PrecheckRun done = runs.findById(queued.id()).orElseThrow();
        assertThat(done.status()).isEqualTo(PrecheckRun.Status.SUCCEEDED);
        assertThat(done.findings()).hasSize(2);
        // Exécution terminée : une nouvelle relance redevient possible.
        assertThat(runs.insert(manual(now.plusSeconds(400)))).isTrue();
    }

    /** Revue adversariale de #211, F3 — les relances se comptent par dossier ; les exécutions du workflow n'entrent pas. */
    @Test
    void rerunsAreCountedPerDossierWithoutWorkflowRuns() {
        runs.insert(manual(now.minusSeconds(3600)).completed(outcome(1), now.minusSeconds(3500)));
        runs.insert(manual(now.minusSeconds(200_000)).completed(outcome(1), now.minusSeconds(199_000)));
        runs.insert(PrecheckRun.pending(dossier.id(), PrecheckRun.Trigger.WORKFLOW, "system", null, SPEC, now)
                .completed(outcome(1), now));
        assertThat(runs.countRequestsSince(dossier.id(), now.minusSeconds(86_400))).isEqualTo(1);
        assertThat(runs.countRequestsSince(UUID.randomUUID(), now.minusSeconds(86_400))).isZero();
    }

    @Test
    void deletingTheDossierDeletesItsRunsAndFindings() {
        PrecheckRun run = PrecheckRun.pending(dossier.id(), PrecheckRun.Trigger.WORKFLOW, "system", null, SPEC, now)
                .completed(outcome(1), now);
        runs.insert(run);
        jdbc.update("delete from dossier where id = ?", dossier.id());
        assertThat(runs.findById(run.id())).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from precheck_finding where run_id = ?", Long.class, run.id()))
                .isZero();
    }
}
