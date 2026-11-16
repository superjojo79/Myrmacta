package be.myrmacta.infrastructure.web.precheck;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.precheck.model.PrecheckFinding;
import be.myrmacta.domain.precheck.model.PrecheckRun;
import be.myrmacta.domain.precheck.port.in.ManageDossierPrechecksUseCase;
import be.myrmacta.domain.user.port.out.KnownUserRepository;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.security.SecurityAuditLog;
import be.myrmacta.infrastructure.web.dossier.AccessContextFactory;
import be.myrmacta.infrastructure.web.user.UserDisplayNames;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TODO §106 (issue #211, lot B, P9b, R7) — pré-vérifications d'un dossier. Absent si {@code app.precheck.enabled=false}
 * (404 : le panneau ne s'affiche pas).
 * TODO §146 (D-PRECHECK-DECIDER-NAME) — les constats exposent aussi le nom de qui a décidé (annuaire D-ASSIGN-DIR).
 */
@RestController
@RequestMapping("/api/dossiers/{id}/prechecks")
@ConditionalOnProperty(prefix = "app.precheck", name = "enabled", havingValue = "true")
class DossierPrecheckController {

    private final ManageDossierPrechecksUseCase useCase;
    private final AccessContextFactory accessContextFactory;
    private final UserDisplayNames directory;

    DossierPrecheckController(
            ManageDossierPrechecksUseCase useCase,
            AccessContextFactory accessContextFactory,
            TenantProperties tenantProperties,
            KnownUserRepository knownUserRepository) {
        this.useCase = useCase;
        this.accessContextFactory = accessContextFactory;
        this.directory = new UserDisplayNames(tenantProperties, knownUserRepository);
    }

    @GetMapping
    List<PrecheckRunResponse> list(@PathVariable UUID id, Authentication authentication) {
        Map<String, String> names = directory.asMap();
        return useCase.list(id, accessContextFactory.user(authentication)).stream()
                .map(run -> PrecheckRunResponse.from(run, directory, names))
                .toList();
    }

    /** Relance avec la spécification de la dernière exécution ; traitée en tâche de fond (202). */
    @PostMapping
    ResponseEntity<PrecheckRunResponse> rerun(@PathVariable UUID id, Authentication authentication) {
        PrecheckRun run = useCase.request(id, accessContextFactory.user(authentication), PrecheckRun.Trigger.MANUAL);
        SecurityAuditLog.precheckRerunRequested("rest", authentication.getName(), id, run.id());
        Map<String, String> names = directory.asMap();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(PrecheckRunResponse.from(run, directory, names));
    }

    @PutMapping("/{runId}/findings/{findingId}/decision")
    PrecheckRunResponse.Finding decide(@PathVariable UUID id, @PathVariable UUID runId, @PathVariable UUID findingId,
            @RequestBody(required = false) DecisionRequest request, Authentication authentication) {
        AccessContext.User actor = accessContextFactory.user(authentication);
        PrecheckFinding.Decision decision = request == null || request.decision() == null || request.decision().isBlank()
                ? null
                : PrecheckFinding.Decision.valueOf(request.decision().strip());
        ManageDossierPrechecksUseCase.Decided decided = useCase.decide(id, runId, findingId, decision, actor);
        SecurityAuditLog.precheckFindingDecided(authentication.getName(), id, runId, findingId,
                decided.previous() == null ? null : decided.previous().name(), decision == null ? null : decision.name());
        return PrecheckRunResponse.Finding.from(decided.finding(), directory, directory.asMap());
    }

    @GetMapping("/{runId}/export")
    PrecheckExportResponse export(@PathVariable UUID id, @PathVariable UUID runId, Authentication authentication) {
        Map<String, String> names = directory.asMap();
        PrecheckExportResponse export = PrecheckExportResponse.from(
                useCase.export(id, runId, accessContextFactory.user(authentication)), directory, names);
        SecurityAuditLog.precheckLogExported(authentication.getName(), id, runId);
        return export;
    }

    record DecisionRequest(String decision) {}
}
