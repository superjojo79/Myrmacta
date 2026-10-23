# §34 — ACL type de dossier sur le panneau dossier de la visualisation workflow

> **Statut :** clos · **Section :** [Décisions actées › Opérationnel](section-decisions-operationnel.md) · ancien `TODO.md` §34 · [registre](README.md)

**Problème :** `GET /api/workflow-links/{linkId}/dossier` (feature `visualisation-workflow`,
décision DD-DOSSIER-LOAD) chargeait le dossier après authz « participant workflow ou
TENANT_ADMIN/WORKFLOW_ADMIN » **sans** repasser par `DossierAccessPolicy` (décision n° 20) —
motivé à l'origine par l'évitement d'une double vérification d'ACL (A10). Un simple participant ou
observer d'un workflow, sans entrée `dossier_type_acl` de niveau VIEW, pouvait donc lire
l'intégralité des champs métier du dossier — un modèle d'accès incohérent entre les deux portes
d'entrée pour lire le même dossier (`GetDossierUseCase` vs panneau viz), relevé par l'audit
sécurité de cette session.

**Décision retenue :** revenir sur DD-DOSSIER-LOAD/A10 — `WorkflowLinkVisualizationUseCaseImpl.getDossier`
appelle désormais `DossierAccessPolicy.assertCanView(dossier, actor)` en plus du contrôle
participant. `TENANT_ADMIN`/`WORKFLOW_ADMIN` continuent de contourner (comme partout ailleurs) ;
un participant/observer sans ACL VIEW garde l'accès au diagramme/historique/tâches (aucune donnée
métier du dossier) mais reçoit 403 sur le panneau dossier.

**Documenté dans :** [12-autorisation-conformite.md](../specs/12-autorisation-conformite.md),
[10-visualisation-workflow.md](../specs/10-visualisation-workflow.md).
