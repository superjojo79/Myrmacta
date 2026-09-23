---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: reviewed
created: 2026-09-22
updated: 2026-09-23
feature: outillage-dmn
phase3: 2026-09-23
---

# Testing Strategy — `outillage-dmn`

Dérivé des requirements revus et du design Phase 3 (`status: reviewed`).
Cases en `- [ ]` jusqu’à Phase 8.

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

- [ ] T-EVAL-1 : version omise → dernière version ; outputs corrects
- [ ] T-EVAL-2 : version explicite → lignes de cette version, pas de la courante
- [ ] T-EVAL-3 : clé inconnue → 404
- [ ] T-EVAL-4 : version absente → 404
- [ ] T-EVAL-5 : aucune règle → liste vide, pas une erreur
- [ ] T-EVAL-6 : échec moteur → 400, distinct de la liste vide

### `TestDecisionUseCaseImpl`

- [ ] T-TEST-1 : tous les cas passent → rapport 100 % passed
- [ ] T-TEST-2 : un cas échoue → passed=false + actualOutputs + message
- [ ] T-TEST-3 : tableau `cases` vide → validation reject
- [ ] T-TEST-4 : délégation à `EvaluateDecisionUseCase` (pas de second
      moteur) — mock verify
- [ ] T-TEST-5 : CSV `input.*` / `expected.*` → même rapport que le JSON équivalent
- [ ] T-TEST-6 : CSV mal formé ou colonne hors préfixe → 400
- [ ] T-TEST-7 : `1000` CSV égale un nombre réel ; `"1000"` JSON reste une chaîne
- [ ] T-TEST-8 : deux lignes dans le désordre ou une ligne en trop → échec
- [ ] T-TEST-9 : 101 cas → 400

### `ListDecisionTableVersionsUseCaseImpl` /
`RedeployDecisionTableUseCaseImpl`

- [ ] T-HIST-1 : versions triées par numéro croissant, avec `deployedAt`
- [ ] T-HIST-2 : clé inconnue → 404
- [ ] T-RB-1 : redéploie le XML de N → nouvelle version courante
- [ ] T-RB-2 : version N absente → 404
- [ ] T-RB-3 : redéploiement repasse par l’allowlist ; XML refusé → pas de deploy
- [ ] T-RB-4 : N déjà courante → 409, aucun nouveau déploiement

### Adaptateurs / tools MCP / contrôleur

- [ ] T-MCP-1 : `evaluate_decision` mappe args → use case
- [ ] T-MCP-2 : `test_decision` / `list_decision_table_versions` /
      `redeploy_decision_table`
- [ ] T-API-1 : REST evaluate / test json / test csv / versions / redeploy — 200 / 400 / 403 / 404 / 409
- [ ] T-API-2 : rôle `DOSSIER_MANAGER` → 403
- [ ] T-API-3 : garde domaine absente sur un acteur non autorisé → rejet avant le port

## Integration Tests

- [ ] T-IT-1 : `DmnDecisionService` réel — evaluate après deploy
      (fixture grille d’éligibilité ou table minimale)
- [ ] T-IT-2 : deux versions déployées ; `version=1` diffère de la courante (`parentDeploymentId`)
- [ ] T-IT-3 : list versions reflète les déploiements Flowable, ordre croissant
- [ ] T-IT-4 : redeploy version 1 → version courante = max+1, XML identique à v1
- [ ] T-IT-5 : XML historique hors allowlist courante → redéploiement refusé
- [ ] T-IT-6 : aucune règle sur le moteur réel → liste vide

## End-to-End Tests (Playwright)

- [ ] T-E2E-1 : zone JSON du Tester → liste de lignes affichée
- [ ] T-E2E-2 : sélecteur de version envoyé en query `version`
- [ ] T-E2E-3 : upload jeu JSON et CSV → rapport pass/fail affiché
- [ ] T-E2E-4 : historique visible après au moins deux déploiements
      (mocks réseau alignés sur les nouveaux contrats)
- [ ] T-E2E-5 : confirmation « Redéployer la version N » ; annulation
      ne appelle pas l’API ; confirm appelle redeploy
- [ ] T-E2E-6 : non-régression parcours brouillon / déployer existant
- [ ] Mocks `frontend/e2e/**` mis à jour pour les nouveaux endpoints

## Test Data

- Fixture DMN minimale (une input, une output, hit policy FIRST) —
  réutiliser / étendre `grille-eligibilite-client.dmn` si stable.
- Jeu JSON de 2–3 cas pour `test_decision`.
- Playwright : `page.route` sur evaluate / versions / redeploy.

## Test Reporting & Coverage

```bash
./mvnw verify
cd frontend && npm run test
cd frontend && npm run test:e2e
```

Enregistrer comptes de tests + Jacoco avant claim Phase 8/9.

## Manual Testing

- Smoke manuel éditeur : Tester + Redéployer avec confirmation lisible
  (libellé D-DMN-ROLLBACK-UX).
- Vérifier qu’aucun libellé UI ne dit « supprimer la version ».

## Performance Testing

- Borne de 100 cas : un test unitaire de rejet au 101e (T-TEST-9).

## Bug Tracking

- Écarts trouvés en TDD → correctif dans le même milestone ; noter dans
  `docs/ai/implementation/2026-09-22-feature-outillage-dmn.md`.
