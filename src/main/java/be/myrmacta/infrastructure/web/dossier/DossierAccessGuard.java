package be.myrmacta.infrastructure.web.dossier;

import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Charge un dossier et vérifie l'ACL en un appel — factorise le couple
 * {@code findById().orElseThrow()} + {@code assertCanView/Edit} répété dans les contrôleurs
 * dossier et workflow.
 */
@Component
public class DossierAccessGuard {

    private final DossierRepository dossierRepository;
    private final DossierAccessPolicy accessPolicy;

    public DossierAccessGuard(DossierRepository dossierRepository, DossierAccessPolicy accessPolicy) {
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    }

    public Dossier requireViewable(UUID dossierId, AccessContext ctx) {
        Dossier dossier = load(dossierId);
        accessPolicy.assertCanView(dossier, ctx);
        return dossier;
    }

    public Dossier requireEditable(UUID dossierId, AccessContext ctx) {
        Dossier dossier = load(dossierId);
        accessPolicy.assertCanEdit(dossier, ctx);
        return dossier;
    }

    private Dossier load(UUID dossierId) {
        return dossierRepository.findById(dossierId)
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + dossierId));
    }
}
