package com.flowforge.domain.workflow.portal.model;

/** Niveau de garantie eIDAS d'une identité vérifiée (TODO §103, T4) : seuls substantiel et élevé sont acceptés. */
public enum IdentityAssuranceLevel {
    LOW,
    SUBSTANTIAL,
    HIGH;

    public boolean isAccepted() {
        return this != LOW;
    }
}
