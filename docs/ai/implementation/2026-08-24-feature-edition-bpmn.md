---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: done
created: 2026-08-24
updated: 2026-08-24
feature: edition-bpmn
design_ref: docs/ai/design/2026-08-24-feature-edition-bpmn.md
planning_ref: docs/ai/planning/2026-08-24-feature-edition-bpmn.md
---

# Implementation Guide — `edition-bpmn`

~20 tâches planifiées (T1.1–T8.4) exécutées en TDD sur 8 milestones. M1-M4
(backend) et M5-M7 (frontend, hors E2E) implémentés **en parallèle** par
deux agents dans des worktrees git isolés (contrat API déjà figé par le
design en Phase 3, aucune dépendance d'exécution croisée). Fusionnés
séquentiellement par l'orchestrateur : backend via `git merge --no-ff`
(branche propre, basée sur le tronc réel), frontend via `git cherry-pick`
ciblé (sa branche partait d'une base `origin/main` ayant divergé — une
autre feature non liée y avait été mergée entre-temps ; seuls les 3
commits M5/M6/M7, strictement scopés à `frontend/**`, ont été repris,
sans importer l'historique non lié). M8 (vérification globale + E2E)
exécuté par l'orchestrateur sur l'arbre fusionné — 1 bug réel trouvé et
corrigé à cette étape (voir Design Deviations). Phase 7 (revue fichier
par fichier, dédiée) a trouvé et corrigé 1 bug réel supplémentaire (voir
Design Deviation 6).

`./mvnw verify` final : **1110 tests, 0 échec** (1106 + 4 en Phase 7),
LINE 93.84% / BRANCH 75.27% (≥ seuils 85%/75%). Frontend **193 tests
Vitest, 0 échec**, typecheck/lint propres. **47/47 Playwright** (43
existants non régressés
+ 4 nouveaux `E2E-EDIT-BPMN-*`). Statut : implémentation **terminée**.
Phase 7 (check implementation) intégrée ci-dessous. Prochaine étape :
`dev-review` (Phase 9), non lancée à ce stade.

## Development Setup

Aucun changement d'environnement backend (JDK 25, dépendances Flowable
déjà présentes). Frontend : une nouvelle dépendance npm,
`bpmn-js-properties-panel@^5.65.0` (voir Design Deviations pour le choix
de version).

## Code Structure

**Backend :**
- `domain/workflow/model/BpmnDraft.java` — nouveau.
- `domain/workflow/port/{in,out}/` — `SaveBpmnDraftUseCase`,
  `GetBpmnDraftUseCase` (in, nouveaux) ; `BpmnDraftRepository` (out,
  nouveau) ; `WorkflowDeploymentPort` (out, modifié — `+findDeployedXml`).
- `domain/workflow/usecase/` — `SaveBpmnDraftUseCaseImpl`,
  `GetBpmnDraftUseCaseImpl` (nouveaux) ; `DeployBpmnProcessUseCaseImpl`
  (modifié — supprime le brouillon après déploiement réussi).
- `infrastructure/persistence/workflow/` — `BpmnDraftJpaEntity`
  (`@IdClass`), `SpringDataBpmnDraftRepository`,
  `JpaBpmnDraftRepositoryAdapter` (nouveaux).
- `infrastructure/flowable/FlowableWorkflowDeploymentAdapter.java`
  (modifié — implémente `findDeployedXml` via
  `RepositoryService.getProcessModel(...)`).
- `infrastructure/web/workflow/` — `BpmnDraftController` (nouveau) ;
  `WorkflowBpmnDeploymentController` (modifié — `GET .../{key}/xml`).
- `infrastructure/config/WorkflowConfig.java` (modifié — beans des
  nouveaux use cases).
- `db/changelog/changes/013-bpmn-draft-tables.yaml` (nouveau) +
  `db.changelog-master.yaml` (modifié).

**Frontend :**
- `components/workflow/BpmnEditorCanvas.vue` (nouveau — `bpmn-js/lib/Modeler`
  + `bpmn-js-properties-panel` + provider custom).
- `components/workflow/bpmn-properties/flowablePropertiesProvider.ts`
  (nouveau).
- `components/workflow/BpmnUploadPreview.vue` (modifié — refactor vers
  `useBpmnDeployment.ts`, contrat public inchangé).
- `composables/useBpmnDraft.ts`, `composables/useBpmnDeployment.ts`
  (nouveaux).
- `views/admin/BpmnEditorView.vue` (nouveau).
- `views/admin/WorkflowConfigsAdminView.vue` (modifié — lien « Éditer »).
- `app/routes.ts` (modifié — route `/admin/bpmn-editor`).
- `api/workflows.ts`, `api/types.ts` (modifiés — `getBpmnDraft`,
  `saveBpmnDraft`, `getDeployedBpmnXml`).
- `types/bpmn-js-properties-panel-shims.d.ts` (nouveau — les packages
  `bpmn-js-properties-panel`/`@bpmn-io/properties-panel` ne fournissent
  aucun type).

## Implementation Notes

### Core Features

- **Brouillon persisté, clé composite** (D-EDIT-SAVE/D-DESIGN-DRAFT-KEY)
  — `BpmnDraftJpaEntity` avec `@IdClass` sur `(tenant_id,
  process_definition_key)`, même pattern que
  `WorkflowIdempotencyJpaEntity`/`WebhookIdempotencyJpaEntity` (déjà
  utilisé 2x dans le dépôt). `save()` avec la même clé écrase
  directement (merge Hibernate), pas de logique d'upsert manuelle.
- **Validation du brouillon plus permissive que le déploiement final**
  (D-EDIT-DRAFT-VALIDATION) — `SaveBpmnDraftUseCaseImpl` valide le XML
  via `BpmnParserPort` (réutilisé tel quel) et la cohérence clé
  chemin/XML, mais **ne vérifie pas** `WorkflowTypeConfigRepository` :
  un brouillon peut exister avant que la clé soit configurée.
- **Suppression du brouillon après déploiement réussi**
  (D-DESIGN-DRAFT-LIFECYCLE) — ajout additif dans
  `DeployBpmnProcessUseCaseImpl` ; les 3 scénarios de test existants
  (`deploiement-bpmn`) restent inchangés et verts.
- **Lecture du XML déployé** (`findDeployedXml`) — nouvelle capacité
  nécessaire pour le 3ᵉ point d'entrée de l'éditeur (édition d'un
  diagramme déjà déployé) : aucun mécanisme existant ne renvoyait le XML
  brut d'une définition de process par clé (`GET .../info`, §35, ne
  renvoie que des métadonnées).
