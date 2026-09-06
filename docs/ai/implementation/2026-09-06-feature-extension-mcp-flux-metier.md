---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
---

# Implementation Guide — `extension-mcp-flux-metier` (TODO §56)

> Phase 5 (implémentation TDD) close, M1–M9 enchaînés — `./mvnw verify` : 1653 tests, 0 échec.
> Phase 7 (check implementation) close — 1 bug dryRun trouvé et corrigé (`DefineConnectorInstanceTool`,
> +4 tests, 1657 tests). Phase 8 (dev-testing) close — 3 gaps de pagination (page/size jamais
> fournis explicitement sur les 3 nouveaux tools de liste) + 6 branches non testées sur
> `DefineConnectorInstanceTool` trouvés via Jacoco classe par classe et comblés (+9 tests). 1 gap
> pré-existant documenté sans correctif (`ManageConnectorInstanceUseCaseImpl`, 69.4 % BRANCH, hors
> périmètre de cette feature). `./mvnw verify` final (JDK 25, installé dans ce bac à sable en
> Phase 7 — absent au démarrage du conteneur) : **1666 tests, 0 échec**, ArchUnit conforme,
> couverture Jacoco `BUNDLE` tenue (LINE ≥ 85 % / BRANCH ≥ 75 %). Frontend : **289/289 Vitest**,
> 0 régression (zéro fichier `frontend/**` modifié, confirmé par `git diff --stat`). Playwright non
> exécutable dans ce bac à sable (binaire `chrome-headless-shell` absent, pré-existant, sans rapport
> avec cette feature) — non bloquant, feature backend-only.

## Ce qui a été livré

7 nouveaux tools MCP + extension de `define_dossier_type`, sur `POST /api/m2m/mcp` :

| Tool | Use case / port |
|---|---|
| `list_connector_definitions` | `ConnectorCatalog.listAll()` (pas de use case, catalogue statique) |
| `list_connector_instances` | `ManageConnectorInstanceUseCase.list` |
| `get_connector_instance` | `ManageConnectorInstanceUseCase.get` |
| `define_connector_instance` | `ManageConnectorInstanceUseCase.create`/`update` |
| `list_task_question_schemas` | `GetTaskQuestionSchemaUseCase.list` |
| `get_task_question_schema` | `GetTaskQuestionSchemaUseCase.getByKey` |
| `define_task_question_schema` | `SaveTaskQuestionSchemaUseCase.save` |
| `define_dossier_type` (étendu) | `documentTemplateKeys` optionnel, absent = préservé en update |

Plus le correctif de sécurité D-MCP-EXT-RBAC-GUARD/D5 (garde-fou domaine sur
`ManageConnectorInstanceUseCaseImpl` toutes opérations, `TaskQuestionSchemaUseCaseImpl`/
`GetTaskQuestionSchemaUseCaseImpl` toutes opérations, `WorkflowTypeConfigUseCaseImpl`
`create`/`update` seulement) et la nouvelle méthode `TenantAdminGuard
.requireTenantOrWorkflowAdminOrApiService`.

## Fichiers modifiés

**Nouveau**

- `infrastructure/mcp/tool/{ListConnectorDefinitionsTool,ListConnectorInstancesTool,GetConnectorInstanceTool,DefineConnectorInstanceTool,ListTaskQuestionSchemasTool,GetTaskQuestionSchemaTool,DefineTaskQuestionSchemaTool}.java`
- `infrastructure/mcp/tool/ConnectorInstanceMcpView.java` (masquage secrets, réutilise
  `ManageConnectorInstanceUseCaseImpl.isSecretKey`/`SECRET_MASK`)
