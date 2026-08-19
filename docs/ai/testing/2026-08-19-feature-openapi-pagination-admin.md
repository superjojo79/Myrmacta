---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
created: 2026-08-19
updated: 2026-08-19
feature: openapi-pagination-admin
design_aligned: 2026-08-19
stakeholder_decisions: 2026-08-19
phase1: 2026-08-19
phase2_sync: 2026-08-19
---

# Testing Strategy — `openapi-pagination-admin`

## Test Coverage Goals
**What level of testing do we aim for?**

- Backend: Jacoco ≥ seuil projet sur code nouveau/modifié (`./mvnw verify`).
- Frontend unit: Vitest (`cd frontend && npm run test`).
- **Frontend E2E Playwright obligatoire** (SPA + contrat API consommé par le
  SPA) : `cd frontend && npm run test:e2e` (ou `test:all`).
- IT MockMvc listes + persistence COUNT/OFFSET ; contrat OpenAPI fail-on-diff.
- ArchUnit : springdoc / DTOs page hors `domain`.

## Unit Tests

### `PageQueryParser` / allow-list sort
- [ ] Défaut `page=0`, `size=20` si absents
- [ ] `page < 0` ou `> MAX_PAGE` → `IllegalArgumentException` (400)
- [ ] `size` 0 / 101 → 400 ; 1 et 100 OK
- [ ] `sort` absent → défaut ressource ; `sort` exact OK
- [ ] `sort` autre (ex. `name,desc` si seul `name,asc`) → 400

### `PageResult`
- [ ] `totalPages` = ceil(totalElements / size)
- [ ] `content` immuable / null → liste vide
- [ ] `totalElements < 0` rejeté

### Use cases list paginés
- [ ] Offset : page 1 size 2 sur 5 éléments → 2 items, totaux 5 / 3 pages
- [ ] Filtre templates `templateKey` appliqué **avant** COUNT
- [ ] Filtre purge `status` + COUNT globaux `counts` **non** filtrés par page
      (counts = toute la file ; content = page filtrée)
- [ ] `includeContent=false` : pas de HTML template dans les items
- [ ] ACL / rôles : pas de changement sémantique vs listes actuelles
      (types visibles selon règles existantes)

### Masquage
- [ ] Réponse liste webhooks : `secret` masqué, `secretConfigured` booléen
- [ ] Config connecteur : secrets masqués (régression)

### OpenApiProperties
- [ ] `enabled=false` → bean/routes docs absents
- [ ] `enabled=true` → matcher admin only (test sécurité)

### Frontend clients
- [ ] `listDossierTypes` / `listConnectors` / `listDocumentTemplates` /
      `listWorkflowTypeConfigs` / `listPurgeOutbox` envoient `page`/`size`
      et lisent `content` (plus un array racine)
- [ ] Purge : plus de `limit` ; lit `content` + `counts`
- [ ] Client webhooks : list envelope, create, toggle, delete ; secret
      jamais relu depuis GET
- [ ] `listAllPages` : 2 pages `size=100` → tableau concaténé = `totalElements`
- [ ] `listAllPages` : page vide avant la fin → stop sans boucle infinie
- [ ] `CreateDossierView` / `SupervisionView` / `StartWorkflowDialog` utilisent
      le helper (pas seulement `content` de la page 0)

### Vues admin
- [ ] Pager Précédent/Suivant désactivé aux bornes
- [ ] Changement de page → `router.replace` avec `?page=`
- [ ] Hydratation depuis query au mount
- [ ] `NotificationWebhooksAdminView` : création, erreur ProblemDetail,
      badge `secretConfigured`, pas d’affichage du secret saisi après submit

## Integration Tests

### Persistence
- [ ] Insert N templates / connectors / types / webhooks / wf configs /
      purge rows → totaux et ordre du tri autorisé
- [ ] Page hors range → `content=[]`, `totalElements` inchangé
- [ ] Purge : `counts.pending` etc. indépendants de `page`/`size`
- [ ] Templates `templateKey` + pagination combinés

### MockMvc
- [ ] Chaque liste D-LISTS : 200 envelope ; item 0 dans `content`
- [ ] Régression : `GET /api/dossiers` envelope inchangée
- [ ] Régression : `GET /api/dossiers?ref=` **pas** une page
- [ ] `GET /api/connector-definitions` **array** (non paginé)
- [ ] Params invalides → 400 ProblemDetail
- [ ] 403 rôles insuffisants (inchangé)
- [ ] `app.openapi.enabled=false` : `/v3/api-docs` et `/swagger-ui.html`
      non accessibles (401/403/404 selon impl, **pas 200** anonyme)
- [ ] `enabled=true` + `TENANT_ADMIN` ou `WORKFLOW_ADMIN` → 200 YAML/JSON
- [ ] `enabled=true` + `DOSSIER_MANAGER` → 403
- [ ] OpenAPI export : chemins listes = schema envelope ; pas de
      `multipart` / SSE / `/api/webhooks/{id}` POST ingress (D-SPEC)

### Contrat CI
- [ ] Test ou step : YAML exporté **identique** à `openapi/openapi.yaml`
      (normaliser fins de ligne). Documenter la commande de régénération.

## End-to-End Tests

Playwright **obligatoire**. Mettre à jour `frontend/e2e/pilotage.spec.ts`
(et specs admin) : mocks **envelope** au lieu d’arrays ;
`purge-outbox` `content` + `counts` (plus `items`).

- [ ] Admin types : liste page 0 visible ; pager si mock multi-pages
- [ ] Admin templates / connectors / workflow-types : même contrat
- [ ] Admin purge : compteurs + file (E2E-PURGE existants adaptés)
- [ ] **Nouveau** : `/admin/notification-webhooks` — liste mockée, création
      (POST), lien nav TENANT_ADMIN
- [ ] Sync URL : `?page=1` au clic Suivant (au moins une vue admin)
- [ ] Régression Supervision pagination dossiers
- [ ] Utilisateur non admin : pas de lien Webhooks

## Test Data

- Fixtures MockMvc : 25+ rows pour forcer page 2 (`size=20`).
- Playwright `page.route` : `{ content, page, size, totalElements, totalPages }`
  ; purge + `counts`.
- OpenAPI : fichier commité comme golden ; pas de secrets d’exemple.

## Test Reporting & Coverage

```bash
./mvnw verify -Dmaven.compiler.release=21
cd frontend && npm run test
cd frontend && npm run test:e2e
# ou: cd frontend && npm run test:all
```

- Enregistrer commandes + codes sortie avant claim Phase 8.
- Playwright : counts passed/failed du rapport.

## Manual Testing

- Activer `app.openapi.enabled=true` en local, login admin, ouvrir
  `/swagger-ui.html`, vérifier une liste paginée et l’absence de secret réel.
- Prod-like : flag false → pas d’UI docs.

## Performance Testing

Hors scope V1 (catalogues admin). Purge : s’appuyer sur l’index existant.

## Bug Tracking

Écart envelope / mocks E2E = P0 (casse le SPA). Drift OpenAPI CI = P0.
Secret en clair dans GET/spec = P0 sécu.