- **Éditeur `bpmn-js` Modeler + panel de propriétés Flowable custom**
  (`BpmnEditorCanvas.vue`) — import dynamique comme `BpmnCanvas.vue`
  pour `NavigatedViewer`, mais avec `Modeler` (édition) + panel standard
  + provider custom limité aux attributs réellement utilisés dans le
  dépôt (`async`, `delegateExpression`, `expression` sur `serviceTask`,
  `scope` sur `signal` — D-EDIT-SCOPE).
- **Déploiement intégré, logique partagée** (D-EDIT-DEPLOY-FLOW) —
  `useBpmnDeployment.ts` extrait de la logique déjà écrite dans
  `BpmnUploadPreview.vue`, consommé à la fois par ce composant (refactor,
  comportement inchangé) et par `BpmnEditorView.vue` (nouveau) — zéro
  duplication.
- **Protection perte de travail** (D-EDIT-UNSAVED) — `useBpmnDraft.ts`
  expose un état « non enregistré » (mis à jour via l'écoute
  `commandStack.changed` de l'éditeur), conditionnant un écouteur
  `beforeunload` ; pas d'autosave.

### Patterns & Best Practices

- **Précédents de code réutilisés systématiquement** : style d'injection
  `tenantId` via constructeur (`DeployBpmnProcessUseCaseImpl`) ;
  `@IdClass` composite (`WorkflowIdempotencyJpaEntity`) ; `Authentication`/
  `AccessContextFactory` pour `updatedBy` (`TenantRetentionSettingsController`) ;
  style d'adaptateur Flowable (`FlowableWorkflowDeploymentAdapter`
  existant, étendu plutôt que dupliqué).