- Tests unitaires miroir de chaque tool + `WorkflowTypeConfigUseCaseImplTest`,
  `TaskQuestionSchemaUseCaseImplTest` (nouveaux, la garde n'avait aucun test dédié avant)

**Modifié**

- `domain/dossier/TenantAdminGuard.java` (+`requireTenantOrWorkflowAdminOrApiService`)
- `domain/connector/port/in/ManageConnectorInstanceUseCase.java` /
  `usecase/ManageConnectorInstanceUseCaseImpl.java` (+ acteur sur toutes les méthodes, + garde ;
  `isSecretKey` passé `public` pour être réutilisé par `ConnectorInstanceMcpView`)
- `domain/workflow/port/in/{SaveTaskQuestionSchemaUseCase,DeleteTaskQuestionSchemaUseCase,GetTaskQuestionSchemaUseCase}.java`
  / `usecase/TaskQuestionSchemaUseCaseImpl.java` (+ acteur sur toutes les méthodes, + garde ;
  javadoc obsolète « no domain guard needed here » retiré)
- `domain/workflow/port/in/SaveWorkflowTypeConfigUseCase.java` /
  `usecase/WorkflowTypeConfigUseCaseImpl.java` (+ acteur sur `create`/`update` uniquement ; `list`/
  `getByKey` inchangés)
- `infrastructure/mcp/tool/DefineDossierTypeTool.java` (+`documentTemplateKeys`)
- `infrastructure/mcp/tool/DeployWorkflowTool.java` (transmet l'acteur déjà résolu à
  `create`/`update`)
- `infrastructure/web/connector/ConnectorAdminController.java`,
  `infrastructure/web/workflow/TaskQuestionSchemaController.java`,
  `infrastructure/web/workflow/WorkflowTypeConfigController.java` (passent l'acteur déjà
  disponible via `Authentication`/`AccessContextFactory`)
- `infrastructure/config/{ConnectorConfig,WorkflowConfig}.java` (injectent `TenantAdminGuard`,
  bean déjà existant depuis `DossierConfig`, réutilisé tel quel)
- `McpEndpointApiTest` (9 → 16 tools attendus — seule régression trouvée en Phase 5, corrigée)

## Écarts réels trouvés en Phase 5 (TDD), pas anticipés par le design

1. **Périmètre du garde-fou plus large que prévu** — en recensant tous les appelants avant
   d'écrire le code (pas après), `ConnectorPortsContractTest` (contrat de test existant avec une
   implémentation anonyme de `ManageConnectorInstanceUseCase`) est apparu comme appelant
   supplémentaire non listé en design — corrigé en même temps que `DeployWorkflowToolTest`.
2. **Mockito et méthodes `default`** — `TaskQuestionSchemaRepository.page(...)`/
   `WorkflowTypeConfigRepository.page(...)` sont des méthodes `default` déléguant à `findAll(...)` ;
   un mock Mockito standard ne les exécute pas (retourne `null`) — les tests stubbent directement
   `page(...)`, pas `findAll(...)`, découvert à l'exécution (2 tests rouges corrigés).
3. **`McpEndpointApiTest.toolsList_registersExactlyTheNineExpectedTools`** — seule régression
   trouvée par `./mvnw verify` : comptage figé à 9 tools, corrigé (16 tools, liste complétée,
   nom de test renommé).

## Décisions d'implémentation non couvertes explicitement par le design

- **`define_connector_instance` : create/update par présence de `id`, pas un upsert par nom** —
  `ConnectorInstance` est identifiée par UUID serveur (pas un `code` choisi par l'appelant comme
  `DossierType`), donc le tool suit le split REST (`POST` sans id vs `PATCH /{id}`) plutôt que le
  pattern upsert de `define_dossier_type`. Absent de `id` → `name`/`connectorType` requis,
  `config` validé par `ConnectorDefinition.validate`. Présent → mise à jour partielle
  (`name`/`enabled`/`config`/`restore` tous optionnels), même contrat que `UpdateCommand`.
- **`dryRun` sur `define_connector_instance`** — sur `create`+`dryRun`, valide `name`/`connectorType`
  requis puis `config` contre le schéma du type via `ConnectorCatalog` (aucune I/O, correctif
  Phase 7 — voir plus bas, la première version sautait cette validation) ; sur `update`+`dryRun`,
  appelle `get(id, tenantId, actor)` pour prouver existence + autorisation sans persister (pas de
  validation de `config` fusionné : le merge secret-préservant est privé à l'use case, documenté
  dans la Javadoc du tool comme limite assumée, pas un oubli).
