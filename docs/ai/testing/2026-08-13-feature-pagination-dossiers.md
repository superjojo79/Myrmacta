---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
created: 2026-08-13
updated: 2026-08-13
feature: pagination-dossiers
design_aligned: 2026-08-13
stakeholder_decisions: 2026-08-13
phase2_sync: 2026-08-13
phase3_sync: 2026-08-13
---

# Testing Strategy

## Test Coverage Goals
**What level of testing do we aim for?**

- **Backend :** ≥ 80 % lignes sur code nouveau/modifié (Jacoco projet).
- **Frontend :** Vitest composants/API client ; pas de baisse sous seuil
  coverage FE existant (≥ 80 % lines).
- **IT :** MockMvc liste + H2 persistence search ; régression `?ref=`.
- **E2E Playwright :** hors scope V1 de cette feature (couvert par Vitest
  Supervision + sync URL).

## Unit Tests
**What individual components need testing?**

### `SearchDossiersUseCaseImpl`
- [ ] Admin bypass → aucun filtre de type injecté (tous types)
- [ ] User avec grants VIEW → `allowedTypeCodes` restreint
- [ ] Zéro grant → page vide sans appel search (short-circuit)
- [ ] `dossierTypeCode` hors allowed → page vide
- [ ] `dossierTypeCode` dans allowed → délégué au port avec filtre
- [ ] `reference` blank → traité absent
- [ ] Attache `dossierTypeLabel` via batch types ; `""` si type manquant
- [ ] `AccessContext.Service` → `AccessDeniedException` / 403

### `JpaDossierSearchAdapter` / Specs
- [ ] Projection **ne charge pas** `fields` (assert colonnes / item sans fields)
- [ ] Spec status / type-in / reference prefix CI
- [ ] Tri `updatedAt` desc + pagination totals

### Validation / parsing (controller ou helper)
- [ ] `page < 0` → 400
- [ ] `size` 0 / 101 → 400 ; défaut 20 si absent ; `1` et `100` OK
- [ ] `sort=reference,asc` → 400 ; `updatedAt,desc` / absent → OK
- [ ] `status=NOPE` → 400 ; `status=OUVERT` → OK
- [ ] Item JSON contient `label` + `dossierTypeLabel` ; pas de `fields`
- [ ] `GET …?ref=…&page=0` → get-by-ref (pas envelope liste)

### Frontend (état local)
- [ ] `listDossiers` envoie les bons query params
- [ ] `SupervisionView` affiche les lignes et pagine (state local, pas Pinia search)
- [ ] Changement filtre met à jour l’URL (`router.replace`)
- [ ] Hydratation depuis query URL au mount
- [ ] Clic ligne → navigation `/dossiers/{id}`
- [ ] Formulaire ouvrir-par-ref toujours présent et fonctionnel (mock API)

## Integration Tests
**How do we test component interactions?**

### Persistence / search adapter
- [ ] Insert N dossiers → page 0 size 20 → totaux corrects
- [ ] Filtre `status` exact
- [ ] Filtre `dossierTypeCode` exact
- [ ] Filtre `reference` préfixe case-insensitive
- [ ] Tri `updatedAt` desc stable
- [ ] Combinaison filtres + pagination page 1

### MockMvc API
- [ ] `GET /api/dossiers` authentifié → 200 envelope
- [ ] `GET /api/dossiers?ref=…` → **toujours** un `DossierResponse` (régression)
- [ ] User sans ACL → `content=[]`, `totalElements=0`
- [ ] Type hors ACL → page vide
- [ ] Params invalides → 400 ProblemDetail
- [ ] Appel style service/API_SERVICE → 403
- [ ] Anonyme → 401

## End-to-End Tests
**What user flows need validation?**

- [ ] Hors scope Playwright pour cette feature
- [ ] Manuel smoke : Supervision liste + filtre + deep-link URL (checklist)

## Test Data
**What data do we use for testing?**

- Fixtures : 2 types (`FAC`, `CTR`) ; grants VIEW sur `FAC` seul pour user
  standard ; dossiers mélangés statuts / refs `DOS-FAC-…`, `DOS-CTR-…`.
- Labels types distincts pour assert `dossierTypeLabel`.
- Timestamps contrôlés pour ordre `updatedAt`.

## Test Reporting & Coverage
**How do we verify and communicate test results?**

```bash
./mvnw test
cd frontend && npm run test -- --coverage
```

Évidence Phase 8 : jacoco zones `dossier` search + rapports Vitest
`SupervisionView`.

## Manual Testing
**What requires human validation?**

- [ ] UX filtres + pagination clavier/focus basique
- [ ] Copier-coller URL filtrée dans un nouvel onglet
- [ ] Vérifier absence de `fields` dans le JSON Network

## Performance Testing
**How do we validate performance?**

- Pas de bench dédié V1 ; smoke local avec ~1k dossiers optionnel.
- Valider presence indexes Liquibase en revue design/impl.

## Bug Tracking
**How do we manage issues?**

- Écarts bloquants → Phase 7/9 ; follow-ups non bloquants documentés
  implementation.
