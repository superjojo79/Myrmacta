---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: done
created: 2026-08-24
updated: 2026-08-24
feature: masquage-donnees-sensibles-logs-webhooks
design_ref: docs/ai/design/2026-08-24-feature-masquage-donnees-sensibles-logs-webhooks.md
planning_ref: docs/ai/planning/2026-08-24-feature-masquage-donnees-sensibles-logs-webhooks.md
---

# Implementation Guide — `masquage-donnees-sensibles-logs-webhooks`

10 tâches planifiées (T1.1–T3.3) exécutées sur 3 milestones. `./mvnw verify`
final : **1088 tests, 0 échec**, seuils Jacoco tenus. Statut : **terminée et
revue** (Phase 7 ci-dessous).

## Development Setup

Aucun — pas de dépendance nouvelle, pas de changelog Liquibase, pas de
configuration à ajouter.

## Code Structure

- `domain/dossier/model/Dossier.java` — `toString()` sûr ajouté (D-LOG-GUARD)
- `domain/dossier/model/FieldDefinition.java` — `toString()` sûr ajouté
- `domain/notification/model/DossierFieldsSnapshot.java` (nouveau) —
  contexte optionnel `(dossierTypeCode, fields)`
- `domain/notification/model/NotificationEvent.java` — composant `dossierFieldsSnapshot`
  ajouté (constructeur canonique 8-arg + constructeur de convenance 7-arg
  délégant à `null`)
- `domain/notification/usecase/SendNotificationUseCaseImpl.java` — `buildPayload()`
  masque avant tout `snapshot()`/écriture outbox ; nouvelle dépendance
  `DossierTypeRepository`
- `infrastructure/config/NotificationConfig.java` — wiring Spring mis à jour
- `test/.../ArchitectureRulesTest.java` — 2 nouvelles règles ArchUnit
  (liste blanche `Dossier.fields()`/`FieldDefinition.defaultValue()`)
- `test/.../ArchitectureRulesFixtureTest.java` (nouveau) — preuve que la
  règle échoue vraiment sur une violation
- `test/.../NotificationSensitiveFieldMaskingEndToEndTest.java` (nouveau) —
  bout en bout réel jusqu'au corps HTTP

## Implementation Notes

### Core Features

- **`toString()` sûrs (D-LOG-GUARD)** — `Dossier`/`FieldDefinition`
  n'impriment plus jamais `fields`/`defaultValue`, inconditionnellement
  (pas de filtrage sélectif — ces types ne portent pas l'information
  nécessaire pour distinguer sensible/non-sensible au moment du
  `toString()`).
- **Liste blanche ArchUnit (D-LOG-GUARD, mécanisme corrigé)** — la Phase 3
  envisageait une détection de logging, vérifiée irréalisable avec
  ArchUnit (pas d'analyse de flux de valeurs — la signature bytecode d'un
  appel `Logger.info(String, Object)` est identique quel que soit le type
  réel de l'argument). Remplacée par une liste blanche des classes
  autorisées à appeler `Dossier.fields()` (5 classes) et
  `FieldDefinition.defaultValue()` (1 classe), déterminée en faisant
  tourner la règle contre le code réel — pas devinée par `grep` (un
  premier essai `grep '\.fields()'` remontait des faux positifs comme
  `command.fields()`/`request.fields()`, sans rapport avec `Dossier`).
- **Masquage webhook automatique (D-WEBHOOK-MASK)** — `SendNotificationUseCaseImpl.buildPayload()`
  est le point de passage unique avant toute écriture outbox, pour les 3
  canaux (email/webhook/browser). Si `dossierFieldsSnapshot` est présent,
  résout les `FieldDefinition` réelles via `DossierTypeRepository.findByCode()`
  et retire les valeurs `sensitive` — automatique, ne dépend pas de la
  discipline du producteur. Coût nul sur le trafic réel actuel (aucun
  producteur ne peuple `dossierFieldsSnapshot` aujourd'hui — zéro appel
  au repository dans ce cas, vérifié par test).

### Patterns & Best Practices

- **Extension de record sans casser les appelants** — `NotificationEvent`
  gagne un composant via un constructeur canonique 8-arg, plus un
  constructeur de convenance 7-arg qui délègue avec `null` — les 3
  producteurs en repo et ~13 sites de test compilent sans modification.
- **Fail-safe par omission plutôt que par exception** — `dossierTypeCode`
  introuvable ou valeurs sensibles non résolvables → la clé `dossierFields`
  est simplement absente du payload final, jamais les valeurs brutes non
  masquées, jamais d'exception qui bloquerait tout l'envoi.
- **Contrôle mutant manuel** — pour T2.4, le masquage a été temporairement
  neutralisé dans le code de production, confirmant que 4 tests
  échouaient réellement (pas un succès à vide), puis restauré et
  re-vérifié. Même discipline appliquée à T1.3 via `ArchitectureRulesFixtureTest`.

## Design Deviations

1. **T1.3 — mécanisme ArchUnit corrigé** (détaillé ci-dessus et dans le
   design doc) : détection de logging → liste blanche d'accès. Trouvé et
   corrigé **avant** d'écrire du code inutile, via `AskUserQuestion`, pas
   après coup.
2. **Limitation assumée, non corrigée** : le masquage webhook écrit sous
   une clé réservée `"dossierFields"` dans le payload. Si un futur
   producteur définissait à la fois `payload.get("dossierFields")` (donnée
   arbitraire) et `dossierFieldsSnapshot`, la clé serait silencieusement
   écrasée par les champs dossier masqués. Non bloquant aujourd'hui
   (aucun producteur ne fait les deux — `NotificationSendController`, le
   seul avec payload libre, ne supporte pas `dossierFieldsSnapshot`, hors
   périmètre par design), mais à garder en tête si un futur producteur
   combine les deux.

## Integration Points

- `SendNotificationUseCaseImpl` dépend maintenant de
  `DossierTypeRepository` (`domain/dossier/port/out`) — couplage croisé
  domaine notification → domaine dossier, assumé et justifié dans le
  design doc (D-WEBHOOK-MASK), vérifié non bloqué par les règles ArchUnit
  hexagonales existantes (elles interdisent domaine→framework et
  domaine→infrastructure, pas domaine→domaine).
- Aucun endpoint HTTP nouveau ou modifié.
- Aucun changelog Liquibase.

## Error Handling

- `dossierTypeCode` introuvable : pas d'exception, omission fail-safe
  (voir Patterns ci-dessus).
- Le reste du comportement d'erreur de `send()` (channels manquants,
  webhook désactivé, etc.) est inchangé.

