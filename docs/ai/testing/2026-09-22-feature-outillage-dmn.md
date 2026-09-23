---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: reviewed
created: 2026-09-22
updated: 2026-09-23
feature: outillage-dmn
phase3: 2026-09-23
phase8: 2026-09-23
---

# Testing Strategy — `outillage-dmn`

Dérivé des requirements revus et du design Phase 3 (`status: reviewed`).
Cases cochées en Phase 5. La Phase 8 (2026-09-23) a ajouté les branches
manquantes utiles et enregistré les preuves ci-dessous.

## Test Coverage Goals

- Backend : Jacoco seuils projet (`./mvnw verify`) ; classes nouvelles
  à 100 % branche ciblé quand raisonnable (précédent ClamAV / DMN).
- Frontend unit : Vitest.
- **Frontend E2E : Playwright obligatoire** (SPA `/admin/dmn-editor`
  touchée).
- IT Flowable réel pour evaluate / versions / redeploy (pas seulement
  mocks) sur au moins un chemin happy-path + un chemin sécurité.

## Unit Tests

### `EvaluateDecisionUseCaseImpl`

- [x] T-EVAL-1 : version omise → dernière version ; outputs corrects
- [x] T-EVAL-2 : version explicite → lignes de cette version, pas de la courante
- [x] T-EVAL-3 : clé inconnue → 404
- [x] T-EVAL-4 : version absente → 404
- [x] T-EVAL-5 : aucune règle → liste vide, pas une erreur
- [x] T-EVAL-6 : échec moteur → 400, distinct de la liste vide

### `TestDecisionUseCaseImpl`

- [x] T-TEST-1 : tous les cas passent → rapport 100 % passed
- [x] T-TEST-2 : un cas échoue → passed=false + actualOutputs + message
- [x] T-TEST-3 : tableau `cases` vide → validation reject
- [x] T-TEST-4 : délégation à `EvaluateDecisionUseCase` (pas de second
      moteur) — mock verify
- [x] T-TEST-5 : CSV `input.*` / `expected.*` → même rapport que le JSON équivalent
- [x] T-TEST-6 : CSV mal formé ou colonne hors préfixe → 400
- [x] T-TEST-7 : `1000` CSV égale un nombre réel ; `"1000"` JSON reste une chaîne
- [x] T-TEST-8 : deux lignes dans le désordre ou une ligne en trop → échec
- [x] T-TEST-9 : 101 cas → 400

### `ListDecisionTableVersionsUseCaseImpl` /
`RedeployDecisionTableUseCaseImpl`

- [x] T-HIST-1 : versions triées par numéro croissant, avec `deployedAt`
- [x] T-HIST-2 : clé inconnue → 404
- [x] T-RB-1 : redéploie le XML de N → nouvelle version courante
- [x] T-RB-2 : version N absente → 404
- [x] T-RB-3 : redéploiement repasse par l’allowlist ; XML refusé → pas de deploy
- [x] T-RB-4 : N déjà courante → 409, aucun nouveau déploiement

### Adaptateurs / tools MCP / contrôleur

- [x] T-MCP-1 : `evaluate_decision` mappe args → use case
- [x] T-MCP-2 : `test_decision` / `list_decision_table_versions` /
      `redeploy_decision_table`
- [x] T-API-1 : REST evaluate / test json / test csv / versions / redeploy — 200 / 400 / 403 / 404 / 409
- [x] T-API-2 : rôle `DOSSIER_MANAGER` → 403
- [x] T-API-3 : garde domaine absente sur un acteur non autorisé → rejet avant le port

## Integration Tests

- [x] T-IT-1 : `DmnDecisionService` réel — evaluate après deploy
      (fixture grille d’éligibilité ou table minimale)
- [x] T-IT-2 : deux versions déployées ; `version=1` diffère de la courante (`parentDeploymentId`)
- [x] T-IT-3 : list versions reflète les déploiements Flowable, ordre croissant
- [x] T-IT-4 : redeploy version 1 → version courante = max+1, XML identique à v1
- [x] T-IT-5 : XML historique hors allowlist courante → redéploiement refusé
- [x] T-IT-6 : aucune règle sur le moteur réel → liste vide

## End-to-End Tests (Playwright)

- [x] T-E2E-1 : zone JSON du Tester → liste de lignes affichée
- [x] T-E2E-2 : sélecteur de version envoyé en query `version`
- [x] T-E2E-3 : upload jeu JSON et CSV → rapport pass/fail affiché
- [x] T-E2E-4 : historique visible après au moins deux déploiements
      (mocks réseau alignés sur les nouveaux contrats)
- [x] T-E2E-5 : confirmation « Redéployer la version N » ; annulation
      ne appelle pas l’API ; confirm appelle redeploy
- [x] T-E2E-6 : non-régression parcours brouillon / déployer existant
- [x] Mocks `frontend/e2e/**` mis à jour pour les nouveaux endpoints

## Test Data

- Fixture DMN minimale (une input, une output, hit policy FIRST) —
  réutiliser / étendre `grille-eligibilite-client.dmn` si stable.
- Jeu JSON de 2–3 cas pour `test_decision`.
- Playwright : `page.route` sur evaluate / versions / redeploy.

## Test Reporting & Coverage

Preuves fraîches du 2026-09-23 (cette session, JDK 21 via `-Djava.version=21`) :

- `./mvnw verify -Djava.version=21 -Dskip.frontend=true` : **BUILD SUCCESS**, Tests run **2133**, Failures 0, Errors 0, Jacoco « All coverage checks have been met ».
- `cd frontend && npm run test` : **358** tests, 80 fichiers, exit 0.
- `PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/google/chrome/chrome npm run test:e2e` : **84** passed, exit 0.

Branches laissées volontairement : corps REST `null` (`@RequestBody` obligatoire),
`suite == null` côté Jackson, `IOException` de lecture XML, `BigInteger` et
`NumberFormatException` du comparateur (le motif numérique ne les produit pas).

## Manual Testing

- Smoke manuel éditeur : Tester + Redéployer avec confirmation lisible
  (libellé D-DMN-ROLLBACK-UX).
- Vérifier qu’aucun libellé UI ne dit « supprimer la version ».

## Performance Testing

- Borne de 100 cas : un test unitaire de rejet au 101e (T-TEST-9).

## Bug Tracking

- Écarts trouvés en TDD → correctif dans le même milestone ; noter dans
  `docs/ai/implementation/2026-09-22-feature-outillage-dmn.md`.