- **`dryRun` sur `define_task_question_schema` : validation réelle sur `create` et `update`** —
  contrairement au point précédent, construire un `TaskQuestionSchema` est une opération domaine
  pure (pas d'I/O) : son constructeur compact valide déjà (champs non vides, noms uniques, pas de
  `FILE`, pas de collision de variable réservée) — le dry run construit l'objet et le jette après
  validation, sans jamais appeler `save`. Documenté explicitement dans la Javadoc du tool pour ne
  pas laisser croire à une incohérence avec le point précédent.

## Sécurité

Voir « Ce qui a été livré » — le correctif de garde-fou domaine est la pièce de sécurité centrale
de cette feature : avant elle, tout appelant qui invoquait `ManageConnectorInstanceUseCaseImpl`,
`TaskQuestionSchemaUseCaseImpl` ou `WorkflowTypeConfigUseCaseImpl` (create/update) directement
(comme le fait tout tool MCP) contournait entièrement le RBAC REST (`@PreAuthorize`), qui n'est
qu'une annotation sur le contrôleur. Testé par des cas explicites acteur-sans-rôle par méthode
(pas seulement « ça compile »), pour chacun des 3 use cases. Secrets connecteurs : jamais
sérialisés en clair par les 3 tools de lecture/écriture connecteur (`ConnectorInstanceMcpView`,
testé).

## Phase 7 — Check Implementation

Revue fichier par fichier de tout ce qui est listé sous « Fichiers modifiés », comparé au design et
aux requirements — pas une relecture du résumé Phase 5, mais une nouvelle lecture du code livré.

**Points vérifiés explicitement (conformes) :**

1. **Pattern adaptateur fin** — les 7 nouveaux tools MCP ne contiennent aucune logique métier
   propre : ils parsent/valident la forme des arguments MCP, résolvent l'acteur via
   `McpAccessContextFactory`, délèguent au port `in` existant, et mettent en forme le résultat
   (`ConnectorInstanceMcpView` pour le masquage). Même forme que les 9 tools préexistants.
2. **Garde-fou en tête de méthode, sans exception** — relu `ManageConnectorInstanceUseCaseImpl`
   (6/6 méthodes), `TaskQuestionSchemaUseCaseImpl` (4/4), `WorkflowTypeConfigUseCaseImpl`
   (`create`/`update`, 2/2) : l'appel à `tenantAdminGuard.require...` est bien la première
   instruction de chaque méthode concernée. Note : `update()` de
   `ManageConnectorInstanceUseCaseImpl` appelle en interne `restore()`, qui revérifie le garde —
   redondant mais inoffensif (même acteur, même garde), non corrigé (pas un bug).
3. **Asymétrie D5 réellement implémentée, pas juste non testée** — confirmé dans le code (pas
   seulement dans les tests) : `SaveWorkflowTypeConfigUseCase`/`GetWorkflowTypeConfigUseCase` —
   seules `create`/`update` ont un paramètre acteur ; `getByKey`/`list` n'en ont toujours aucun,
   ni dans l'interface ni dans l'impl. Confirmé à l'identique côté REST :
   `WorkflowTypeConfigController.list`/`get` n'appellent toujours que
   `accessContextFactory.requireOidcPrincipal(authentication)` (vérif OIDC générique), sans
   transmettre d'acteur au use case — alors que `create`/`update` le font. Asymétrie cohérente de
   bout en bout.
4. **Masquage des secrets** — `ConnectorInstanceMcpView.from(...)` est le seul point de sortie des
   3 tools connecteur (`list`/`get`/`define`) vers le protocole MCP ; aucun ne renvoie
   `ConnectorInstanceData` brut. Vérifié qu'aucun message d'exception (`ConnectorValidationException`,
   `UnknownConnectorException`) n'interpole de valeur de `config` — ils ne portent que des noms de
   champs/types, jamais de valeurs.
