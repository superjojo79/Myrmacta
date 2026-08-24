---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: done
created: 2026-08-24
updated: 2026-08-24
feature: masquage-donnees-sensibles-logs-webhooks
design_ref: docs/ai/design/2026-08-24-feature-masquage-donnees-sensibles-logs-webhooks.md
testing_ref: docs/ai/testing/2026-08-24-feature-masquage-donnees-sensibles-logs-webhooks.md
---

# Project Planning & Task Breakdown — `masquage-donnees-sensibles-logs-webhooks`

## Milestones

- [x] M1 : Garde-fou logs (D-LOG-GUARD)
- [x] M2 : Masquage webhook (D-WEBHOOK-MASK)
- [x] M3 : Vérification et clôture

## Task Breakdown

### M1 : Garde-fou logs

- [x] T1.1 — `Dossier.toString()` sûr (n'imprime jamais `fields`)
- [x] T1.2 — `FieldDefinition.toString()` sûr (n'imprime jamais `defaultValue`)
- [x] T1.3 — Règle ArchUnit **corrigée en implémentation** (liste blanche des
  classes autorisées à appeler `Dossier.fields()`/`FieldDefinition.defaultValue()`,
  pas une détection de logging — vérifiée non réalisable avec ArchUnit,
  cf. design doc D-LOG-GUARD). Prouvée par `ArchitectureRulesFixtureTest`.

### M2 : Masquage webhook

- [x] T2.1 — Nouveau record `DossierFieldsSnapshot` (`dossierTypeCode`, `fields`) ;
  composant optionnel ajouté à `NotificationEvent` via un constructeur canonique
  8-arg + un constructeur de convenance 7-arg délégant à `null` — **aucun
  appelant en repo modifié** (les 3 producteurs + ~13 sites de test
  compilent inchangés grâce au constructeur de convenance)
- [x] T2.2 — `SendNotificationUseCaseImpl` : injection `DossierTypeRepository`,
  `buildPayload()` masque avant `snapshot()`. Décision actée en
  implémentation : `dossierTypeCode` introuvable → clé `dossierFields`
  omise entièrement (fail-safe), pas d'exception
- [x] T2.3 — 6 tests unitaires `SendNotificationUseCaseImpl` (17/17 verts,
  11 existants + 6 nouveaux)
- [x] T2.4 — Test d'intégration bout en bout réel (`SendNotificationUseCaseImpl`
  → `NotificationOutboxRelay` → `WebhookRestAdapter`, seules les frontières
  repository/HTTP mockées) + contrôle mutant manuel (masquage neutralisé
  temporairement, 4 tests détectent la régression, code restauré)

### M3 : Vérification et clôture

- [x] T3.1 — Régression complète : `./mvnw verify` → **1088 tests, 0 échec**,
  seuils Jacoco tenus, `BUILD SUCCESS`. Pas de commande frontend (confirmé
  par `git diff --stat -- frontend/`, zéro sortie)
- [x] T3.2 — Revue Phase 7 (fichier par fichier) contre le design doc —
  aucune lacune trouvée au-delà de la correction déjà tracée (T1.3) ; 1
  limitation mineure documentée (collision possible sur la clé réservée
  `dossierFields` si un futur producteur combine payload libre +
  snapshot — non bloquant, aucun producteur actuel ne le fait)
- [x] T3.3 — TODO §23 clos (note honnête : aucune fuite active trouvée,
  protection préventive ajoutée) ; `FILE-ATTENTE.md` et
  `DEFERRED-FEATURES.md` mis à jour

## Dependencies

Aucune dépendance externe. Séquencement interne : M1 et M2 sont
indépendants entre eux (peuvent être menés dans n'importe quel ordre ou en
parallèle conceptuel) ; M3 dépend des deux.

## Timeline & Estimates

Feature de taille modérée (2 sous-systèmes indépendants + tests) — pas
d'estimation en jours demandée, séquencement M1 → M2 → M3 par simplicité de
suivi (pas de vraie contrainte de parallélisation ici).

## Risks & Mitigation

- **Risque** : le couplage croisé `notification` → `dossier.port.out.DossierTypeRepository`
  (assumé en Phase 3) pourrait surprendre une future revue architecture s'il
  n'est pas documenté clairement. **Mitigation** : justifié explicitement
  dans le design doc (section Design Decisions, D-WEBHOOK-MASK) et rappelé
  dans le doc d'implémentation.
- **Risque** : la règle ArchUnit (T1.3) pourrait être trop large (faux
  positifs sur du code légitime) ou trop étroite (ne détecte pas tous les
  contournements). **Mitigation** : testée explicitement avec une classe de
  fixture qui doit la violer, pas seulement supposée correcte.
- **Risque** : `dossierTypeCode` introuvable en base au moment du `send()`
  (race avec suppression du type) — comportement non spécifié dans le
  design doc, à trancher en implémentation (T2.2) sans bloquer le reste.
  **Mitigation** : scénario de test dédié (testing doc) force une décision
  explicite plutôt qu'un comportement accidentel.

## Resources Needed

Aucune ressource externe/humaine supplémentaire — travail solo IA sur la
branche `claude/queue-next-feature-8r054i`.