- **Composable plutôt que composant partagé** pour la logique de
  déploiement (D-EDIT-DEPLOY-EXTRACT, design) — `BpmnUploadPreview.vue`
  (aperçu compact) et `BpmnEditorView.vue` (panneau pleine page) ont des
  besoins de rendu différents ; un composable partage l'état/la logique
  sans imposer un même gabarit visuel (cohérent avec `vue-best-practices`).
- **Validation en profondeur, jamais côté client seul** — cohérent avec
  `deploiement-bpmn` : le brouillon est revalidé côté serveur (bien-formé
  BPMN) même si l'UI ne permet normalement pas de sauvegarder un XML
  invalide.

## Design Deviations

1. **Écart mineur documenté (backend, T4.1)** : `deployedXml` sur
   `WorkflowBpmnDeploymentController` appelle `WorkflowDeploymentPort`
   directement, sans use case intermédiaire dédié — fidèle au diagramme
   mermaid du design (`Controller → Port → Adapter`) et à un précédent
   déjà présent dans le dépôt (`WorkflowTaskController` →
   `WorkflowEnginePort`). Pas de classe use case superflue créée ;
   aucune fuite d'architecture (le domaine reste consulté via un port).
2. **Dépendance npm : version par défaut dépréciée évitée (frontend,
   T5.0)** — `npm view bpmn-js-properties-panel` résout par défaut la
   dernière version majeure (`6.0.1`), explicitement dépréciée par son
   mainteneur (`npm warn deprecated ... use 5.x instead`). Fixé à
   `^5.65.0` (peer range `bpmn-js >= 11.5`, compatible avec `^18.24.0`
   déjà présent) avant d'écrire tout code contre son API.
3. **Bug réel trouvé et corrigé en Phase 8/M8 (pas en Phase 5)** :
   `BpmnEditorCanvas.vue` écoutait
   `modeler.get('commandStack').on('commandStack.changed', ...)` —
   `CommandStack` de diagram-js **n'a pas** de méthode `.on()` propre,
   les événements passent par l'`eventBus`. L'erreur
   (`"p.get(...).on is not a function"`) n'apparaissait qu'au premier
   rendu réel dans un navigateur (Playwright) : les tests Vitest
   mockaient entièrement `bpmn-js/lib/Modeler`, y compris un
   `commandStack` doté d'un `.on()` fictif, masquant le bug par
   construction. Corrigé en `modeler.get('eventBus').on(...)`, mocks
   Vitest alignés sur la vraie API (`BpmnEditorCanvas.spec.ts`,
   `BpmnEditorView.spec.ts`). C'est exactement la classe de risque que
   l'E2E est censée couvrir, et qui l'a effectivement fait ici — le plan
   (Phase 4) l'avait explicitement anticipé comme risque non couvert par
   les tests unitaires mockés.
4. **Bug réel trouvé et corrigé en TDD (frontend, T7.1)** :
   `BpmnEditorCanvas` était initialement monté avant que
   `useBpmnDraft.load()` (appelé dans le `onMounted` du parent) n'ait
   résolu — Vue monte les enfants avant que le `onMounted` du parent ne
   s'exécute, créant une course entre l'import du template vide et le
   vrai import (brouillon/déployé). Corrigé par un flag `ready` qui ne
   bascule qu'une fois le chargement initial réglé.
