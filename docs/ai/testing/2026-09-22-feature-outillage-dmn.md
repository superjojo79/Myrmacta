---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
created: 2026-09-22
updated: 2026-09-22
feature: outillage-dmn
phase1_draft: 2026-09-22
---

# Testing Strategy — `outillage-dmn`

Dérivé des critères de succès requirements et des composants design
(brouillon Phase 1). Cases en `- [ ]` jusqu’à Phase 8.

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
- [ ] T-EVAL-2 : version explicite → cette version
- [ ] T-EVAL-3 : clé inconnue → exception métier (404)
- [ ] T-EVAL-4 : version absente → exception métier (404)
- [ ] T-EVAL-5 : refus si tentative d’évaluer hors table déployée
      (A-EVAL-DEPLOYED-ONLY)

### `TestDecisionUseCaseImpl`

- [ ] T-TEST-1 : tous les cas passent → rapport 100 % passed
- [ ] T-TEST-2 : un cas échoue → passed=false + actualOutputs + message
- [ ] T-TEST-3 : tableau `cases` vide → validation reject
- [ ] T-TEST-4 : délégation à `EvaluateDecisionUseCase` (pas de second
      moteur) — mock verify
- [ ] T-TEST-5 : entrée CSV valide → même rapport que JSON équivalent
- [ ] T-TEST-6 : CSV mal formé / colonnes manquantes → 400

### `ListDecisionTableVersionsUseCaseImpl` /
`RollbackDecisionTableUseCaseImpl`

- [ ] T-HIST-1 : liste ordonnée de toutes les versions
- [ ] T-HIST-2 : clé inconnue → vide ou 404 (aligner design Phase 3)
- [ ] T-RB-1 : redéploie le XML de N → nouvelle version courante
- [ ] T-RB-2 : version N absente → 404
- [ ] T-RB-3 : redéploiement repasse par parse allowlist (défense)

### Adaptateurs / tools MCP / contrôleur

- [ ] T-MCP-1 : `evaluate_decision` mappe args → use case
- [ ] T-MCP-2 : `test_decision` / `list_decision_table_versions` /
      `rollback_decision_table`
- [ ] T-API-1 : REST evaluate / test / versions / redeploy — 200 / 403 /
      404
- [ ] T-API-2 : rôle `DOSSIER_MANAGER` → 403

## Integration Tests

- [ ] T-IT-1 : `DmnDecisionService` réel — evaluate après deploy
      (fixture grille d’éligibilité ou table minimale)
- [ ] T-IT-2 : deux versions déployées ; evaluate `version=1` vs
      courante
- [ ] T-IT-3 : list versions reflète les déploiements Flowable
- [ ] T-IT-4 : redeploy version 1 → version courante = max+1, XML
      identique à v1
- [ ] T-IT-5 : sécurité — table non allowlistée ne peut pas arriver
      via evaluate (chemin déployé uniquement) ; smoke que evaluate
      n’ouvre pas une faille JUEL nouvelle

## End-to-End Tests (Playwright)

- [ ] T-E2E-1 : panneau Tester — inputs → résultat affiché
- [ ] T-E2E-2 : Tester avec version choisie (si UI le permet)
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

- Borne max cas de test (à figer Phase 3) : un test unitaire de rejet
  au-delà de la borne.

## Bug Tracking

- Écarts trouvés en TDD → correctif dans le même milestone ; noter dans
  `docs/ai/implementation/2026-09-22-feature-outillage-dmn.md`.
