---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
---

# Project Planning & Task Breakdown

## Milestones
**What are the major checkpoints?**

- [x] M1 : Domaine — unicité de `name` dans `fieldDefinitions` (D-NAME-UNIQUE)
- [x] M2 : Domaine/infra — blocage 409 sur `PATCH` (D-UI-SCHEMA-COMPAT, D-DESIGN-CHECK-PLACEMENT)
- [x] M3 : MCP — simplification `DefineDossierTypeTool` (régression, dépend de M2)
- [x] M4 : API — tests d'intégration bout en bout backend (dépend M1–M3)
- [x] M5 : Frontend — composant éditeur de schéma + utilitaire de validation
- [x] M6 : Frontend — intégration dans `DossierTypesAdminView.vue` (dépend M5)
- [x] M7 : E2E Playwright + doc utilisateur (dépend M1–M6)
- [x] M8 : Vérification globale + clôture (Phases 7–9)

## Task Breakdown
**What specific work needs to be done?**

### M1 — Domaine : unicité de `name`

- [x] T1.1 : `DossierType` — ajoute `requireUniqueFieldNames` au compact constructor, appelé après
  la normalisation de `fieldDefinitions`. Dépendance : aucune. Validation : `DossierTypeTest`
  couvre T-DOM-1 à T-DOM-4. Rouge d'abord (test dupliqué qui échoue faute de garde), puis code.

### M2 — Blocage 409 sur `PATCH` (D-UI-SCHEMA-COMPAT, D-DESIGN-CHECK-PLACEMENT)

- [x] T2.1 : `UpdateDossierTypeUseCaseImpl` — ajoute le paramètre constructeur
  `CheckDossierTypeSchemaCompatibilityUseCase`, invoque `check(...)` avant `repository.save`, lève
  `DossierTypeSchemaIncompatibleException` si incompatible. Dépendance : aucune (le use case cible
  et le check existent déjà). Validation : nouveaux tests T-USE-1 à T-USE-4 dans
  `UpdateDossierTypeUseCaseImplTest` ; les 7 tests existants adaptés au nouveau constructeur
  (T-USE-5 — stub par défaut compatible dans `setUp()`).
- [x] T2.2 : `DossierConfig` — bean `updateDossierTypeUseCase` reçoit
  `CheckDossierTypeSchemaCompatibilityUseCase` en paramètre. Dépendance : T2.1. Validation :
  démarrage du contexte Spring (T-CFG-1, couvert par les IT existantes qui chargent le contexte).
- [x] T2.3 : `DossierExceptionHandler` — nouveau `@ExceptionHandler(DossierTypeSchemaIncompatibleException.class)`
  → 409, `detail` avec nombre + exemples, propriétés `incompatibleCount`/`sampleReferences`.
  Dépendance : aucune (peut être fait en parallèle de T2.1). Validation : T-EXC-1 à T-EXC-3.

### M3 — Simplification MCP (dépend de M2)

- [x] T3.1 : `DefineDossierTypeTool.defineUpdate()` — retire l'appel explicite à
  `checkCompatibilityUseCase.check()` sur le chemin non-`dryRun` (l'exception remonte désormais
  depuis `updateDossierTypeUseCase.update()`) ; conserve l'appel explicite uniquement pour le
  chemin `dryRun`. Dépendance : T2.1 (le use case doit déjà bloquer). Validation : réécrit
  `update_incompatibleSchema_isRejectedWithoutSaving` (stub `updateDossierTypeUseCase.update()`
  au lieu de `checkCompatibilityUseCase`) et `update_preservesExistingRetentionAndSla` (retire le
  stub désormais inutile sur le chemin réel) — T-MCP-1 à T-MCP-5. **Risque de régression identifié
  en Phase 3** (design § Non-Functional Requirements) : à traiter en premier dans ce milestone,
  avant tout autre test MCP, pour ne pas laisser la suite MCP rouge entre deux commits.

### M4 — Tests d'intégration API (dépend de M1–M3)

- [x] T4.1 : `DossierApiTest` (ou fichier équivalent identifié en implémentation) — nouveaux cas
  T-API-1 à T-API-6 : 200/409/400 sur `PATCH`, 400 sur `POST` dupliqué, round-trip
  `allowUnknownFields`, 403 sans rôle. Dépendance : M1, M2, M3 mergés dans la même branche.

### M5 — Frontend : composant éditeur + validation (indépendant de M1–M4, contrat déjà figé par le design)

- [x] T5.1 : `frontend/src/dossier/validateFieldDefinitions.ts` — utilitaire pur (pas de composant),
  écrit **avant** le composant pour pouvoir être testé isolément et réutilisé par le composant et
  la vue. Validation : T-FE-VAL-1 à T-FE-VAL-5.
- [x] T5.2 : `frontend/src/components/dossier/FieldDefinitionDefsEditor.vue` — éditeur de liste
  (ajout/suppression/réordonnancement, sous-formulaire par ligne, `v-model` liste complète),
  utilise `validateFieldDefinitions` pour l'affichage d'erreurs inline. Dépendance : T5.1.
  Validation : T-FE-1 à T-FE-7.

### M6 — Intégration `DossierTypesAdminView.vue` (dépend de M5)

- [x] T6.1 : formulaire de création — ajoute la case `allowUnknownFields` +
  `<FieldDefinitionDefsEditor v-model="form.fieldDefinitions" />`, remplace le
  `fieldDefinitions: []` en dur, bloque la soumission si `validateFieldDefinitions` échoue.
  Validation : T-FE-VIEW-1, T-FE-VIEW-2.
