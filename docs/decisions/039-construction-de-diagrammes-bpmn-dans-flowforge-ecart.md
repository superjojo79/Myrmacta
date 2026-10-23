# §39 — Construction de diagrammes BPMN dans FlowForge — écart restant après §35

> **Statut :** clos · **Section :** [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) · ancien `TODO.md` §39 · [registre](README.md)

**Constat :** §35 (`deploiement-bpmn`, PR #67) a livré l'upload + prévisualisation d'un
`.bpmn20.xml` **déjà modélisé en externe**, avec comme non-goal explicite « pas d'édition/design
de diagramme dans FlowForge ». Le besoin métier sous-jacent (pouvoir construire/modifier un
diagramme BPMN *depuis* FlowForge) reste donc un écart réel, pas un choix assumé — distinct du
volet Cockpit ([`DEFERRED-FEATURES.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/DEFERRED-FEATURES.md) §1.5, hors scope produit, non remis en cause ici).

**Options analysées (2026-08-24, discussion utilisateur) :**

- **(a) `bpmn-js` Modeler seul.** Bascule de `NavigatedViewer` vers le bundle `Modeler` du même
  package déjà en dépendance (`bpmn-js` `^18.24.0`, `frontend/package.json`) — palette
  drag-and-drop, `saveXML()`. Zéro nouvelle dépendance, mais pas d'édition des attributs
  `flowable:*` (assignee, `formKey`, listeners…) utilisés par les process réels
  (`smoke-process.bpmn20.xml` etc.) via la palette générique.
- **(b) `bpmn-js` Modeler + `bpmn-js-properties-panel` avec provider custom Flowable — retenue.**
  Même socle que (a), plus le panel de propriétés officiel de l'écosystème bpmn.io (MIT, même
  éditeur que Camunda Modeler mais embarqué en lib, pas d'app tierce) étendu par un provider
  custom pour exposer les extensions `flowable:*` réellement utilisées dans les diagrammes du
  dépôt.
- **(c) `diagram-js` bas niveau, éditeur maison.** Écarté — réinvente ce que `bpmn-js` fait déjà.
- **(d) Flowable Modeler (webapp séparée).** Dernier recours explicitement écarté par
  l'utilisateur — app tierce à opérer/sécuriser par tenant, contradictoire avec la décision déjà
  actée d'exclure les apps Flowable UI (§35, [`DEFERRED-FEATURES.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/DEFERRED-FEATURES.md) §1.5).

**Recommandation actée :** option **(b)**. Analyse détaillée :
[docs/specs/14-edition-bpmn.md](../specs/14-edition-bpmn.md) et
[docs/ai/requirements/2026-08-24-feature-edition-bpmn.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-24-feature-edition-bpmn.md).

**Cycle `dev-lifecycle` lancé (2026-08-24), Phase 2 (requirements) close** — 7 décisions actées via
`AskUserQuestion` (une à la fois) : D-EDIT-SCOPE=provider custom limité aux attributs `flowable:*`
réellement utilisés (`async`/`delegateExpression`/`expression`/`scope`) ; D-EDIT-PLACEMENT=nouvel
écran dédié (pas une section de `/admin/workflow-types`) ; D-EDIT-ENTRYPOINT=3 points d'entrée
(création vierge, import fichier, édition d'un diagramme déjà déployé) ; D-EDIT-SAVE=brouillon
**persisté côté backend** (écart par rapport à la recommandation initiale d'export fichier local —
élargit le scope à un nouveau composant backend) ; D-EDIT-DRAFT-MODEL=un brouillon par clé de
process, écrasé à chaque sauvegarde, pas d'historique ; D-EDIT-DEPLOY-FLOW=bouton déployer
intégré, logique de confirmation/déploiement de `BpmnUploadPreview.vue` extraite et partagée (pas
dupliquée) ; D-EDIT-UNSAVED=sauvegarde explicite + confirmation navigateur `beforeunload`, pas
d'autosave.

**Phase 3 (design) close** — 3 décisions techniques actées via `AskUserQuestion` : D-DESIGN-DRAFT-KEY=clé
composite `(tenant_id, process_definition_key)` via `@IdClass` sur `BpmnDraftJpaEntity` (même pattern que
`WorkflowIdempotencyJpaEntity`/`WebhookIdempotencyJpaEntity`, déjà utilisé 2x dans le dépôt) ;
D-DESIGN-ROUTE=une seule route SPA `/admin/bpmn-editor` avec `processDefinitionKey` en query param
optionnel ; D-DESIGN-DRAFT-LIFECYCLE=le brouillon est supprimé automatiquement après un déploiement
réussi. Gap réel trouvé en exploration (pas supposé) : aucun mécanisme existant ne renvoyait le XML brut
d'une définition de process par clé — nouvelle méthode `WorkflowDeploymentPort.findDeployedXml`. Détail :
[docs/ai/design/2026-08-24-feature-edition-bpmn.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-08-24-feature-edition-bpmn.md).

**Phase 4 (planning) close** — testing doc créé (comblant l'étape sautée en Phase 1 initiale) puis plan en
8 milestones / ~20 tâches : M1 domaine backend brouillon, M2 persistence (migration
`013-bpmn-draft-tables.yaml`), M3 `findDeployedXml`, M4 endpoint brouillon + suppression post-déploiement,
M5 canvas éditeur (`bpmn-js` Modeler + `bpmn-js-properties-panel`), M6 composables partagés
(`useBpmnDraft`/`useBpmnDeployment`), M7 écran dédié + intégration admin, M8 vérification globale
(4 nouveaux scénarios E2E). Couverture testing→planning vérifiée exhaustive. Détail :
[docs/ai/testing/2026-08-24-feature-edition-bpmn.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/testing/2026-08-24-feature-edition-bpmn.md),
[docs/ai/planning/2026-08-24-feature-edition-bpmn.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/planning/2026-08-24-feature-edition-bpmn.md).

**Résolu (2026-08-24, feature `edition-bpmn`) :** Phase 5 (implémentation) exécutée en TDD, M1-M4
(backend) et M5-M7 (frontend) en parallèle par deux agents dans des worktrees git isolés (contrat
API déjà figé par le design), fusionnés puis M8 (vérification globale) exécuté par l'orchestrateur.

- Backend : `BpmnDraft`/`BpmnDraftRepository`/`SaveBpmnDraftUseCase`/`GetBpmnDraftUseCase`
  (domaine), `BpmnDraftJpaEntity` (clé composite `@IdClass`, même pattern que
  `WorkflowIdempotencyJpaEntity`), `BpmnDraftController` (`GET`/`PUT /api/workflow-bpmn-drafts/{key}`),
  `WorkflowDeploymentPort.findDeployedXml` (+`GET /api/workflow-bpmn-deployments/{key}/xml`),
  `DeployBpmnProcessUseCaseImpl` modifié (supprime le brouillon après déploiement réussi).
- Frontend : `BpmnEditorCanvas.vue` (`bpmn-js` Modeler + `bpmn-js-properties-panel` +
  `flowablePropertiesProvider.ts` custom limité aux attributs réellement utilisés), composables
  `useBpmnDraft.ts`/`useBpmnDeployment.ts` (ce dernier extrait de `BpmnUploadPreview.vue`, contrat
  public inchangé), `BpmnEditorView.vue` (route `/admin/bpmn-editor`), lien « Éditer » dans
  `WorkflowConfigsAdminView.vue`.
- Le mécanisme de déploiement existant (§35) reste inchangé et réutilisé tel quel ; aucune
  application Flowable UI tierce introduite.
- **2 bugs réels trouvés et corrigés en Phase 5/8** : `modeler.get('commandStack').on(...)` —
  `CommandStack` de diagram-js n'a pas de `.on()` propre (événements via l'`eventBus`), masqué par
  des tests Vitest mockant entièrement `bpmn-js`, trouvé en écrivant l'E2E (Playwright, vrai
  navigateur) ; race condition de montage `BpmnEditorCanvas`/`useBpmnDraft.load()`, trouvée et
  corrigée en TDD par l'agent frontend.
- `./mvnw verify` : **1110 tests, 0 échec**, LINE 93.84%/BRANCH 75.27% (≥ seuils). Frontend : **193
  tests Vitest** (0 échec), typecheck/lint propres, **47/47 Playwright** (43 existants non
  régressés + 4 nouveaux `E2E-EDIT-BPMN-1..4`).
- **Phase 7 (check implementation, revue dédiée) : 1 bug réel supplémentaire trouvé et corrigé** —
  `SaveBpmnDraftUseCaseImpl.save()` levait une `NullPointerException` non gérée (→ 500) sur un `xml`
  `null`/manquant dans le corps de la requête `PUT /api/workflow-bpmn-drafts/{key}` ; même classe de
  défaut que la NPE déjà trouvée et corrigée en Phase 7 de §35 (`originalFilename` null), réapparue
  ici indépendamment. Corrigé par une garde explicite → `InvalidBpmnException` → `400`, couvert par
  4 nouveaux tests (2 unitaires, 2 API dont un `PUT` sans rôle admin jamais testé explicitement).
- **Phase 8 (write tests) : coverage vérifiée par classe/fichier, pas seulement à l'agrégat** — écart
  réel trouvé et comblé sur `flowablePropertiesProvider.ts` (33% branch coverage : les tests initiaux
  ne couvraient que `getGroups()`, jamais les fonctions `getValue`/`setValue` de chaque entrée — le
  pont lecture/écriture réel entre le panel et les attributs `flowable:*`, cœur de D-EDIT-SCOPE). 3
  tests ajoutés, tous verts du premier coup (implémentation déjà correcte, juste jamais exercée) →
  100% sur ce fichier. `./mvnw verify` final : 1110 tests (inchangé) ; frontend **196/196 Vitest**
  (193 + 3), `test:coverage` 86.84%/77.59%/86.05%/88.71% (≥ seuils 80/75/70/60), **47/47 Playwright**.
- `README.md`, `05-workflow-engine.md`, `docs/guides/GUIDE-UTILISATEUR.md` corrigés (mentions « pas de designer
  visuel/éditeur BPMN » retirées, nouveau mécanisme documenté).
- **Phase 9 (revue finale) close** : diff complet vérifié (65 fichiers, strictement scopé), contrat
  OpenAPI relu (2 nouveaux chemins + 2 schémas, 1 imprécision cosmétique mineure relevée — `format:
  byte` documenté au lieu de `text/xml` réel, non bloquant), rollback de migration confirmé,
  aucun breaking change, aucun secret/debug résiduel. **4 bugs réels trouvés et corrigés au total**
  sur la feature (Phases 5 et 7). Preuve fraîche re-vérifiée avant PR : 1110 backend + 196 Vitest +
  47 Playwright, tous verts.

Détail complet : [docs/ai/implementation/2026-08-24-feature-edition-bpmn.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-08-24-feature-edition-bpmn.md).
Phase 9 (revue finale) close, PR ouverte sur demande explicite de l'utilisateur :
[PR #71](https://github.com/superjojo79/FlowForge/pull/71).

**Impacte :** `frontend/src/components/workflow/BpmnCanvas.vue`, `BpmnUploadPreview.vue`,
`WorkflowBpmnDeploymentController` (endpoint de déploiement potentiellement réutilisable tel
quel), [05-workflow-engine.md](../specs/05-workflow-engine.md), `README.md`.
