# §56 — Extension du serveur MCP au flux métier complet (connecteurs, association templates, questions de tâche)

> **Statut :** clos · **Section :** [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) · ancien `TODO.md` §56 · [registre](README.md)

**Statut (2026-09-06) : cycle `dev-lifecycle` complet, Phases 1 à 9 closes.** Origine :
demande utilisateur — inspecter ce qui a été ajouté à FlowForge depuis `serveur-mcp` (PR #82, 9
tools) et déterminer ce qui manque pour qu'un agent MCP puisse définir un flux métier complet sans
sortir du canal MCP. Analyse initiale :
[24-extension-mcp-flux-metier.md](../specs/24-extension-mcp-flux-metier.md). Trois lacunes
vérifiées contre le code réel : **(1)** aucun tool ne couvre `ConnectorInstance` (créée
aujourd'hui uniquement via l'écran admin `TENANT_ADMIN`-only, alors qu'un `serviceTask` BPMN la
référence par UUID d'instance) ; **(2)** `DossierType.documentTemplateKeys` (association template
↔ type de dossier, TODO §52) est le seul champ de `DossierType` inaccessible en écriture via
`define_dossier_type` (forcé à `null` en création, préservé tel quel en update) ; **(3)**
`TaskQuestionSchema` (TODO §55) n'a pas de tool MCP, la feature étant postérieure au cadrage MCP.

**6 décisions tranchées en Phase 1 via `AskUserQuestion`** :

- **D-MCP-EXT-SCOPE = un seul cycle** couvrant les 3 lacunes (pas 3 cycles séparés).
- **D-MCP-EXT-SECRET = même flux que l'admin REST** — secret connecteur en clair sur l'appel MCP
  (canal M2M déjà authentifié `API_SERVICE`), chiffré AES-GCM à l'écriture, jamais renvoyé en
  clair en lecture.
- **D-MCP-EXT-DOCTPL = étendre `define_dossier_type`** avec un paramètre optionnel
  `documentTemplateKeys`, plutôt qu'un tool dédié.
- **D-MCP-EXT-DRYRUN = `dryRun` partout** — reconduit sur les 2 nouveaux tools d'écriture, cohérent
  avec `define_dossier_type`/`deploy_workflow`.
- **D-MCP-EXT-NAMING** — 7 nouveaux tools confirmés : `list_connector_definitions`,
  `list_connector_instances`, `get_connector_instance`, `define_connector_instance`,
  `list_task_question_schemas`, `get_task_question_schema`, `define_task_question_schema`.
- **D-MCP-EXT-RBAC-GUARD** — trouvaille réelle faite en Phase 1 (pas anticipée par l'analyse
  initiale) : `ManageConnectorInstanceUseCaseImpl` et `TaskQuestionSchemaUseCaseImpl` n'ont
  **aucune garde de rôle au niveau domaine** (RBAC vérifié seulement au contrôleur REST
  `@PreAuthorize`) — et `WorkflowTypeConfigUseCaseImpl`, **déjà utilisé par `deploy_workflow`
  depuis PR #82**, non plus. Décision : ajouter le garde-fou domaine sur les 3 use cases, y
  compris le correctif sur du code déjà livré (pas seulement sur les 2 nouveaux tools).