5. **Limitations d'environnement de test découvertes et contournées
   (M8, écriture de l'E2E, pas des bugs de production)** :
   - `page.goto('/admin/bpmn-editor...')` en navigation directe échoue
     silencieusement (le build en chemins relatifs, décision n°27,
     résout `./assets/...` contre l'URL profonde et 404 le bundle JS —
     limitation préexistante du dépôt, `/admin/observabilite` a le même
     problème latent mais son seul test E2E qui y navigue en direct ne
     vérifie qu'une absence, jamais un rendu positif). Contourné en
     naviguant par clic (`Workflows` → `Éditer`), comme le reste de la
     suite E2E le fait déjà partout ailleurs.
   - Les fixtures BPMN existantes de `deploiement-bpmn`
     (`BPMN_FOR_OTHER_WORKFLOW`) n'ont pas de `bpmndi:BPMNDiagram` (DI) —
     tolérable pour le `NavigatedViewer` en lecture seule de
     `BpmnUploadPreview.vue` (qui ne vérifie jamais que le rendu
     réussit), mais rejetées par le `Modeler` réel de l'éditeur
     (`"no diagram to display"`). Fixtures dédiées avec DI complète
     ajoutées pour les tests de l'éditeur, sans toucher aux fixtures
     partagées existantes.
6. **Bug réel trouvé et corrigé en Phase 7 (revue fichier par fichier
   dédiée, pas M8)** : `SaveBpmnDraftUseCaseImpl.save()` appelait
   `xml.getBytes(...)` sans garde contre `xml` `null` — un `PUT
   /api/workflow-bpmn-drafts/{key}` avec un corps `{}` (champ `xml`
   manquant, désérialisation Jackson d'un `record` sans validation)
   levait une `NullPointerException` non gérée (aucun
   `@ExceptionHandler` ne la catch dans `WorkflowExceptionHandler`),
   renvoyant `500` au lieu d'un `400` contrôlé. **Même classe de bug que
   celle déjà trouvée et corrigée en Phase 7 de `deploiement-bpmn`**
   (NPE sur `originalFilename` `null`) — le précédent n'a pas empêché la
   récurrence côté brouillon, code écrit indépendamment. Corrigé par une
   garde explicite (`xml == null || xml.isBlank()` → `InvalidBpmnException`,
   déjà mappée en `400`) en tête de `save()`, **avant** tout appel au
   parser BPMN. Couvert par 2 nouveaux scénarios unitaires
   (`SaveBpmnDraftUseCaseImplTest`) et 1 nouveau scénario API
   (`BpmnDraftApiTest.put_missingXmlField_returns400`) ; un second
   scénario API ajouté au passage (`put_withoutAdminRole_returns403`,
   couverture explicite d'un cas déjà protégé par `@PreAuthorize`
   classe mais jamais testé explicitement pour `PUT`).

**Point ouvert non corrigé, signalé plutôt que tranché silencieusement**
(sévérité plus faible, UX pas sécurité/intégrité) : `useBpmnDraft.load()`
n'a pas de `try/catch` — si `getBpmnDraft`/`getDeployedBpmnXml` échoue
pour une raison réseau (pas un `204`, une vraie erreur HTTP/réseau),
l'exception remonte et interrompt le `onMounted` de `BpmnEditorView.vue`
avant que `ready.value = true` ne soit atteint : l'éditeur reste bloqué
sur une page vide, sans message d'erreur ni option de réessayer. Pas
corrigé dans cette session — périmètre au-delà des success criteria
actés (aucun scénario requirements/testing ne couvre ce cas), à trancher
explicitement si l'utilisateur veut l'ajouter (nouveau ticket ou
extension mineure de `useBpmnDraft.ts`/`BpmnEditorView.vue`).

## Coverage

Aucun changement aux seuils Jacoco existants (LINE 85%, BRANCH 75%). État
final (`./mvnw verify`, 1110 tests, après Phase 7) : LINE 93.84%, BRANCH
75.27% — tous seuils tenus, aucun test ciblé supplémentaire nécessaire
pour les atteindre (BRANCH reste proche du seuil, à surveiller si de
nouvelles branches non testées sont ajoutées plus tard sur ce module).

## Files Changed

**Production (backend) :**
- `src/main/java/com/flowforge/domain/workflow/model/BpmnDraft.java`
- `src/main/java/com/flowforge/domain/workflow/port/in/{SaveBpmnDraftUseCase,GetBpmnDraftUseCase}.java`
- `src/main/java/com/flowforge/domain/workflow/port/out/BpmnDraftRepository.java`
- `src/main/java/com/flowforge/domain/workflow/port/out/WorkflowDeploymentPort.java` (modifié)
- `src/main/java/com/flowforge/domain/workflow/usecase/{SaveBpmnDraftUseCaseImpl,GetBpmnDraftUseCaseImpl}.java`
- `src/main/java/com/flowforge/domain/workflow/usecase/DeployBpmnProcessUseCaseImpl.java` (modifié)
- `src/main/java/com/flowforge/infrastructure/persistence/workflow/{BpmnDraftJpaEntity,SpringDataBpmnDraftRepository,JpaBpmnDraftRepositoryAdapter}.java`
- `src/main/java/com/flowforge/infrastructure/flowable/FlowableWorkflowDeploymentAdapter.java` (modifié)
- `src/main/java/com/flowforge/infrastructure/web/workflow/BpmnDraftController.java`
- `src/main/java/com/flowforge/infrastructure/web/workflow/WorkflowBpmnDeploymentController.java` (modifié)
- `src/main/java/com/flowforge/infrastructure/config/WorkflowConfig.java` (modifié)
- `src/main/resources/db/changelog/changes/013-bpmn-draft-tables.yaml`
- `src/main/resources/db/changelog/db.changelog-master.yaml` (modifié)
- `openapi/openapi.yaml` (golden régénéré)

**Production (frontend) :**
- `frontend/src/components/workflow/BpmnEditorCanvas.vue`
- `frontend/src/components/workflow/bpmn-properties/flowablePropertiesProvider.ts`
- `frontend/src/components/workflow/BpmnUploadPreview.vue` (modifié)
- `frontend/src/composables/{useBpmnDraft,useBpmnDeployment}.ts`
- `frontend/src/views/admin/BpmnEditorView.vue`
- `frontend/src/views/admin/WorkflowConfigsAdminView.vue` (modifié)
- `frontend/src/app/routes.ts` (modifié)
- `frontend/src/api/{workflows,types}.ts` (modifiés)
- `frontend/src/types/bpmn-js-properties-panel-shims.d.ts`
- `frontend/package.json`, `frontend/package-lock.json` (modifiés)

**Tests (nouveaux, sauf mention) :**
- `src/test/java/com/flowforge/domain/workflow/usecase/{SaveBpmnDraftUseCaseImplTest,GetBpmnDraftUseCaseImplTest}.java`
- `src/test/java/com/flowforge/domain/workflow/usecase/DeployBpmnProcessUseCaseImplTest.java` (modifié)
- `src/test/java/com/flowforge/infrastructure/flowable/FlowableWorkflowDeploymentAdapterTest.java` (modifié)
- `src/test/java/com/flowforge/infrastructure/persistence/workflow/{BpmnDraftJpaEntityTest,JpaBpmnDraftRepositoryAdapterTest}.java`
- `src/test/java/com/flowforge/infrastructure/web/workflow/BpmnDraftApiTest.java`
- `src/test/java/com/flowforge/infrastructure/web/workflow/WorkflowBpmnDeploymentApiTest.java` (modifié)
- `frontend/src/components/workflow/{BpmnEditorCanvas.spec.ts,bpmn-properties/flowablePropertiesProvider.spec.ts}`
- `frontend/src/composables/{useBpmnDraft.spec.ts,useBpmnDeployment.spec.ts}`
- `frontend/src/views/admin/BpmnEditorView.spec.ts`
- `frontend/src/app/routes.spec.ts` (modifié), `frontend/src/views/admin/WorkflowConfigsAdminView.spec.ts` (modifié)
- `frontend/src/api/workflowBpmnDeployment.spec.ts` (modifié)
- `frontend/e2e/pilotage.spec.ts` (modifié — 4 nouveaux scénarios `E2E-EDIT-BPMN-*`)

**Documentation :**
- `docs/ai/{requirements,design,planning,testing,implementation}/2026-08-24-feature-edition-bpmn.md`
- `analysis doc/14-edition-bpmn.md`, `analysis doc/00-index.md`
- `TODO.md` (§39), `FILE-ATTENTE.md`

## Integration Points

Aucune API externe modifiée. Nouveaux endpoints
`/api/workflow-bpmn-drafts/{key}` (`GET`/`PUT`) et
`/api/workflow-bpmn-deployments/{key}/xml` (`GET`), isolés. `WorkflowDeploymentPort`
étendu (méthode ajoutée, pas de signature existante changée) —
`FlowableWorkflowDeploymentAdapter` reste le seul implémenteur. `BpmnUploadPreview.vue` :
contrat public préservé, 5 tests Vitest existants inchangés et verts.

## Error Handling

`SaveBpmnDraftUseCaseImpl` : XML malformé →`InvalidBpmnException` →
`400` (même mapping que `deploiement-bpmn`) ; clé chemin/XML incohérente
→ `400` ; `xml` `null`/blanc (champ manquant du corps JSON) → `400`
plutôt qu'une `NullPointerException` non gérée (Design Deviation 6,
trouvée en Phase 7). `BpmnDraftController`/`WorkflowBpmnDeploymentController` :
`204` pour une absence de brouillon/déploiement (pas une erreur — état
normal). Toute validation reproduite côté serveur, jamais confiance dans
l'éditeur frontend.

## Performance Considerations

Aucun changement de performance en production. Brouillon = `text`
unique par clé (pas de volumétrie particulière), pas de nouveau chemin
chaud.

## Security Notes

- **Autorisation** : mêmes rôles que `deploiement-bpmn`
  (`TENANT_ADMIN`/`WORKFLOW_ADMIN`) sur tous les nouveaux endpoints.
- **Validation d'entrée** : bien-formé BPMN 2.0 revalidé côté serveur à
  la sauvegarde du brouillon (pas seulement à l'écran) ; le déploiement
  final garde sa validation stricte inchangée (clé connue,
  `WorkflowTypeConfigRepository`).
- **Pas de nouvelle surface d'attaque backend** : aucune nouvelle
  dépendance, réutilise `BpmnParserPort`/`RepositoryService` déjà
  présents. Côté frontend, seule nouvelle dépendance
  (`bpmn-js-properties-panel`) — licence MIT vérifiée avant usage (Design
  Deviation 2).
- **Robustesse** : voir Design Deviation 6 (NPE potentielle sur `xml`
  `null`/manquant trouvée et corrigée — corps de requête `{}` renvoyait
  `500` au lieu de `400`).

## Phase 7 — check implementation

**Correction** : la version précédente de cette section datait le
« Phase 7 » du travail fait pendant M8 (fusion + vérification globale).
En réalité, les 2 bugs trouvés à cette étape (`eventBus`/`commandStack`,
race condition de montage) l'ont été respectivement en écrivant l'E2E et
en TDD par l'agent frontend — pas via une revue fichier par fichier
dédiée. Cette section documente maintenant la **vraie** Phase 7,
exécutée séparément sur demande explicite de l'utilisateur, avec relecture
systématique de chacun des 45 fichiers du diff (`git diff a56ceb7~1
7aa31d5 --stat`), au-delà de la revue faite par chaque agent dans son
propre worktree.

- **Alignement design** : les 7 décisions Phase 2 et les 3 décisions
  Phase 3 sont toutes couvertes (tableaux de traçabilité requirements→design
  et design→implémentation). Aucun gap trouvé.
- **Cohérence avec modules similaires** : `@IdClass` suit
  `WorkflowIdempotencyJpaEntity` (vérifié explicitement, pas supposé) ;
  injection `tenantId` suit `DeployBpmnProcessUseCaseImpl` ; composable
  plutôt que composant suit les conventions `vue-best-practices` ;
  `WorkflowBpmnDeploymentController` dépendant directement d'un port
  (Design Deviation 1) vérifié contre le précédent réel
  `WorkflowTaskController`/`WorkflowEnginePort` (lu, pas supposé).
- **Duplication** : aucune — `useBpmnDeployment.ts` élimine la
  duplication potentielle entre `BpmnUploadPreview.vue` et
  `BpmnEditorView.vue` (c'était l'objectif explicite de D-EDIT-DEPLOY-FLOW).
  Le refactor de `BpmnUploadPreview.vue` relu ligne à ligne : comportement
  observable strictement identique, confirmé par les 5 tests existants
  inchangés.
- **Rupture de contrat** : aucune — `WorkflowDeploymentPort` étendu de
  façon additive ; `BpmnUploadPreview.vue` garde son contrat public ;
  `openapi.yaml` inchangé par le correctif de cette phase (pas de
  nouveau champ/endpoint, juste un code de retour resserré sur un cas déjà
  documenté comme `400` pour XML invalide).
- **Persistence** : `JpaBpmnDraftRepositoryAdapter.save()` relu — `save()`
  sur une entité à clé assignée (non générée) déclenche un `merge`
  Hibernate côté Spring Data (pas un `persist`), donc l'upsert fonctionne
  correctement même si les champs `@Id` ne sont jamais `null` ; confirmé
  par `JpaBpmnDraftRepositoryAdapterTest` (round-trip + écrasement réels).
- **1 bug réel trouvé et corrigé à cette étape** (Design Deviation 6) :
  `SaveBpmnDraftUseCaseImpl.save()` levait une `NullPointerException` non
  gérée (→ `500`) sur un `xml` `null`/manquant, aucun `@ExceptionHandler`
  ne la catch — même classe de défaut que le bug déjà trouvé en Phase 7
  de `deploiement-bpmn` (NPE sur `originalFilename`), réapparu ici dans
  du code écrit indépendamment. Corrigé en TDD (2 tests unitaires + 2
  tests API, dont un profitant du passage pour couvrir `PUT` sans rôle
  admin, jamais testé explicitement malgré une garde `@PreAuthorize`
  classe déjà correcte).
- **1 point ouvert signalé, non corrigé** (sévérité UX, pas
  sécurité/intégrité) : absence de gestion d'erreur réseau dans
  `useBpmnDraft.load()` — voir Design Deviations pour le détail. Hors
  scope des success criteria actés, laissé à la décision de l'utilisateur.

**Checklist :** design ✅ · pas de gap logique ✅ (1 bug trouvé et
corrigé cette étape, 2 autres trouvés et corrigés en M8/TDD avant) ·
sécurité ✅ (le bug corrigé était justement un point de robustesse/sécurité —
un `500` non contrôlé) · points d'intégration vérifiés ✅ · tests
couvrent les changements ✅ (1110 backend + 193 frontend + 47 Playwright,
tous verts) · docs à jour ✅ (requirements, design, planning, testing,
implementation, TODO §39, FILE-ATTENTE.md, `analysis doc/14`).

**Aucun blocage.**

## Phase 8 — write tests (2026-08-24)

Coverage vérifiée par classe/fichier (`jacoco.csv` backend,
`test:coverage` frontend), pas seulement à l'agrégat `BUNDLE`. Backend :
tous les fichiers `edition-bpmn` à 100% sauf 2 blocs `catch (IOException)`
défensifs (1 préexistant à `deploiement-bpmn`, 1 nouveau — traduction
d'exception non-branchante, jugés acceptables, non testés).

**Écart de couverture réel trouvé et comblé** (pas un bug — code déjà
correct, juste jamais exercé) : `flowablePropertiesProvider.ts` était à
33% branch coverage — les 5 tests initiaux ne couvraient que
`getGroups()` (quels groupes pour quel type d'élément), jamais les
fonctions `getValue`/`setValue` de chaque entrée, c'est-à-dire le pont
lecture/écriture réel entre le panel de propriétés et les attributs
`flowable:*` — la logique la plus spécifique de toute la feature
(D-EDIT-SCOPE). 3 tests ajoutés en invoquant directement `getValue`/
`setValue` (mocks `CheckboxEntry`/`TextFieldEntry` réécrits pour capturer
leurs props plutôt que de les avaler) : tous verts du premier coup,
confirmant que l'implémentation suit correctement l'API `modeling.updateModdleProperties`
documentée par bpmn.io → **100% sur les 4 métriques** pour ce fichier.

Validation finale : `./mvnw verify` 1110 tests (inchangé, pas de code
backend touché) ; frontend `npm run test` **196/196** (193 + 3),
`test:coverage` statements 86.84%/branches 77.59%/functions
86.05%/lines 88.71% (≥ seuils 80/75/70/60), typecheck/lint propres,
`npx playwright test` **47/47** (0 régression).

Prochaine étape, sur demande explicite de l'utilisateur : `dev-review`
(Phase 9), puis PR.