5. **Contrat `dryRun`** — `define_task_question_schema` : conforme, la validation (construction de
   `TaskQuestionSchema`, dont le constructeur compact valide) a toujours lieu avant le
   `if (rawDryRun)`, sur les deux branches create/update. `define_connector_instance` : **non
   conforme, corrigé** — voir ci-dessous.

**Écart trouvé et corrigé :**

- **Bug — `DefineConnectorInstanceTool.defineCreate` sautait toute validation en `dryRun`.**
  L'ordre initial était : `if (dryRun) return stub;` placé *avant* les contrôles `name`/
  `connectorType` requis et avant toute validation de `config`. Un appel
  `define_connector_instance(dryRun=true)` sans `name`, ou avec un `config` invalide pour le
  `connectorType`, réussissait silencieusement — à l'opposé du contrat annoncé (« validates
  without persisting ») et incohérent avec `define_dossier_type`, où toute validation
  structurelle a lieu avant le retour dryRun. Corrigé : les contrôles `name`/`connectorType`
  passent avant le test `dryRun`, et une nouvelle validation `connectorCatalog.get(connectorType)
  .validate(resolvedConfig)` est ajoutée dans la branche dryRun (miroir non dupliqué du chemin
  réel — `ConnectorCatalog` est désormais injecté dans le tool). Tests : 3 nouveaux tests
  unitaires (`create_dryRun_stillValidatesConfigAgainstCatalog`,
  `create_dryRun_unknownConnectorType_isRejected`, `create_dryRun_missingName_isStillRejected`,
  `DefineConnectorInstanceToolTest` → 10/10 verts) + 1 nouveau test d'intégration HTTP réel
  (`defineConnectorInstance_dryRun_rejectsInvalidConfigWithoutPersisting`,
  `McpToolCallIntegrationTest` → 14/14 verts).

**Observations examinées et écartées (pas des bugs) :**

- *Le garde-fou domaine n'est-il pas redondant avec le fait que le transport MCP est déjà réservé
  au rôle `API_SERVICE` (chaîne de sécurité Order 1) ?* Non écarté à tort : le garde vérifie un
  rôle *métier* (`TENANT_ADMIN`/`WORKFLOW_ADMIN`/`API_SERVICE`) porté par l'acteur résolu depuis le
  JWT M2M, alors que la chaîne Spring Security vérifie seulement que le token M2M est valide et
  porte `API_SERVICE` au niveau transport. Un agent M2M authentifié reste un acteur métier soumis
  au RBAC domaine — les deux contrôles ont des objets différents, aucun n'est redondant avec
  l'autre.
