package be.myrmacta.domain.workflow.portal.model;

/** Niveau de garantie eIDAS d'une identité vérifiée (TODO §104, T4) : seuls substantiel et élevé sont acceptés. */
public enum IdentityAssuranceLevel {
    LOW,
    SUBSTANTIAL,
    HIGH;

    public boolean isAccepted() {
        return this != LOW;
    }
}
