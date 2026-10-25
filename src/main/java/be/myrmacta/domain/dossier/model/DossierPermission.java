package be.myrmacta.domain.dossier.model;

public enum DossierPermission {
    VIEW,
    EDIT;

    public boolean implies(DossierPermission required) {
        if (required == VIEW) {
            return this == VIEW || this == EDIT;
        }
        return this == EDIT;
    }
}