- *`define_connector_instance` en `dryRun` sur `create` n'appelle aucun garde explicite avant la
  validation `ConnectorCatalog`* — vérifié : ce n'est pas un trou RBAC, `ConnectorCatalog.get(...)
  .validate(...)` est une opération purement locale (schéma statique en mémoire, aucun accès
  tenant), il n'y a rien à protéger par un garde-fou tant qu'aucune donnée du tenant n'est lue ou
  écrite. Le chemin non-dryRun, lui, reste protégé par le garde dans
  `ManageConnectorInstanceUseCaseImpl.create`.

**Conclusion Phase 7 :** implémentation alignée avec le design et les requirements ; un écart réel
trouvé et corrigé (dryRun connecteur), aucune autre déviation, aucune fuite de secret identifiée,
aucun appelant manquant supplémentaire (balayage `grep` sur les 5 interfaces modifiées : tous les
appelants recensés étaient déjà connus et à jour). Pas de retour en Phase 3/Phase 5 nécessaire.

## Phase 9 — Revue finale (dev-review)

Revue holistique du diff réel de la feature contre `origin/main`
(`git diff --stat e9d38d7..HEAD` : **53 fichiers, +3703/-118**, hors bruit d'un `merge-base` qui
incluait à tort 1043 fichiers d'un autre historique) — pas seulement une relecture des phases
précédentes.

**Checklist finale :**

- **Alignement design/requirements** : confirmé (Phases 3/7). Aucune déviation non documentée.
- **Dépendances circulaires / ArchUnit** : vérifié que `TenantAdminGuard` (`domain.dossier`)
  n'importe que son propre package (`domain.dossier.model`) — aucun risque de cycle avec
  `domain.connector`/`domain.workflow`, qui en dépendent dans un seul sens (précédent déjà
  accepté, TODO §55). 7 tests `ArchitectureRulesTest` toujours verts.
- **Contrats de rupture (breaking changes)** : les 5 interfaces `port.in` modifiées
  (signature + paramètre acteur) sont internes au dépôt (pas d'API publique externe) — modification
  en place avec tous les appelants mis à jour dans le même commit, conforme à la règle du dépôt
  pour ce cas (pas de dépréciation nécessaire).
- **Sécurité / secrets** : masquage vérifié exhaustif sur les 3 tools connecteur
  (`ConnectorInstanceMcpView`, `GetConnectorInstanceTool`, `ListConnectorInstancesTool`) —
  relecture complète, aucun chemin de retour n'expose `ConnectorInstanceData` brut.
- **Rollback safety** : aucune migration de schéma dans cette feature (uniquement du code Java) —
  vérifié (`git diff --stat` sur `*.xml`/`*.sql`/changelogs : vide). Revert trivial si besoin.
- **Frontend / E2E** : zéro fichier `frontend/**` touché — la clause Playwright obligatoire du
  contrat `dev-review` ne s'applique pas (feature backend-only, confirmé).
- **Tests couvrent les changements** : `./mvnw verify` — **1666 tests, 0 échec**, gate Jacoco
  `BUNDLE` tenue ; Vitest 289/289, 0 régression.

**2 trouvailles non bloquantes (follow-ups recommandés, hors périmètre de cette feature) :**

1. **`DefineDossierTypeTool.defineCreate()` — même classe de bug que le correctif Phase 7, mais
   pré-existante (PR #82, pas introduite par cette feature).** Le test `if (dryRun)` (ligne 155)
   retourne le stub *avant* de construire l'objet `DossierType`, dont le constructeur compact
   valide pourtant des règles réelles (noms de `fieldDefinitions` dupliqués, politique de
   rétention/SLA). Un appel `dryRun=true` sur `create` avec des noms de champs dupliqués
   réussirait donc silencieusement, alors que le même appel sans `dryRun` échouerait. Ce n'est
   **pas** un écart introduit par `extension-mcp-flux-metier` : le seul ajout de cette feature à ce
   tool (`documentTemplateKeys`) est lui bien validé même en dryRun (`parseDocumentTemplateKeys`
   s'exécute avant la branche dryRun, ligne 119). Ce cycle a d'ailleurs involontairement reproduit
   ce même défaut dans `DefineConnectorInstanceTool` (code neuf, donc corrigé en Phase 7) en le
   citant comme précédent à suivre — la trouvaille de Phase 9 est que le précédent lui-même était
   défectueux. Recommandation : un correctif dédié dans un futur cycle sur `DefineDossierTypeTool`
   seul (hors scope ici, même principe que le point 2 ci-dessous et que le gap Jacoco documenté en
   Phase 8 sur `ManageConnectorInstanceUseCaseImpl` : ne pas élargir cette PR à du code que la
   feature n'a pas touché sur ce point précis).
2. **Opportunité de réutilisation non exploitée** : `ConnectorApiDtos.ConnectorResponse` conserve
   sa propre méthode privée `isSecretKey` (duplication déjà connue et documentée en Phase 1/D3),
   alors que cette feature a justement rendu `ManageConnectorInstanceUseCaseImpl.isSecretKey`
   `public` pour que `ConnectorInstanceMcpView` la réutilise plutôt que d'en créer une 3ᵉ copie.
   L'occasion de retirer le doublon pré-existant côté REST n'a pas été prise ici (fichier hors
   périmètre déclaré de cette feature). Recommandation : petit nettoyage séparé, non bloquant.

**Conclusion Phase 9 :** cycle `dev-lifecycle` **prêt à pousser / ouvrir une PR**. Aucun blocage.
Les 2 trouvailles ci-dessus sont des follow-ups documentés, pas des régressions de cette feature.
`CHANGELOG.md` reste différé (aucune PR ouverte à ce stade, même convention que TODO §47/§55) —
à faire à l'ouverture de la PR, sur demande.

## Post-Phase 9 — Revue adversariale et correctifs de sécurité

Un sous-agent a mené une analyse adversariale dédiée du diff complet de la feature (`e9d38d795..
HEAD`), en se plaçant du point de vue d'un porteur de JWT M2M déjà authentifié (`API_SERVICE`)
cherchant une élévation de privilège, une fuite de secret ou un contournement de validation. **1
faille bloquante réelle trouvée et corrigée**, plus 2 findings mineurs corrigés et 1 risque
architectural documenté comme accepté.

### Bloquant — fuite en clair du champ `authHeaderValue` (corrigé)

`HttpRestConnector` déclare `authHeaderValue` (`ConfigFieldType.PASSWORD` — un credential HTTP
réel, envoyé tel quel comme header `Authorization`) mais la détection des secrets était
**par nom** (`isSecretKey`/`isPasswordKey` : `endsWith("secret")`/`endsWith("password")`), qui ne
reconnaît pas `authHeaderValue`. Conséquence vérifiée : jamais chiffré en base
(`EnvMasterKeyCipher`), jamais masqué en lecture (`ConnectorInstanceMcpView`,
`ConnectorApiDtos.ConnectorResponse`) — un bug **pré-existant** (avant `extension-mcp-flux-metier`)
mais dont cette feature étend concrètement la portée : avant, seul un `TENANT_ADMIN` humain via la
session BFF pouvait le lire ; désormais tout service M2M le peut aussi via
`get_connector_instance`/`list_connector_instances`.

**Correctif** : `ConnectorCatalog` gagne une méthode par défaut `isPasswordField(type, key)` qui
consulte le schéma déclaré du connecteur (`ConfigField.type() == PASSWORD`) — source de vérité
unique, en plus (pas à la place) de l'heuristique de nom existante. Propagé partout où un secret
était détecté par nom seul :
- `ManageConnectorInstanceUseCaseImpl.mergeConfig` (préservation du secret existant à l'update) ;
- `ConnectorInstanceMcpView` (masquage MCP — `from` prend désormais `ConnectorCatalog`) ;
- `ConnectorApiDtos.ConnectorResponse` (masquage REST — même changement de signature, et son
  ancien `isSecretKey` privé dupliqué est supprimé au passage, réutilisant
  `ManageConnectorInstanceUseCaseImpl.isSecretKey` : ferme aussi la trouvaille (b) de Phase 9) ;
- `ConnectorSecretCipher`/`EnvMasterKeyCipher` (chiffrement au repos — les deux méthodes du port
  gagnent un paramètre `connectorType`, propagé depuis `JpaConnectorInstanceRepositoryAdapter`, qui
  lit `connectorType` sur `ConnectorInstanceData`/`ConnectorInstanceJpaEntity`, cette dernière ayant
  gagné un accessseur dédié).

**Piège trouvé en cours de correctif — dépendance circulaire réelle, non détectable par lecture
statique du code** : injecter `ConnectorCatalog` directement dans `EnvMasterKeyCipher` casse le
démarrage du contexte Spring. Chaîne : `EnvMasterKeyCipher` → `ConnectorCatalog`
(`ConnectorRegistry`) → `FlowableSignalConnector` (un `ConnectorDefinition`) → `RuntimeService`
Flowable → configuration du moteur → `WorkflowStateEventListener` → `SendNotificationUseCase` →
`JpaNotificationWebhookRepositoryAdapter`, qui dépend **aussi** de `ConnectorSecretCipher` (le même
cipher, réutilisé génériquement pour les secrets de webhooks de notification, pas seulement les
connecteurs) → cycle. Confirmé par un échec réel de chargement de contexte
(`UnsatisfiedDependencyException: ... Requested bean is currently in creation`) avant correctif.
**Corrigé par `@Lazy` sur le paramètre `ConnectorCatalog`** du constructeur d'`EnvMasterKeyCipher`
— rompt la résolution éager sans changer le comportement (le catalogue n'est consulté qu'au moment
effectif du chiffrement/déchiffrement, jamais à la construction du bean).

Tests ajoutés (schéma déclaré, pas le nom, sur `authHeaderValue`) : unitaires sur
`ManageConnectorInstanceUseCaseImplTest`, `GetConnectorInstanceToolTest`, `EnvMasterKeyCipherTest`,
`ConnectorResponseSecretMaskingTest` (garde-fou anti-régression dédié, D-SECRETS-AUDIT) ; IT réelles
sur `ConnectorAdminApiTest` (REST) et `McpToolCallIntegrationTest` (MCP, round-trip create → get →
list, preuve qu'aucun chemin ne renvoie la valeur en clair). `./mvnw verify` final (avec les 3
correctifs de cette section + les 2 mineurs ci-dessous) : **1675 tests, 0 échec** (+9 vs. Phase 8),
ArchUnit conforme, gate Jacoco `BUNDLE` tenue.

### Mineur — `define_connector_instance` create dryRun ne réaffirmait jamais le garde-fou (corrigé)

Contrairement à sa propre branche `update` (qui réaffirme l'autorisation via `get()` pendant la
prévisualisation), la branche `create` en `dryRun=true` ne rappelait jamais le garde-fou —
incohérence dans du code neuf de ce cycle. Corrigé par une nouvelle méthode
`ManageConnectorInstanceUseCase.assertAuthorized(actor)` (délègue au garde-fou sans effet de bord),
appelée en tête de la branche dryRun de `defineCreate`. Testé (`create_dryRun_reassertsAuthorization`).

### Mineur — casts non défensifs sur listes hétérogènes (corrigé)

`DefineDossierTypeTool.parseDocumentTemplateKeys` et `DeployWorkflowTool` (`observerUserIds`,
`allowedDossierStatuses`, `onStartDossierStatus`) utilisaient un cast direct
(`String.class::cast`/`(String) item`) au lieu d'un `instanceof` explicite — un élément non-`String`
levait une `ClassCastException` générique (jamais un stack trace exposé, le framework MCP
intercepte proprement, mais message moins soigné que les `IllegalArgumentException` explicites déjà
utilisées ailleurs dans les mêmes fichiers). Corrigé par cohérence de style, aucun changement de
comportement observable côté sécurité (l'agent avait qualifié ce point d'observation, pas de faille).

### Risque accepté et documenté (pas corrigé) — confiance « tout ou rien » du JWT M2M

`McpAccessContextFactory` attribue le rôle `API_SERVICE` à tout porteur de JWT M2M valide (constante,
pas dérivée des claims réels du token) ; combiné au garde-fou D5, cela signifie que tout client M2M
autorisé devient de facto administrateur complet des connecteurs/questions de tâche. Pré-existant
(PR #82), pas introduit par cette feature, mais celle-ci en étend la portée (nouveaux chemins de
lecture des secrets connecteur). Documenté comme décision assumée (un seul client M2M de confiance
prévu à ce stade) dans le Javadoc de `McpAccessContextFactory` et dans
[docs/ai/design/2026-09-06-feature-extension-mcp-flux-metier.md](../design/2026-09-06-feature-extension-mcp-flux-metier.md)
§ D6, plutôt que corrigé — dériver les rôles des scopes JWT réels serait un changement plus large
touchant l'émission des tokens M2M, hors périmètre d'un correctif de findings. À revisiter si
plusieurs clients M2M à portées différentes sont introduits.
