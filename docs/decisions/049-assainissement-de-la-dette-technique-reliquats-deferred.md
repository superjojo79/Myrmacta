# §49 — Assainissement de la dette technique — reliquats [`DEFERRED-FEATURES.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/DEFERRED-FEATURES.md) §1

> **Statut :** clos · **Section :** [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) · ancien `TODO.md` §49 · [registre](README.md)

**Clos** (2026-09-01, `assainissement-dette-technique`,
[PR #97](https://github.com/superjojo79/FlowForge/pull/97)) — cycle `dev-lifecycle`
complet (Phases 1-9). Vérification item par item contre le code réel (2026-08-31) : **2 items
étaient déjà clos, jamais répercutés dans le doc** (`CHECK SQL dossier_type_acl.permission` —
migration `014` déjà livrée, PR #72 ; validation blank `tenantId` — `TenantProperties.id` porte
déjà `@NotBlank`) ; **2 réellement ouverts, livrés par cette feature** (validation blank
`processKey`/`workflowKey`, `dossierId` en MDC par item du job de purge) ; **1 confirmé redondant
sans code** (`Persistable`/`isNew()`).

**Décisions actées (`AskUserQuestion`, options recommandées) :**

- **D-BLANK-VALIDATION-SCOPE = défense en profondeur dans le domaine** — garde
  `if (x.isBlank()) throw new IllegalArgumentException(...)` ajoutée dans les compact constructors
  de `WorkflowTypeConfig.workflowKey`, `TriggerOutboxPayload.processKey`,
  `WorkflowStartedEvent.processKey`, `DossierType.workflowKey`, sur le modèle
  `DossierType.code`/`CODE_PATTERN`. Tous les points d'entrée externes (REST, MCP) bloquaient déjà
  le blank en amont (`required()`/`requireString()`) — cette garde est une seconde ligne de
  défense, pas la fermeture d'un bug atteignable.
- **D-MDC-DOSSIER-MECHANISM = callback `Function<UUID, Runnable>`** — `ProcessPurgeOutboxUseCase.
  processBatch` gagne un 3e paramètre invoqué avant `purgeDossier(record)`, fermé dans un `finally`
  englobant tout le traitement de l'item (pas seulement le `catch`). `PurgeOutboxJob` fournit le
  callback via `MdcScope.dossier(...)`. Justifié par une trouvaille concrète en design : le seul
  vrai `log.warn` par item existant (`FlowableWorkflowEngineAdapter.
  deleteProcessInstanceBestEffort`) avale son exception sans la relancer — un scope limité au
  `catch` l'aurait raté. Preuve directe via un test d'intégration multi-items utilisant le vrai
  `ProcessPurgeOutboxUseCaseImpl` (pas mocké).
- **D-PERSISTABLE-CONFIRM = confirmé redondant par preuve documentée** — `DossierJpaEntity.version`
  est un `@Version private long` primitif, `SpringDataDossierRepository` un `JpaRepository`
  standard sans `Persistable` : comportement déjà correct via
  `JpaMetamodelEntityInformation.isNew()`. Aucun code écrit.

**Vérifié en implémentation (Phase 7)** : tous les appelants de production des 4 constructeurs
gardés passent déjà par un DTO REST/MCP qui blank-check en amont — aucun risque de régression
(confirmé par `./mvnw verify`). Trouvaille mineure hors périmètre non corrigée :
`WorkflowTriggerEvent.processKey` porte le même gap, mais son seul appelant construit un
`TriggerOutboxPayload` immédiatement après, déjà gardé par cette feature.

**Validation :** `./mvnw verify` vert (1430 tests, `ArchitectureRulesTest.
domain_has_no_framework_dependency` toujours vert, seuils Jacoco maintenus). Balayage Jacoco classe
par classe (Phase 8) : 4 nouvelles gardes + code du callback 100% couverts, aucun gap introduit.
Feature confirmée backend seule : `npm run test` (247 tests) et `npm run test:e2e` (59 tests) verts
en régression, zéro fichier `frontend/**` touché.

**Impacte :** `WorkflowTypeConfig`, `TriggerOutboxPayload`, `WorkflowStartedEvent`, `DossierType`,
`ProcessPurgeOutboxUseCase`, `ProcessPurgeOutboxUseCaseImpl`, `PurgeOutboxJob`,
[`DEFERRED-FEATURES.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/DEFERRED-FEATURES.md), `CHANGELOG.md`.

**Détail :** [19-assainissement-dette-technique.md](../specs/19-assainissement-dette-technique.md).
