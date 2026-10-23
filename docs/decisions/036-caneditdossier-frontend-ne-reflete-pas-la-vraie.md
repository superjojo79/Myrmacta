# §36 — `canEditDossier` (frontend) ne reflète pas la vraie règle d'autorisation backend

> **Statut :** clos · **Section :** [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) · ancien `TODO.md` §36 · [registre](README.md)

**Constat (revue PR #53, `edition-champs-dossier`) :** le frontend calculait `canEditDossier`
comme `isTenantAdmin || isWorkflowAdmin || roles.includes('DOSSIER_MANAGER')` — un simple rôle
`DOSSIER_MANAGER` suffisait à afficher les actions d'édition (champs dynamiques depuis PR #53,
« Ajouter » élément, « Lier » dossier). Or `DossierAccessPolicy.assertCanEdit`, le contrôle
réellement appliqué côté serveur, ne laissait passer automatiquement que `TENANT_ADMIN` et
`WORKFLOW_ADMIN` — un `DOSSIER_MANAGER` seul devait en plus détenir une entrée
`DossierTypeAclEntry(EDIT)` explicite pour ce type de dossier (décision n° 20). Sans cette ACL, le
frontend affichait donc une UI éditable qui échouait systématiquement en 403 à l'enregistrement.

**Pas une faille de sécurité** — le backend refusait proprement et aucune donnée n'était exposée ou
modifiée sans autorisation — mais un écart de fidélité UI/backend, présent avant PR #53
(« Ajouter »/« Lier ») et étendu à l'édition des champs.

**Corrigé (2026-08-20, cette session) :** première option retenue — la permission effective est
désormais exposée par le backend plutôt que redocumentée côté UI.

- `DossierAccessPolicy.canEdit(Dossier, AccessContext)` (nouveau, domaine pur) : miroir
  non-throwing de `assertCanEdit`, même logique (`hasPermission` factorisé), pour les appelants qui
  veulent le résultat plutôt qu'une exception.
- `DossierResponse` porte désormais un champ `canEdit: boolean`, calculé par `DossierController`
  via `accessPolicy.canEdit(dossier, actor)` sur les 5 réponses concernées (`create`, `getById`,
  `getByReference`, `updateStatus`, `updateFields`).
- Frontend : `DossierDetailView.vue` calcule `canEditDossier` depuis `dossier.value?.canEdit`
  (réponse serveur), plus depuis le rôle local — supprime la dépendance à `roles`/`DOSSIER_MANAGER`
  dans ce composant.
- OpenAPI golden régénéré (`openapi/openapi.yaml`) — ce passage a aussi rattrapé un golden
  périmé : la feature `observabilite` (PR #59) avait ajouté `GET /api/observability` sans
  régénérer le fichier, malgré sa propre revue Phase 9 affirmant le contraire.
- Tests : `DossierAccessPolicyTest` (nouveaux cas `canEdit`, y compris le contexte `Service` qui
  retourne `false` au lieu de lever) ; `DossierApiTest` (`canEdit` vrai/faux selon ACL et rôle,
  y compris `DOSSIER_MANAGER` sans grant) ; front Vitest + Playwright mettent à jour les fixtures
  `Dossier` avec `canEdit` et un test dédié prouve le découplage rôle/serveur.

**Impacte :** `frontend/src/views/pilotage/DossierDetailView.vue` (`canEditDossier`),
`DossierAccessPolicy.java`, `DossierController.java`, `DossierApiDtos.java`,
[12-autorisation-conformite.md](../specs/12-autorisation-conformite.md).
