# §19 — Granularité des rôles RBAC

> **Statut :** clos · **Section :** [Points ouverts › Autorisation et conformité](section-points-autorisation-et-conformite.md) · ancien `TODO.md` §19 · [registre](README.md)

**Clos V1 (2026-08-03 — feature `autorisation-conformite`) :** les cinq rôles
`TENANT_ADMIN`, `WORKFLOW_ADMIN`, `DOSSIER_MANAGER`, `DOSSIER_VIEWER`,
`API_SERVICE` sont la liste définitive V1. Mapping `realm_access.roles` →
`ROLE_*` déjà livré (`authentification`). Restriction par type = ACL
(décision n° 20). Peut être rouvert si les premiers clients réels exigent
d’autres rôles.

**Impacte :** [12-autorisation-conformite.md](../specs/12-autorisation-conformite.md).