- [x] T6.2 : table des types — action « Modifier les champs » par ligne, révèle un panneau
  d'édition (même composant, pré-rempli directement depuis l'objet `DossierType` déjà chargé par
  la liste paginée — **écart par rapport au plan initial** : pas d'appel `getDossierType(code)`
  dédié, la réponse de liste porte déjà `fieldDefinitions`/`allowUnknownFields` complets, vérifié
  en implémentation, cf. doc implementation), boutons Enregistrer/Annuler, gestion de l'erreur 409
  via `toProblemDetail(err).detail` (pattern déjà utilisé ailleurs dans ce fichier). Dépendance :
  T6.1 (même formulaire de champs réutilisé). Validation : T-FE-VIEW-3 à T-FE-VIEW-6.

### M7 — E2E Playwright + documentation utilisateur (dépend de M1–M6)

- [x] T7.1 : scénarios `E2E-SCHEMA-1` à `E2E-SCHEMA-5` dans `frontend/e2e/**` (fixtures seedées via
  API pour le cas « dossier existant incompatible »). Dépendance : backend + frontend complets.
- [x] T7.2 : `GUIDE-UTILISATEUR.md` §1 — retire le paragraphe « Limite actuelle de l'écran »,
  documente le nouvel éditeur. Convention `flowforge-conventions` § « Workflow de feature » point 6
  (écran admin livré → doc à jour avant clôture). Dépendance : T6.2 livré (comportement réel à
  décrire).

### M8 — Vérification globale et clôture

- [x] T8.1 : `./mvnw verify -Dmaven.compiler.release=21` vert (suite complète + ArchUnit + Jacoco) ;
  confirmation qu'aucune des 15 classes de test existantes utilisant `FieldDefinition` n'introduit
  de doublon de `name` bloqué par T1.1 (risque signalé en design, non vérifié exhaustivement avant
  code).
- [x] T8.2 : `cd frontend && npm run test` + `npm run test:e2e` verts, `typecheck`/`lint` propres.
- [ ] T8.3 : `CHANGELOG.md` — entrée `[Non publié]` synthétique une fois la PR ouverte (convention
  `flowforge-conventions` point 7). **Différé** — aucune PR demandée/ouverte à ce stade (Phase 5
  seule demandée), pas de numéro de PR à référencer ; à faire au moment de l'ouverture (`dev-pr`).
- [x] T8.4 : TODO §47 clos, `FILE-ATTENTE.md` déplace `schema-champs-dynamiques` vers *Récemment
  livré* avec la synthèse finale.

## Dependencies
**What needs to happen in what order?**

```
M1 ─┐
    ├─> M4 ─┐
M2 ─┤       │
    │       ├─> M7 ─> M8
M3 ─┘ (dép. M2)
                │
M5 ─> M6 ────────┘
```

- M1, M2, M5 sont indépendants entre eux et peuvent être menés en parallèle (backend domaine, et
  frontend composant — contrat `FieldDefinition`/`DossierType` déjà figé par le design, aucune
  attente réciproque).
- M3 dépend strictement de M2 (le comportement qu'il vérifie n'existe qu'une fois M2 livré).
- M4 dépend de M1+M2+M3 réunis (teste le comportement HTTP bout en bout).
- M6 dépend de M5 (réutilise le composant).
- M7 dépend de tout le reste (E2E = bout en bout réel, doc décrit le comportement livré).
- Aucune dépendance externe (API/service tiers) ; aucune dépendance d'équipe au-delà de cette
  session.

## Timeline & Estimates
**When will things be done?**

Pas d'estimation en jours/heures (convention de ce dépôt : lifecycle piloté par phase et par
preuve de vérification, pas par échéancier calendaire — voir `FILE-ATTENTE.md` historique des
features précédentes). Séquencement ci-dessus = ordre d'exécution recommandé pour la Phase 5.

## Risks & Mitigation
**What could go wrong?**

- **Régression `DefineDossierTypeToolTest`** (M3) — le refactor change *quel* mock lève
  l'exception dans 2 tests existants. Mitigation : traité en premier dans M3, avant tout ajout de
  nouveau test, pour isoler la cause si la suite MCP casse.
- **Fixture de test avec `name` dupliqué non détectée en design** (T1.1/T8.1) — 15 classes de test
  construisent des `FieldDefinition`, aucune vérifiée exhaustivement en Phase 3. Mitigation :
  première tâche du milestone M1, `./mvnw verify` immédiatement après pour surfacer tout cas existant ;
  correction ciblée si trouvé (pas un blocage de conception, juste un ajustement de fixture).
- **Volumétrie du check de compatibilité** (pas de pagination, signalé en design) — pas un risque
  pour cette feature (comportement déjà en prod via MCP), mais à garder en tête si un futur tenant
  atteint un volume de dossiers/type élevé. Hors scope V1, aucune action ici.
- **Double emploi visuel avec `DynamicFields.vue`** — le nouveau composant édite des définitions,
  pas des valeurs ; risque de confusion nominale seulement (mitigé par le nom
  `FieldDefinitionDefsEditor.vue`, distinct et explicite).

## Resources Needed
**What do we need to succeed?**

- Aucune ressource externe. Stack déjà en place (Java 25/Spring Boot 4.1, Vue 3/TS/Vite, Vitest,
  Playwright). Commandes de vérification : `./mvnw verify -Dmaven.compiler.release=21`,
  `cd frontend && npm run test`/`test:e2e`.
