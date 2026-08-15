---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: done
created: 2026-08-15
updated: 2026-08-15
feature: patch-dossier-fields
phase7: 2026-08-15
phase9: 2026-08-15
---

# Implementation Guide

## Development Setup

Rien de spécifique — même stack que le reste du module `dossier` (Java 25,
Spring Boot 4.1, H2 mode PostgreSQL pour les tests). Worktree
`feature-patch-dossier-fields`, branche du même nom.

## Code Structure

Aucun nouveau package. Fichiers créés/modifiés :

- `domain/dossier/DossierFieldMerger.java` (nouveau) — `merge()` +
  `changedSensitiveFieldNames()`, statique, même patron que
  `DossierFieldRedactor`.
- `domain/dossier/model/Dossier.java` — nouvelle surcharge
  `withFields(Map, String, Instant)` ; l'existante `withFields(Map)` est
  inchangée (toujours utilisée par `DossierFieldRedactor`).
- `domain/dossier/port/in/UpdateDossierFieldsUseCase.java` (nouveau) —
  port + `UpdateDossierFieldsCommand` + `UpdateDossierFieldsResult`.
- `domain/dossier/usecase/UpdateDossierFieldsUseCaseImpl.java` (nouveau).
- `infrastructure/security/SecurityAuditLog.java` — nouvelle méthode
  `sensitiveFieldChanged(...)`, niveau INFO.
- `infrastructure/web/dossier/DossierApiDtos.java` — nouveau record
  `UpdateDossierFieldsRequest`.
- `infrastructure/web/dossier/DossierController.java` — nouvelle méthode
  `PATCH /{id}/fields`.
- `infrastructure/config/DossierConfig.java` — bean
  `UpdateDossierFieldsUseCase`.

## Implementation Notes

### Core Features

- **Fusion (D-MERGE) :** `DossierFieldMerger.merge(existing, patch)` —
  clé présente dans `patch` avec valeur non nulle → écrasée ; valeur
  `null` → clé retirée (D-CLEAR, et contrainte imposée par
  `Dossier`'s `Map.copyOf(fields)`, qui lève `NullPointerException` sur
  une entrée à valeur `null`) ; clé absente de `patch` → conservée.
- **Gate statut (D-STATUS-GATE) :** `UpdateDossierFieldsUseCaseImpl`
  rejette (409) si `dossier.status()` ∈ `{CLOTURE, ARCHIVE}`, avant même
  de charger le `DossierType` — évite un aller-retour de validation
  inutile sur un dossier de toute façon non modifiable.
- **Verrou optimiste :** `expectedVersion` optionnel, même sémantique que
  `PATCH .../status` (pas de vérification si absent).
- **Validation :** le jeu **fusionné** (pas juste le patch) est validé via
  `DossierFieldValidator.validate()` existant — un champ `required`
  effacé par un `null` redevient absent du jeu fusionné et échoue donc la
  validation `REQUIRED`, sans logique dédiée.
- **Audit `sensitive` (D-SENSITIVE-AUDIT) :** `DossierFieldMerger
  .changedSensitiveFieldNames()` est une fonction pure domaine — elle ne
  connaît pas `SecurityAuditLog`. Le contrôleur boucle sur
  `result.sensitiveFieldsChanged()` après le `save()` et appelle
  `SecurityAuditLog.sensitiveFieldChanged(...)` pour chacun. Aucune
  restriction d'accès supplémentaire : tout détenteur `EDIT` peut modifier
  un champ `sensitive`, l'événement est informatif (niveau INFO), jamais
  bloquant.
- **Assumption A2 confirmée par les tests :** l'édition des champs reste
  possible même si `DossierType.active = false` (contrairement à la
  création, qui rejette via `InactiveDossierTypeException`) —
  volontairement pas de vérification `type.active()` dans
  `UpdateDossierFieldsUseCaseImpl`.

### Patterns & Best Practices

- **Piège ArchUnit anticipé (Phase 2/3, avant tout code) :** le domaine
  ne dépend jamais de `infrastructure.*`. `UpdateDossierFieldsUseCaseImpl`
  renvoie `UpdateDossierFieldsResult(dossier, sensitiveFieldsChanged)` —
  c'est le contrôleur (infrastructure) qui décide de logger, pas le
  domaine. Vérifié par `ArchitectureRulesTest` (`domain_never_depends_on_infrastructure`),
  3/3 vert.
- Même patron que `UpdateDossierStatusUseCaseImpl` pour le chargement /
  ACL / verrou de version ; même patron que `CreateDossierUseCaseImpl`
  pour la validation de champs via `DossierFieldValidator`.

## Integration Points

- `PATCH /api/dossiers/{id}/fields`, body
  `{"fields": {...}, "expectedVersion": <optional>}` → `DossierResponse`
  complet (champs `sensitive` toujours en clair pour l'auteur de la
  requête — la redaction en lecture reste uniquement dans
  `DossierFieldRedactor`, appliqué côté `GetDossierUseCase`, pas ici).
