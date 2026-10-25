package be.myrmacta.infrastructure.web.parcours;

import be.myrmacta.domain.parcours.model.CompiledParcours;
import be.myrmacta.domain.parcours.model.Parcours;
import be.myrmacta.domain.parcours.model.ParcoursDefinition;
import be.myrmacta.domain.parcours.port.in.CompileParcoursUseCase;
import be.myrmacta.domain.parcours.port.in.ManageParcoursUseCase;
import be.myrmacta.domain.workflow.model.Provenance;
import be.myrmacta.infrastructure.web.dossier.AccessContextFactory;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TODO §88 (LC5) — parcours : une suite d'étapes compilée en BPMN (sens unique). {@code TENANT_ADMIN} et {@code WORKFLOW_ADMIN}.
 * La compilation dépose un brouillon BPMN de l'éditeur (provenance {@code PARCOURS}) que l'on relit puis publie depuis Admin → Brouillons.
 */
@RestController
@RequestMapping("/api/parcours")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN')")
class ParcoursController {

    private final ManageParcoursUseCase manage;
    private final CompileParcoursUseCase compile;
    private final AccessContextFactory accessContextFactory;

    ParcoursController(ManageParcoursUseCase manage, CompileParcoursUseCase compile, AccessContextFactory accessContextFactory) {
        this.manage = manage;
        this.compile = compile;
        this.accessContextFactory = accessContextFactory;
    }

    @GetMapping
    List<ParcoursSummary> list(Authentication authentication) {
        return manage.list(accessContextFactory.user(authentication)).stream().map(ParcoursSummary::from).toList();
    }

    @GetMapping("/{key}")
    ParcoursView get(@PathVariable String key, Authentication authentication) {
        return ParcoursView.from(manage.get(accessContextFactory.user(authentication), key));
    }

    @PutMapping("/{key}")
    ParcoursView save(@PathVariable String key, @RequestBody SaveRequest request, Authentication authentication) {
        return ParcoursView.from(manage.save(accessContextFactory.user(authentication),
                new ManageParcoursUseCase.SaveCommand(key, request.label(), request.definition(), request.expectedVersion())));
    }

    @DeleteMapping("/{key}")
    ResponseEntity<Void> delete(
            @PathVariable String key,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer expectedVersion,
            Authentication authentication) {
        manage.delete(accessContextFactory.user(authentication), key, expectedVersion);
        return ResponseEntity.noContent().build();
    }

    /** Compile le parcours enregistré et dépose le brouillon BPMN (ou valide seulement avec {@code dryRun}). */
    @PostMapping("/{key}/compile")
    CompileResponse compile(@PathVariable String key, @RequestBody(required = false) CompileRequest request, Authentication authentication) {
        boolean dryRun = request != null && Boolean.TRUE.equals(request.dryRun());
        boolean replace = request != null && Boolean.TRUE.equals(request.replaceDraft());
        var result = compile.compile(accessContextFactory.user(authentication), Provenance.PARCOURS, key, dryRun, replace);
        return new CompileResponse(result.compiled().xml(), result.compiled().sha256(), result.compiled().warnings(),
                result.proposal() != null, result.proposal() != null && result.proposal().replacedExistingDraft(), null);
    }

    /** Aperçu d'une définition non enregistrée : le BPMN et les avertissements, aucune écriture. */
    @PostMapping("/preview")
    CompileResponse preview(@RequestBody PreviewRequest request, Authentication authentication) {
        var preview = compile.preview(accessContextFactory.user(authentication), request.key(), request.label(), request.definition());
        CompiledParcours compiled = preview.compiled();
        return new CompileResponse(compiled.xml(), compiled.sha256(), compiled.warnings(), false, false, preview.problem());
    }

    record SaveRequest(String label, ParcoursDefinition definition, Integer expectedVersion) {}

    record CompileRequest(Boolean dryRun, Boolean replaceDraft) {}

    record PreviewRequest(String key, String label, ParcoursDefinition definition) {}

    /** {@code problem} : pour un aperçu, pourquoi le déploiement refuserait ce BPMN ({@code null} s'il l'accepterait). */
    record CompileResponse(String xml, String sha256, List<String> warnings, boolean draftWritten, boolean replacedExistingDraft,
            String problem) {}

    record ParcoursSummary(String key, String label, int version, int steps, Instant updatedAt, String updatedBy, Instant lastCompiledAt) {
        static ParcoursSummary from(Parcours p) {
            return new ParcoursSummary(p.key(), p.label(), p.version(), p.definition().steps().size(), p.updatedAt(), p.updatedBy(),
                    p.lastCompiledAt());
        }
    }

    record ParcoursView(String key, String label, int version, ParcoursDefinition definition, Instant updatedAt, String updatedBy,
            Instant lastCompiledAt, String lastCompiledSha256) {
        static ParcoursView from(Parcours p) {
            return new ParcoursView(p.key(), p.label(), p.version(), p.definition(), p.updatedAt(), p.updatedBy(), p.lastCompiledAt(),
                    p.lastCompiledSha256());
        }
    }
}
