package be.myrmacta.domain.dossier.model;

public class IllegalDossierStatusTransitionException extends RuntimeException {
    private final DossierStatus from;
    private final DossierStatus to;

    public IllegalDossierStatusTransitionException(DossierStatus from, DossierStatus to) {
        super("Illegal status transition from " + from + " to " + to);
        this.from = from;
        this.to = to;
    }

    public DossierStatus from() {
        return from;
    }

    public DossierStatus to() {
        return to;
    }
}
