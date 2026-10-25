package be.myrmacta.domain.dossier.model;

public enum DossierStatus {
    DRAFT,
    OUVERT,
    EN_COURS,
    SUSPENDU,
    CLOTURE,
    ARCHIVE;

    /** True once a dossier is closed/archived and human-initiated mutations must be rejected. */
    public boolean blocksMutation() {
        return this == CLOTURE || this == ARCHIVE;
    }
}
