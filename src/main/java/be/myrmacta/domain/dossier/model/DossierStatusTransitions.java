package be.myrmacta.domain.dossier.model;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DossierStatusTransitions {

    private static final Map<DossierStatus, Set<DossierStatus>> ALLOWED = Map.of(
            DossierStatus.DRAFT, Set.of(DossierStatus.OUVERT),
            DossierStatus.OUVERT, Set.of(DossierStatus.EN_COURS, DossierStatus.CLOTURE),
            DossierStatus.EN_COURS, Set.of(DossierStatus.SUSPENDU, DossierStatus.CLOTURE),
            DossierStatus.SUSPENDU, Set.of(DossierStatus.EN_COURS, DossierStatus.CLOTURE),
            DossierStatus.CLOTURE, Set.of(DossierStatus.ARCHIVE),
            DossierStatus.ARCHIVE, Set.of());

    private DossierStatusTransitions() {}

    public static boolean canTransition(DossierStatus from, DossierStatus to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void assertCanTransition(DossierStatus from, DossierStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalDossierStatusTransitionException(from, to);
        }
    }
}