**Portée technique désormais plus large que l'analyse initiale** : le garde-fou implique de
changer la signature de 4 interfaces `port.in`
(`ManageConnectorInstanceUseCase`, `SaveTaskQuestionSchemaUseCase`/`DeleteTaskQuestionSchemaUseCase`,
`SaveWorkflowTypeConfigUseCase`) pour y ajouter l'acteur, et de mettre à jour tous leurs appelants
REST existants. Point de conception encore ouvert (Phase 3) : emplacement du garde-fou partagé
(dupliquer un guard par bounded context, le déplacer dans un package neutre, ou étendre
`TenantAdminGuard` en l'acceptant comme dépendance transverse) — voir
[docs/ai/design/2026-09-06-feature-extension-mcp-flux-metier.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-09-06-feature-extension-mcp-flux-metier.md).

**Phase 2 (revue requirements) close** — 1 décision supplémentaire tranchée via `AskUserQuestion` :
**D-MCP-EXT-RBAC-SCOPE**, trouvée en revérifiant le RBAC REST exact de chaque contrôleur (pas
seulement l'existence du garde) — l'asymétrie est réelle et volontairement reproduite au niveau
domaine : `ManageConnectorInstanceUseCaseImpl` (garde sur toutes les opérations, miroir du
`@PreAuthorize` classe de `ConnectorAdminController`) et `TaskQuestionSchemaUseCaseImpl`/
`GetTaskQuestionSchemaUseCaseImpl` (garde sur toutes les opérations, miroir de
`TaskQuestionSchemaController` qui gate explicitement `list`/`get`) contre
`WorkflowTypeConfigUseCaseImpl` (garde **uniquement** sur `create`/`update` — ses `list`/
`getByKey` restent ouverts comme le REST, pour ne pas régresser `list_workflow_types` déjà en
production sans demande fonctionnelle). 2 points que le brouillon design avait laissés incertains
en Phase 1 sont résolus après vérification du code réel (pas des trous) :
`GetTaskQuestionSchemaUseCase.list(PageQuery)` existe déjà (pas de nouveau port nécessaire) ;
`list_connector_definitions` réutilisera `ConnectorCatalog.listAll()` directement, comme le fait
déjà `ConnectorDefinitionController`.

**Phase 3 (design) close** — dernier point de conception ouvert (D2, emplacement du garde-fou
partagé) tranché via `AskUserQuestion` : **option (c)** — `TenantAdminGuard` (`domain.dossier`)
gagne une méthode `requireTenantOrWorkflowAdminOrApiService`, réutilisée par `domain.connector`
(via `requireTenantAdminOrApiService`, déjà existante) et `domain.workflow` (nouvelle méthode),
plutôt que de dupliquer un guard par bounded context ou de déplacer la classe dans un package
neutre. Vérifié avant de trancher : aucune règle ArchUnit n'interdit une dépendance entre
sous-packages du domaine, et un précédent déjà accepté existe (`domain.workflow` dépend déjà de
`domain.dossier.model.FieldDefinition`, TODO §55). En recensant tous les appelants des interfaces
`port.in` à modifier, le compte est passé de **4 à 5 interfaces** : `GetTaskQuestionSchemaUseCase`
a aussi besoin de l'acteur (pas seulement `Save`/`Delete`), car `TaskQuestionSchemaController` gate
également ses lectures (D5). Trouvaille utile : `DeployWorkflowTool` (tool `deploy_workflow`
existant, PR #82) résout déjà l'acteur (`accessContextFactory.currentActor()`) mais ne le transmet
pas à `saveWorkflowTypeConfigUseCase.create`/`update` — changement minimal (un paramètre à ajouter
à un appel déjà en place).

**Phase 4 (planning) close** — plan en **9 milestones / ~26 tâches** (comparable à `serveur-mcp`,
9 milestones / 32 tâches) : M1 nouvelle méthode `TenantAdminGuard` seule d'abord (pattern « gardes
domaine d'abord » déjà utilisé par `serveur-mcp` M1) ; M2/M3/M4 câblage du garde-fou par bounded
context (connecteurs, questions de tâche, correctif `WorkflowTypeConfigUseCase` limité à
`create`/`update`) ; M5 `define_dossier_type` étendu (indépendant, peut être fait en parallèle) ;
M6/M7 les 7 nouveaux tools MCP (lecture puis écriture) ; M8 IT HTTP M2M réelles + scénario bout en
bout (`define_connector_instance` → `define_dossier_type` → `define_task_question_schema` →
`deploy_workflow`, preuve du flux métier complet) ; M9 vérification globale + documentation.
Traçabilité testing → tâches vérifiée exhaustive.

**Phase 5 (implémentation TDD) close** — M1 à M9 enchaînés en TDD dans la même session. 16 tools
MCP au total (9 → 16). **3 écarts réels trouvés en cours d'implémentation, pas anticipés par le
design** : (1) `ConnectorPortsContractTest` (implémentation anonyme du port dans un test de
contrat existant) était un appelant supplémentaire non recensé en design, corrigé en même temps
que `DeployWorkflowTool`/`DeployWorkflowToolTest` ; (2) les méthodes `default`
`TaskQuestionSchemaRepository.page(...)`/`WorkflowTypeConfigRepository.page(...)` ne sont pas
exécutées par un mock Mockito standard (retournent `null`) — les nouveaux tests stubbent
directement `page(...)`, pas `findAll(...)` ; (3) seule régression trouvée par `./mvnw verify` :
`McpEndpointApiTest` figeait le compte de tools à 9, corrigé (16, test renommé). `./mvnw verify` :
**1653 tests, 0 échec**, ArchUnit conforme, couverture Jacoco tenue. Frontend : **289/289 Vitest**,
0 régression (zéro fichier `frontend/**` modifié, confirmé par `git diff --stat`). Playwright non
exécutable dans ce bac à sable (binaire `chrome-headless-shell` absent, pré-existant, sans rapport
avec cette feature) — non bloquant, feature backend-only. Documentation resynchronisée :
[docs/guides/GETTING-STARTED.md](../guides/GETTING-STARTED.md) §12 (9 → 16 tools),
[15-serveur-mcp.md](../specs/15-serveur-mcp.md) (bandeau d'extension). `CHANGELOG.md`
différé (aucune PR ouverte à ce stade, même convention que TODO §47/§55). Détail complet :
[docs/ai/implementation/2026-09-06-feature-extension-mcp-flux-metier.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-09-06-feature-extension-mcp-flux-metier.md).

**Phase 6 (mise à jour planning)** — déjà reflétée ci-dessus (les 9 milestones étaient marqués
`[x]` en fin de Phase 5).

**Phase 7 (check implementation) close** — revue fichier par fichier (pas par relecture du résumé
Phase 5) de tous les fichiers modifiés, comparés au design et aux requirements. Confirmé conforme :
pattern adaptateur fin sur les 7 nouveaux tools ; garde-fou domaine bien en tête de chaque méthode
concernée sans exception, dans les 3 use case impl ; asymétrie D5 réellement implémentée dans le
code (pas seulement non testée) — vérifiée à l'identique côté domaine et côté REST
(`WorkflowTypeConfigController.list`/`get` toujours sans acteur transmis) ; masquage secrets
(`ConnectorInstanceMcpView`) seul point de sortie des 3 tools connecteur, aucune fuite en clair
dans les messages d'erreur. **1 écart réel trouvé et corrigé** : `DefineConnectorInstanceTool`
sautait toute validation (`name`/`connectorType` requis, `config` contre le schéma connecteur) en
`dryRun=true` sur `create` — le test `if (dryRun)` était placé avant les contrôles, contrairement
au contrat annoncé et incohérent avec `define_dossier_type` (validation structurelle toujours
active en dryRun). Corrigé : contrôles requis avant le test dryRun, ajout d'une validation
`ConnectorCatalog.get(connectorType).validate(...)` dans la branche dryRun (`ConnectorCatalog`
désormais injecté dans le tool) — 3 nouveaux tests unitaires (`DefineConnectorInstanceToolTest` →
10/10) + 1 nouveau test d'intégration HTTP réel (`McpToolCallIntegrationTest` → 14/14). 2 questions
examinées et écartées (pas des bugs) : le garde-fou domaine n'est pas redondant avec la chaîne
Spring Security M2M (rôle métier vs. validité transport du JWT, objets différents) ; la validation
`ConnectorCatalog` en dryRun-create n'a pas besoin de garde propre (opération locale en mémoire,
aucun accès tenant). `./mvnw verify` final (JDK 25 installé dans ce bac à sable, absent au
démarrage du conteneur) : **1657 tests, 0 échec**, ArchUnit conforme, couverture Jacoco tenue.
Détail complet dans
[docs/ai/implementation/2026-09-06-feature-extension-mcp-flux-metier.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-09-06-feature-extension-mcp-flux-metier.md)
§ « Phase 7 — Check Implementation ».

**Phase 8 (dev-testing) close** — vérification Jacoco **classe par classe** (convention TODO §28)
sur les 14 classes nouvelles/modifiées, pas seulement à l'agrégat `BUNDLE`. **3 gaps réels
trouvés et comblés** : `list_connector_definitions`/`list_connector_instances`/
`list_task_question_schemas` ne testaient `page`/`size` que absents, jamais fournis explicitement
— contrairement au pattern déjà établi par `ListWorkflowTypesToolTest` (4 tests) — 1 test ajouté
par tool (BRANCH 50 % → 100 % chacun). `DefineConnectorInstanceTool` avait 6 branches non testées
(`isBlank()` vs `null` sur `id`/`name`/`connectorType`, `config` `null` sur create, `enabled`
explicite `false`, `restore=true`) — 6 tests ajoutés (BRANCH 70.8 % → 95.8 %). **1 gap
pré-existant identifié, non corrigé** : `ManageConnectorInstanceUseCaseImpl` reste à 69.4 % BRANCH
(sous le seuil de 75 % du dépôt) — vérifié via `git diff` que cette feature n'y a ajouté que
l'appel au garde-fou (une ligne sans branche), les branches non couvertes
(`mergeConfig`/`isBlankOrMask`/fusion `name`/`enabled`) étant de la logique métier pré-existante
et hors sujet de ce cycle — même principe que l'import mort laissé intact en Phase 7. `./mvnw
verify` final : **1666 tests, 0 échec** (+9 vs Phase 7), ArchUnit conforme, gate Jacoco `BUNDLE`
tenue. Détail complet dans
[docs/ai/testing/2026-09-06-feature-extension-mcp-flux-metier.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/testing/2026-09-06-feature-extension-mcp-flux-metier.md).

**Phase 9 (revue finale) close** — revue holistique du diff réel contre `origin/main` (53 fichiers,
+3703/-118, pas les 1043 fichiers de bruit d'un mauvais `merge-base`). Checklist complète passée :
alignement design/requirements confirmé, aucune dépendance circulaire (`TenantAdminGuard` n'importe
que son propre package), pas de breaking change externe (interfaces `port.in` internes au dépôt,
tous les appelants mis à jour dans le même commit), aucune fuite de secret, aucune migration de
schéma (rollback trivial), pas de fichier frontend touché (clause Playwright non applicable).
**2 trouvailles non bloquantes, documentées comme follow-ups hors périmètre** : (1)
`DefineDossierTypeTool.defineCreate()` a le même défaut que celui corrigé en Phase 7 sur
`DefineConnectorInstanceTool` (dryRun retourne avant de construire l'objet domaine, donc avant sa
validation) — mais celui-ci est pré-existant (PR #82), pas introduit par cette feature (le seul
ajout de cette feature à ce tool, `documentTemplateKeys`, est lui bien validé en dryRun) ; ironie
notée : ce cycle a cité ce tool comme précédent à suivre pour `DefineConnectorInstanceTool`, sans
savoir que le précédent lui-même était défectueux — trouvaille de Phase 9, pas de Phase 7 ; (2)
`ConnectorApiDtos.ConnectorResponse` garde sa propre méthode privée `isSecretKey` en double, alors
que cette feature a justement rendu la méthode équivalente `public` sur
`ManageConnectorInstanceUseCaseImpl` pour éviter une 3ᵉ duplication côté MCP — l'occasion de
nettoyer le doublon REST pré-existant n'a pas été prise ici (fichier hors périmètre déclaré).
**Cycle mergé** : [PR #111](https://github.com/superjojo79/FlowForge/pull/111), mergée sur `main`
sur demande explicite de l'utilisateur **sans attendre la CI GitHub Actions** (crédits épuisés) —
décision basée sur la vérification locale fraîche (`./mvnw verify` : 1675 tests, 0 échec, même
build que celui poussé). `CHANGELOG.md` mis à jour (entrées Ajouté + Corrigé référençant la PR).
Détail complet dans
[docs/ai/implementation/2026-09-06-feature-extension-mcp-flux-metier.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-09-06-feature-extension-mcp-flux-metier.md)
§ « Phase 9 — Revue finale ».

**Post-Phase 9 — revue adversariale (sous-agent) et correctifs** : analyse dédiée du diff complet
par un sous-agent, en se plaçant du point de vue d'un porteur de JWT M2M déjà authentifié cherchant
une élévation de privilège ou une fuite de secret. **1 faille bloquante réelle trouvée et
corrigée** : `authHeaderValue` (credential HTTP réel du connecteur `http-rest`, déclaré
`ConfigFieldType.PASSWORD`) n'était reconnu par aucune des heuristiques de détection de secret par
nom (`isSecretKey`/`isPasswordKey`), donc **jamais chiffré en base ni masqué en lecture** — bug
pré-existant (avant cette feature) mais dont ce cycle étend concrètement la portée en ajoutant les
premiers chemins de lecture M2M des configurations de connecteur. Corrigé en ajoutant à
`ConnectorCatalog` une méthode `isPasswordField(type, key)` qui consulte le schéma déclaré du
connecteur (source de vérité unique), propagée au masquage MCP (`ConnectorInstanceMcpView`), au
masquage REST (`ConnectorApiDtos.ConnectorResponse` — dont l'ancien doublon `isSecretKey` est
supprimé, fermant au passage la trouvaille (2) ci-dessus) et au chiffrement au repos
(`ConnectorSecretCipher`/`EnvMasterKeyCipher`, qui gagnent un paramètre `connectorType`). **Piège
non détectable par lecture statique** : injecter `ConnectorCatalog` dans `EnvMasterKeyCipher` casse
le démarrage Spring par une dépendance circulaire réelle (`EnvMasterKeyCipher` → `ConnectorCatalog`
→ `FlowableSignalConnector` → moteur Flowable → `WorkflowStateEventListener` →
`SendNotificationUseCase` → `JpaNotificationWebhookRepositoryAdapter`, qui dépend du même cipher
pour les secrets de webhooks de notification) — corrigé par `@Lazy` sur le paramètre, après un
échec de contexte confirmé puis résolu. **2 findings mineurs corrigés** : `define_connector_instance`
create dryRun ne réaffirmait jamais le garde-fou (nouvelle méthode `assertAuthorized`, cohérent
avec la branche update qui le fait déjà) ; casts non défensifs sur listes hétérogènes dans
`DefineDossierTypeTool`/`DeployWorkflowTool` (remplacés par des `instanceof` explicites). **1
risque documenté sans correctif** : tout JWT M2M valide reçoit `API_SERVICE`, traité comme
équivalent `TENANT_ADMIN` par le garde-fou — décision assumée (un seul client M2M de confiance
prévu), documentée dans le design doc § D6 plutôt que corrigée (dériver les rôles des scopes JWT
réels serait un changement plus large, hors périmètre). `./mvnw verify` final : **1675 tests, 0
échec** (+9 vs Phase 9), ArchUnit conforme, gate Jacoco tenue.

**Mise à jour (revue adversariale lot A3, 2026-09-09, A3-M2)** : ce risque n'est plus totalement
sans garde-fou côté configuration. `McpAccessContextFactory` accepte désormais
`M2mSecurityProperties#trustedClientIds()` (property `app.security.m2m.trusted-client-ids`,
`M2M_TRUSTED_CLIENT_IDS`) : vide (défaut, comportement historique inchangé) = tout JWT M2M reçoit
`API_SERVICE` ; renseignée = seuls les `client_id`/`sub` listés obtiennent `API_SERVICE`, les
autres sont rejetés (403). Le risque de fond (aucune dérivation des scopes JWT réels) reste assumé
et non corrigé — à activer explicitement avant d'accorder l'accès `/api/m2m/mcp` à un second client
M2M à portée réduite. Détail complet dans
[docs/ai/implementation/2026-09-06-feature-extension-mcp-flux-metier.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-09-06-feature-extension-mcp-flux-metier.md)
§ « Post-Phase 9 — Revue adversariale et correctifs de sécurité ».

**Suite, sur demande :** ouverture PR + `CHANGELOG.md` ; éventuel cycle dédié pour le follow-up
`DefineDossierTypeTool` restant (trouvaille (1) ci-dessus, toujours non corrigée). Documents lifecycle :
[docs/ai/requirements/2026-09-06-feature-extension-mcp-flux-metier.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-06-feature-extension-mcp-flux-metier.md),
[docs/ai/design/2026-09-06-feature-extension-mcp-flux-metier.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-09-06-feature-extension-mcp-flux-metier.md),
[docs/ai/testing/2026-09-06-feature-extension-mcp-flux-metier.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/testing/2026-09-06-feature-extension-mcp-flux-metier.md),
[docs/ai/planning/2026-09-06-feature-extension-mcp-flux-metier.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/planning/2026-09-06-feature-extension-mcp-flux-metier.md).

**Impacte :** `infrastructure/mcp/tool/**` (7 nouveaux tools + `DefineDossierTypeTool` étendu),
`domain/connector/**`, `domain/workflow/**` (`TaskQuestionSchemaUseCaseImpl`,
`WorkflowTypeConfigUseCaseImpl`), [15-serveur-mcp.md](../specs/15-serveur-mcp.md),
[06-connecteurs.md](../specs/06-connecteurs.md).

**Détail :** [24-extension-mcp-flux-metier.md](../specs/24-extension-mcp-flux-metier.md).
