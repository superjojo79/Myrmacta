---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
---

# Project Planning & Task Breakdown

## Milestones
**What are the major checkpoints?**

- [x] M1 : Validation blank en domaine — 4 records (D-BLANK-VALIDATION-SCOPE)
- [x] M2 : Callback MDC par item de purge — port + use case + job (D-MDC-DOSSIER-MECHANISM, dépend M1
      seulement pour l'ordre de commit, aucune dépendance technique réelle)
- [x] M3 : Nettoyage documentaire `DEFERRED-FEATURES.md` (dépend M1–M2 livrés, D-PERSISTABLE-CONFIRM
      déjà fermé par preuve documentée en Phase 3 — pas de tâche de code)
- [x] M4 : Vérification globale + clôture (Phases 7–9)

## Task Breakdown
**What specific work needs to be done?**

### M1 — Validation blank en domaine (D-BLANK-VALIDATION-SCOPE)

- [x] T1.1 : `WorkflowTypeConfig` — compact constructor : remplace/complète
      `Objects.requireNonNull(workflowKey, "workflowKey")` par une garde blank
      (`IllegalArgumentException` si `workflowKey.isBlank()`), sur le modèle
      `DossierType.code`/`CODE_PATTERN` (ici une simple garde `isBlank()`, pas de pattern de
      caractères — `workflowKey` n'a pas la même contrainte d'usage en URL/payload que `code`).
      Dépendance : aucune. Validation : T-BLANK-1, T-BLANK-5 (non-régression `null`).
- [x] T1.2 : `TriggerOutboxPayload` — même garde sur `processKey`. Dépendance : aucune (parallélisable
      avec T1.1). Validation : T-BLANK-2, T-BLANK-5.
- [x] T1.3 : `WorkflowStartedEvent` — même garde sur `processKey`. Dépendance : aucune. Validation :
      T-BLANK-3, T-BLANK-5.
- [x] T1.4 : `DossierType` — même garde sur `workflowKey` (s'ajoute à côté de la garde `code`
      existante, dans le même compact constructor). Dépendance : aucune. Validation : T-BLANK-4,
      T-BLANK-5.

### M2 — Callback MDC par item de purge (D-MDC-DOSSIER-MECHANISM)

- [x] T2.1 : `ProcessPurgeOutboxUseCase` (port `in`) — signature étendue :
      `int processBatch(Instant now, int limit, Function<UUID, Runnable> onItemStart)`. Dépendance :
      aucune. Validation : compilation (changement de contrat).
- [x] T2.2 : `ProcessPurgeOutboxUseCaseImpl.processBatch` — dans la boucle, juste avant
      `purgeDossier(record)` : `Runnable closeScope = onItemStart.apply(record.dossierId())` ; ajoute
      un `finally { closeScope.run(); }` englobant le `try`/`catch (RuntimeException ex)` existant
      (le corps du `try`/`catch` ne change pas). Dépendance : T2.1. Validation : T-MDC-1, T-MDC-2,
      T-MDC-3, T-MDC-5.
- [x] T2.3 : `PurgeOutboxJob.process()` — fournit le callback à l'appel de `processBatch` :
      `dossierId -> { MdcScope scope = MdcScope.dossier(dossierId); return scope::close; }` (lambda
      inline ou méthode privée si plus lisible). `recoverStaleProcessing` n'est pas concerné (pas
      d'item unitaire à corréler, reste sous `MdcScope.tenant` seul). Dépendance : T2.1, T2.2.
      Validation : T-JOB-1, T-JOB-2, T-JOB-3.
- [x] T2.4 (régression signature, 3 fichiers) : mise à jour des appels/mocks à 2 arguments trouvés en
      Phase 3 — `ProcessPurgeOutboxUseCaseImplTest` (appels directs), `PurgeOutboxJobTest` (mock du
      port), `PurgeOutboxApiTest` ligne ~142 (appel direct dans un test d'API HTTP). Dépendance :
      T2.1. Validation : T-MDC-5, T-JOB-3, T-API-1.
- [x] T2.5 : test d'intégration multi-items (2+ dossiers dans le même batch) prouvant l'absence de
      fuite de `dossierId` entre items. Dépendance : T2.2, T2.3. Validation : T-INT-1.

### M3 — Nettoyage documentaire `DEFERRED-FEATURES.md`

- [x] T3.1 : Retire de `DEFERRED-FEATURES.md` §1 les 2 lignes déjà closes : CHECK SQL
      `dossier_type_acl.permission` (migration `014-dossier-type-acl-permission-check.yaml`, PR #72)
      et validation blank `tenantId` (`TenantProperties.id` `@NotBlank`). Dépendance : aucune
      (peut être fait à tout moment). Validation : revue manuelle (diff du fichier).
- [x] T3.2 : Retire (ou marque livrée avec référence à cette feature) la ligne validation blank
      `processKey`/`workflowKey` (§1.1) une fois M1 livré. Dépendance : M1 (T1.1–T1.4). Validation :
      revue manuelle.
- [x] T3.3 : Retire (ou marque livrée) la ligne `dossierId` en MDC (§1.8) une fois M2 livré.
      Dépendance : M2 (T2.1–T2.5). Validation : revue manuelle.
- [x] T3.4 : Retire la ligne `Persistable`/`isNew()` (§1.4), avec une note courte référençant la
      preuve documentée en Phase 3 (design doc, §Design Decisions) plutôt que de la répéter en
      entier. Dépendance : aucune (déjà tranché en Phase 3, tâche purement documentaire).
      Validation : revue manuelle.

### M4 — Vérification globale + clôture (Phases 7–9)

- [x] T4.1 : Phase 7 (check implementation) — relecture des 4 records + `ProcessPurgeOutboxUseCase`/
      `ProcessPurgeOutboxUseCaseImpl`/`PurgeOutboxJob` contre le design ; chercher les écarts réels
      (pattern historique du dépôt : la plupart des features précédentes trouvent 1-2 écarts réels à
      cette étape, cf. `connecteur-http-rest-service`/T5.5). Dépendance : M1–M3.
- [x] T4.2 : Phase 8 (couverture) — balayage Jacoco classe par classe sur les fichiers touchés,
      combler les lacunes réelles trouvées (au-delà du bundle global). Dépendance : T4.1. Validation :
      seuils bundle maintenus (LINE ≥ 85% / BRANCH ≥ 75%).
- [x] T4.3 : régression frontend complète (`npm run test`, `npm run test:e2e`) — preuve de non-impact
      (feature confirmée backend seul), aucun fichier `frontend/**` modifié attendu. Dépendance :
      T4.1.
- [x] T4.4 : Phase 9 (revue finale) — alignement design confirmé, `CHANGELOG.md` mis à jour
      (convention `flowforge-conventions` §7), `analysis doc/19-...md`/`00-index.md` marqués livrés,
      `DEFERRED-FEATURES.md` cohérent avec le code (T3.1–T3.4 vérifiées), PR prête. Dépendance :
      T4.1–T4.3.

## Dependencies
**What needs to happen in what order?**

```
T1.1 ─┐
T1.2 ─┼→ (M1 livré) ─┐
T1.3 ─┤              │
T1.4 ─┘              ├→ T3.2
                      │
T2.1 → T2.2 → T2.3 ──┼→ T2.5 ──┬→ T3.3
              T2.4 ───┘         │
                                 │
T3.1 ── (indépendant, à tout moment)
T3.4 ── (indépendant, déjà tranché en Phase 3)
                                 │
                    T3.1–T3.4 ──┴→ T4.1 → T4.2 / T4.3 → T4.4
```

- M1 et M2 sont indépendants entre eux techniquement (aucun fichier partagé) — parallélisables sans
  risque de conflit de merge, contrairement à M3/M4 du cycle précédent (`connecteur-http-rest-service`)
  qui partageaient une même méthode.
- T3.1 et T3.4 (nettoyage doc) ne dépendent d'aucun code — peuvent être faits en tout premier ou en
  dernier sans impact, regroupés ici en fin de séquence par cohérence de revue (un seul diff
  `DEFERRED-FEATURES.md` à relire).
- Aucune dépendance externe (pas de nouvelle librairie, `java.util.function.Function`/`Runnable`
  déjà JDK standard). Aucune migration Liquibase, aucun changement de schéma de données.

## Timeline & Estimates
**When will things be done?**

- Feature de taille réduite (2 lots de code indépendants + nettoyage doc, pas de nouvel endpoint ni
  écran) — pas d'estimation en jours/points, découpage en 4 milestones/14 tâches TDD suffisant pour
  le suivi. M1/M2 parallélisables (le cœur fonctionnel) ; M3 le nettoyage doc ; M4 la clôture
  standard du projet.

## Risks & Mitigation
**What could go wrong?**

- **Risque de régression sur les 3 tests existants qui appellent `processBatch` en 2 arguments**
  (`ProcessPurgeOutboxUseCaseImplTest`, `PurgeOutboxJobTest`, `PurgeOutboxApiTest`) — identifiés
  exhaustivement en Phase 3 (T2.4), pas une découverte à faire en implémentation. Mitigation :
  traiter T2.4 dans le même commit que T2.1–T2.3, ne jamais laisser le port changé sans ses 3
  appelants de test mis à jour.
- **Risque de portée du callback trop étroite** — si implémenté seulement autour du `catch` plutôt
  que de tout `purgeDossier(record)` (erreur d'implémentation possible malgré la décision explicite
  en Phase 3), le `log.warn` réel de `FlowableWorkflowEngineAdapter` resterait sans `dossierId` en
  MDC, rendant la feature silencieusement sans effet réel. Mitigation : T-INT-1 doit spécifiquement
  exercer un item dont `purgeDossier` déclenche un appel à `workflowEnginePort` (pas seulement un
  item qui réussit trivialement sans lien workflow), pour prouver que le MDC est bien actif à ce
  moment précis — pas seulement autour du bloc `catch`.
- **Faible valeur produit assumée** (cf. requirements, Constraints & Assumptions) — risque de
  sur-investir en tests/documentation pour un gain diagnostique modeste ; mitigation : périmètre
  volontairement limité (pas de test manuel, pas de nouveau dashboard/alerte), cohérent avec le
  Non-goal explicite des requirements.

## Resources Needed
**What do we need to succeed?**

- Aucune ressource externe — types déjà sur le classpath JDK (`java.util.function.Function`,
  `java.lang.Runnable`), `MdcScope`/`ArchitectureRulesTest` déjà en place, aucun nouveau service
  tiers ni infrastructure supplémentaire.
- Référence de comportement pour T2.3 : `MdcScope.dossier(UUID)` existe déjà comme méthode sur
  `MdcScope` (`infrastructure/observability/MdcScope.java`) mais n'est **utilisée nulle part
  aujourd'hui** (`grep MdcScope.dossier(` : aucun résultat) — T2.3 en sera le premier appelant réel,
  pas une réutilisation d'un patron existant. `MdcScope.tenant(String)` (utilisé dans
  `PurgeOutboxJob.process()` actuel) reste la seule référence d'usage concrète du type dans le
  dépôt.

## Statut (Phase 6, 2026-09-01)

M1, M2 et M3 sont livrés — 14/14 tâches faites, `./mvnw verify` vert (1430 tests, ArchUnit compris,
seuils Jacoco maintenus). Aucun risque identifié en planning ne s'est matérialisé : le callback
enveloppe bien tout `purgeDossier(record)` (prouvé par le test d'intégration multi-items T2.5/T-INT-1
avec le vrai `ProcessPurgeOutboxUseCaseImpl`, pas un mock), et les 3 fichiers de test à signature
2 arguments ont tous été mis à jour dans le même geste que T2.1–T2.3 (aucune régression). Aucune
tâche découverte hors plan, aucun blocage. Seul écart mineur : le séquencement TDD strict n'a pas
été respecté sur le tout premier geste du changement de signature de port (comportement complet
écrit avant test, corrigé en revenant à une base compilable puis en repilotant le vrai comportement
par test rouge→vert) — documenté dans le doc implementation plutôt que masqué.

Reste M4 (Phases 7–9 : check implementation, couverture Jacoco classe par classe, régression
frontend, revue finale + PR) avant clôture complète.