- Pas de nouvelle table, pas de migration Liquibase, pas de `ServiceTask`
  Flowable, pas de composant frontend (hors scope V1, cf. design).

## Error Handling

| Cas | HTTP | Exception |
|---|---|---|
| `fields` absent ou vide | 400 | `IllegalArgumentException` |
| Dossier introuvable / autre tenant | 404 | `DossierNotFoundException` |
| Pas de permission `EDIT` | 403 | `AccessDeniedException` |
| Statut `CLOTURE`/`ARCHIVE` | 409 | `DossierConflictException` |
| `expectedVersion` ne correspond pas | 409 | `DossierConflictException` |
| Validation de champ (required/type/option/unknown) | 400 | `DossierFieldValidationException` |

Toutes ces exceptions sont déjà mappées par le `DossierExceptionHandler`
existant — aucune extension du handler n'a été nécessaire.

## Performance Considerations

Aucune préoccupation spécifique — un `findById` + un `findByCode` + un
`save()`, comme `UpdateDossierStatusUseCaseImpl`. Pas de N+1, pas de
requête additionnelle liée à l'audit (le logging se fait en mémoire sur
le résultat déjà chargé).

## Security Notes

- `SecurityAuditLog.sensitiveFieldChanged` journalise uniquement
  `dossierId`, le nom du champ et l'auteur — jamais l'ancienne ni la
  nouvelle valeur (vérifié par test : `doesNotContain` sur les deux
  valeurs SSN dans `DossierApiTest.updateFields_sensitiveFieldChanged_emitsSecurityAuditEvent`).
  Niveau INFO, distinct des refus (`accessDenied`/`tenantMismatch`,
  niveau WARN) — ce n'est pas un événement de sécurité négatif.
- La redaction en lecture (`DossierFieldRedactor`) n'est pas affectée :
  un `PATCH .../fields` qui modifie un champ `sensitive` reste redacté au
  `GET` suivant pour un non-admin (test de non-régression dédié).

## Deviations from Design

Aucun écart matériel — l'implémentation suit le design de Phase 3 fichier
par fichier. Un seul micro-écart, sans conséquence fonctionnelle : le
pseudo-code du design charge `DossierType` **après** l'appel à
`DossierFieldMerger.merge()` ; l'implémentation charge le type **avant**
le merge (`UpdateDossierFieldsUseCaseImpl` lignes 66–71). `merge()` ne
dépend jamais du type, donc les deux ordres produisent exactement le même
comportement observable (mêmes codes d'erreur, même 404 si le type est
introuvable) — pure réorganisation locale, pas un écart de design à
tracer plus loin.

Le seul risque documenté en planning (faux-positif de type numérique dans
`changedSensitiveFieldNames`, ex. `Integer` JSON vs `Long` stocké) reste
accepté en V1, non observé dans les tests (Jackson désérialise les
nombres JSON en `Integer`/`Long`/`Double` de façon cohérente avec ce qui
est stocké par le même chemin de désérialisation).

## Phase 7 — Check Implementation (revue fichier par fichier)

**Méthode :** chaque fichier livré (§ Code Structure) relu contre le
design (composants clés, pseudo-code, Component Breakdown) et contre les
requirements (décisions D-*, success criteria, edge cases, assumptions
A1–A3).

