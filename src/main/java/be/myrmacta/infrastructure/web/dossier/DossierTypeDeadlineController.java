package be.myrmacta.infrastructure.web.dossier;

import be.myrmacta.domain.deadline.model.DeadlineSetting;
import be.myrmacta.domain.deadline.port.in.ManageDeadlineUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TODO §115 (D-DEADLINE-FIELD) — échéance d'un type de dossier : champ {@code DATE}, rappel J-n, groupe d'escalade. Réservé à
 * l'administrateur du tenant, comme le reste du type.
 */
@RestController
@RequestMapping("/api/dossier-types/{code}/deadline")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class DossierTypeDeadlineController {

    private final ManageDeadlineUseCase deadlines;

    public DossierTypeDeadlineController(ManageDeadlineUseCase deadlines) {
        this.deadlines = deadlines;
    }

    /** 204 si le type n'en déclare pas. */
    @GetMapping
    public ResponseEntity<DeadlineResponse> getDeadline(@PathVariable String code) {
        return deadlines.get(code).map(DeadlineResponse::from).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping
    public DeadlineResponse setDeadline(@PathVariable String code, @RequestBody DeadlineRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        int reminderDays = request.reminderDays() == null ? 0 : request.reminderDays();
        return DeadlineResponse.from(deadlines.set(code, request.fieldName(), reminderDays, request.escalationGroup()));
    }

    @DeleteMapping
    public ResponseEntity<Void> clearDeadline(@PathVariable String code) {
        deadlines.clear(code);
        return ResponseEntity.noContent().build();
    }

    record DeadlineRequest(String fieldName, Integer reminderDays, String escalationGroup) {}

    record DeadlineResponse(String dossierTypeCode, String fieldName, int reminderDays, String escalationGroup) {

        static DeadlineResponse from(DeadlineSetting setting) {
            return new DeadlineResponse(setting.dossierTypeCode(), setting.fieldName(), setting.reminderDays(), setting.escalationGroup());
        }
    }
}
