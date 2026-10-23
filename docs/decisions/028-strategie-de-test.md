# §28 — Stratégie de test

> **Statut :** clos · **Section :** [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) · ancien `TODO.md` §28 · [registre](README.md)

**Livré (2026-08-22 — feature `fiabilisation-tests-plateforme`) :**

- Étanchéité `search_path` par tenant : `TenantSchemaIsolationIT` (Testcontainers PostgreSQL réel,
  2 schémas). Écrit/compilé, non exécuté ici (Docker absent) — CI seulement.
- Dimensionnement des verrous Flowable sous charge : `FlowableLockDimensioningTest` — H2 + threads
  réels (le verrouillage Flowable est un mécanisme SQL portable, pas Postgres-spécifique), exercice
  direct de `AcquireJobsCmd` et `ResetExpiredJobsCmd`. Exécuté et vert.
- Migration Liquibase en échec partiel : `LiquibasePartialFailureIT` — portée mono-schéma (pas
  multi-schéma : l'architecture réelle est « un schéma par instance », voir
  `02-multi-tenant.md`), H2, reprise idempotente via `DATABASECHANGELOG`. Exécuté et vert.
- ArchUnit : règle `..usecase..*Impl` → doit implémenter une interface de `..port.in..` ajoutée à
  `ArchitectureRulesTest`. Exécutée et verte.
- Relais outbox (rejeu, idempotence, crash) : couverture déjà substantielle avant cette feature
  (`OutboxRelaySchedulerTest`, `OutboxRelayIT`) — pas un trou réel, pas de nouveau test nécessaire.
- Résolution en cascade des templates : `ResolveDocumentTemplateUseCaseImplTest` (JPA réel, pas de
  mock — la cascade est implémentée dans la requête native `findBestActive`). Exécuté et vert.
- « Un seul workflow actif » sous parallélisme réel : `ConcurrentWorkflowReservationTest` — H2 +
  threads réels sur `AttachWorkflowUseCase.reserve`. A révélé et corrigé un bug réel : le perdant
  de la course recevait une `DataIntegrityViolationException` brute au lieu de
  `WorkflowAlreadyRunningException`, traitée par erreur comme un échec transitoire par
  `OutboxRelayScheduler` (retry) au lieu d'un dead-letter. Exécuté et vert.
- Seuils de couverture : Jacoco `BUNDLE` LINE 85% (était 80%), nouvelle règle BRANCH 75% (n'existait
  pas). `./mvnw verify` vert avec 1050 tests (0 échec), LINE 93.7%, BRANCH 75.0%.

Détails complets (décisions techniques, requirements coverage) :
[`docs/ai/design/2026-08-22-feature-fiabilisation-tests-plateforme.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-08-22-feature-fiabilisation-tests-plateforme.md).