| Fichier | Intention design | Statut |
|---|---|---|
| `DossierFieldMerger.java` | `merge`/`changedSensitiveFieldNames`, statique, package `domain.dossier`, patron `DossierFieldRedactor` | Conforme — signatures, emplacement, Javadoc expliquant la contrainte `Map.copyOf` identiques au design |
| `Dossier.java` | nouvelle surcharge `withFields(Map, String, Instant)`, surcharge `withFields(Map)` existante inchangée | Conforme — non-régression testée explicitement (`DossierTest.withFields_singleArg_...`, `DossierFieldRedactorTest` 5/5) |
| `UpdateDossierFieldsUseCase.java` | port + `UpdateDossierFieldsCommand`/`UpdateDossierFieldsResult` | Conforme — signatures identiques au Component Breakdown |
| `UpdateDossierFieldsUseCaseImpl.java` | ordre empty→404→403→409 statut→409 version→merge→validate→save ; renvoie `UpdateDossierFieldsResult` ; aucune dépendance infrastructure | Conforme (micro-écart d'ordre non fonctionnel documenté ci-dessus) — ArchUnit `domain_never_depends_on_infrastructure` vert |
| `SecurityAuditLog.java` | `sensitiveFieldChanged(...)`, niveau INFO, jamais la valeur | Conforme — testé unitairement (`SecurityAuditLogTest`) et bout en bout (`DossierApiTest`, assertion `doesNotContain` sur les deux valeurs SSN) |
| `DossierApiDtos.java` | `UpdateDossierFieldsRequest(Map<String,Object> fields, Long expectedVersion)` | Conforme |
| `DossierController.java` | `@PatchMapping("/{id}/fields")`, boucle post-save sur `sensitiveFieldsChanged` → `SecurityAuditLog` | Conforme — vérifié par capture logger (émis/absent selon le contenu du patch) |
| `DossierConfig.java` | bean `UpdateDossierFieldsUseCase` | Conforme — contexte Spring boot vérifié (`DossierApiTest` 53/53, `@SpringBootTest`) |

**Trous de logique / cas limites :** aucun trouvé qui ne soit déjà couvert
par un test. Point vérifié spécifiquement : l'edge case requirements
« dossier `CLOTURE`/`ARCHIVE` bloqué même avec un `expectedVersion`
correct » — confirmé par relecture de code (gate de statut avant le
contrôle de version) plutôt que par un test dédié isolant les deux causes
(les tests existants couvrent chaque 409 séparément, pas la combinaison
exacte "les deux conditions seraient satisfaites/insatisfaites
simultanément") ; jugé suffisant, la lecture du code lève toute ambiguïté
sur l'ordre.

**Sécurité :** ACL réutilisée sans modification (`assertCanEdit`) ; pas de
nouvelle surface d'attaque ; isolation tenant via `dossier.tenantId()`
chargé depuis le repository (jamais depuis l'entrée cliente) ; le nouvel
événement d'audit ne journalise jamais de valeur, testé explicitement en
négatif (`doesNotContain`).

**Docs/tests manquants :** aucun. Testing doc § entièrement coché ;
`DEFERRED-FEATURES.md`/`TODO.md` mis à jour (T10) ; requirements ·
Success Criteria coché.

## Alignment Summary

**Statut :** aligné, aucune déviation majeure. Prêt pour Phase 8
(`dev-testing`) et Phase 9 (`dev-review`).

## Phase 9 — Revue de code finale (holistique)

**Méthode :** relecture fraîche de chaque fichier de production (pas
seulement des diffs), traçage des appelants pour les signatures changées
(`Dossier.withFields`, `DossierController` constructeur), comparaison
avec `UpdateDossierStatusUseCaseImpl`/`CreateDossierUseCaseImpl` pour la
cohérence de patron, vérification `pom.xml` (aucune nouvelle dépendance
Maven ajoutée), vérification `git diff --stat origin/main...HEAD` (22
fichiers, aucun fichier frontend touché).

**Trouvé et corrigé (mineur, non-bloquant) :** le Javadoc de classe de
`SecurityAuditLog` décrivait encore la classe comme dédiée uniquement aux
refus WARN — devenu inexact depuis l'ajout de `sensitiveFieldChanged`
(INFO). Corrigé pour refléter les deux niveaux.

**Observations non-bloquantes (dette préexistante, pas introduite par
cette feature) :**
- `DossierController` a maintenant 10 dépendances injectées au
  constructeur — accentue une tendance déjà présente (9 avant), pas causé
  par cette PR. Un futur refactor (façade sur les use cases dossier)
  pourrait l'adresser, hors scope ici.
- `updateFields` transmet `request.fields()` sans `Map.copyOf` défensif,
  contrairement à `create()`. Sans conséquence pratique (Jackson produit
  une `Map` neuve par requête, jamais partagée entre deux appels), mais
  une incohérence de style mineure.
- `allowUnknownFields = true` accepte des valeurs JSON de forme
  arbitraire sur les champs inconnus (pas de validation de forme) — même
  comportement hérité de `DossierFieldValidator` déjà présent à la
  création, non aggravé par cette feature.

**Point positif vérifié :** la fiabilité contre les écritures
concurrentes est couverte à deux niveaux indépendants — le verrou
applicatif optionnel (`expectedVersion`) **et** le `@Version` JPA
physique sur `DossierJpaEntity`, qui lève
`ObjectOptimisticLockingFailureException` (déjà mappée 409 par
`DossierExceptionHandler`) même quand le client n'envoie pas
`expectedVersion`. Une vraie collision concurrente reste donc détectée
dans tous les cas.

**Checklist finale :**
- [x] Correspondance avec le design — vérifiée fichier par fichier
      (Phase 7), reconfirmée ici sans nouvel écart.
- [x] Aucun trou de logique bloquant.
- [x] Sécurité traitée (ACL inchangée, isolation tenant, audit sans
      fuite de valeur, verrou concurrent double niveau).
- [x] Points d'intégration vérifiés (`DossierConfig` boot Spring,
      `DossierController` routes existantes non affectées).
- [x] Tests couvrent les changements (695/695 verts, Phase 8 a comblé les
      trous restants).
- [x] Docs à jour (les 7 docs `ai-devkit` + `DEFERRED-FEATURES.md` +
      `TODO.md`).

**Verdict :** prête à pousser. Aucune PR créée — en attente d'une
demande explicite de l'utilisateur.