## Performance Considerations

Coût nul sur le trafic réel actuel : `buildPayload()` retourne immédiatement
`event.payload()` sans aucun accès repository si `dossierFieldsSnapshot`
est `null` — vrai pour les 2 producteurs Java existants aujourd'hui
(vérifié par `send_noDossierFieldsSnapshot_neverQueriesDossierTypeRepository`).

## Security Notes

- Protection en défense en profondeur : même si un futur développeur
  oublie de masquer manuellement des données sensibles avant de les
  mettre dans un payload de notification, `SendNotificationUseCaseImpl`
  les masque automatiquement dès lors qu'il connaît le type de dossier
  d'origine.
- `Dossier.fields()`/`FieldDefinition.defaultValue()` restreints par
  liste blanche ArchUnit — toute nouvelle extraction de la map brute hors
  liste blanche fait échouer le build, forçant une revue explicite plutôt
  qu'une fuite silencieuse.
- Aucun changement aux mécanismes de sécurité existants (masquage
  `DossierFieldRedactor`, chiffrement `tenant_connector_dek`, etc.) — ce
  sont des protections additionnelles, pas des remplacements.

## Phase 9 — revue finale (2026-08-24)

- **Alignement design** : les 2 décisions actées (D-LOG-GUARD, D-WEBHOOK-MASK)
  sont implémentées telles que décidées, à une correction technique près
  (T1.3, documentée ci-dessus et validée par l'utilisateur avant
  implémentation).
- **Cohérence avec le code existant** : style de test aligné sur
  `D-SECRETS-AUDIT` (`NotificationWebhookResponseSecretMaskingTest`,
  `ConnectorResponseSecretMaskingTest`) ; convention "clé absente plutôt
  que valeur null" respectée (`DossierFieldMerger`, `DossierFieldRedactor`) ;
  fail-safe par omission cohérent avec le reste du projet (ex. `AttachmentScanPort`
  no-op plutôt que blocage, TODO §30).
- **Duplication** : aucune — `redactSensitiveFields()` est un nouveau
  besoin (masquage d'un `Map` brut par nom de champ sensible), distinct
  de `DossierFieldRedactor.redact()` (masquage d'un `Dossier` complet) ;
  pas de fusion possible sans coupler les deux domaines plus qu'assumé.
- **Rupture de contrat** : `NotificationEvent` et
  `SendNotificationUseCaseImpl` (constructeur) ont changé de forme, mais
  tous les appelants en repo compilent sans modification (constructeur de
  convenance) sauf les 2 sites d'injection Spring/tests mis à jour
  atomiquement.
- **Sécurité rollback** : triviale — aucune migration, aucune donnée
  persistée modifiée rétroactivement.
- **Fidélité au TODO d'origine** : le TODO §23 laissait entendre une fuite
  à corriger ; l'audit (Phase 1) a montré qu'aucune fuite active n'existe
  — traité explicitement comme protection préventive plutôt que
  silencieusement requalifié, avec citation de l'audit dans la note de
  résolution TODO.

**Checklist finale :** design ✅ (2/2 décisions implémentées, 1 correction
technique tracée) · pas de gap logique ✅ · sécurité ✅ (défense en
profondeur ajoutée, rien retiré) · points d'intégration vérifiés ✅
(couplage croisé domaine documenté et justifié) · tests ✅ (1088/1088,
dont 18 nouveaux/modifiés + preuve de non-vacuité par contrôle mutant sur
T1.3 et T2.4) · docs à jour ✅.

**Aucun blocage.** Feature prête à être poussée / PR à ouvrir sur demande
explicite de l'utilisateur (convention établie cette session).
