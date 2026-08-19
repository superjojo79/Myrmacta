---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: complete
created: 2026-08-19
updated: 2026-08-19
feature: openapi-pagination-admin
design_aligned: 2026-08-19
stakeholder_decisions: 2026-08-19
phase1: 2026-08-19
phase2_sync: 2026-08-19
phase3_sync: 2026-08-19
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
- [x] Défaut `page=0`, `size=20` si absents
- [x] `page < 0` ou `> MAX_PAGE` → `IllegalArgumentException` (400)
- [x] `size` 0 / 101 → 400 ; 1 et 100 OK
- [x] `sort` absent → défaut ressource ; `sort` exact OK
- [x] `sort` autre (ex. `name,desc` si seul `name,asc`) → 400

### `PageResult`
- [x] `totalPages` = ceil(totalElements / size)
- [x] `content` immuable / null → liste vide
- [x] `totalElements < 0` rejeté

### Use cases list paginés
- [x] Offset : page 1 size 2 sur 5 éléments → 2 items, totaux 5 / 3 pages
- [x] Filtre templates `templateKey` appliqué **avant** COUNT
- [x] Filtre purge `status` : `content` filtré ; `counts` **toujours** les 4
      totaux tenant (ignore `status` et `page`)
- [x] Purge tri `createdAt` **desc** (plus récent d’abord) ; plus de défaut
      `limit=50` / max 200
- [x] Types : User sans grant → page vide sans appel search ; User VIEW →
      uniquement codes accordés (pas de « trous » de page dus à un filtre
      après OFFSET)
- [x] `includeContent=false` : pas de HTML template dans les items
- [x] ACL / rôles : pas de changement sémantique vs listes actuelles
      (types visibles selon règles existantes)

### Masquage
- [x] Réponse liste webhooks : `secret` masqué, `secretConfigured` booléen
- [x] Config connecteur : secrets masqués (régression)

### OpenApiProperties
- [x] `enabled=false` → bean/routes docs absents
- [x] `enabled=true` → matcher admin only (test sécurité)

### Frontend clients
- [x] `listDossierTypes` / `listConnectors` / `listDocumentTemplates` /
      `listWorkflowTypeConfigs` / `listPurgeOutbox` envoient `page`/`size`
      et lisent `content` (plus un array racine)
- [x] Purge : plus de `limit` ; lit `content` + `counts`
- [x] Client webhooks : list envelope, create, toggle, delete ; secret
      jamais relu depuis GET
- [x] `listAllPages` : 2 pages `size=100` → tableau concaténé = `totalElements`
- [x] `listAllPages` : page vide avant la fin → stop sans boucle infinie
- [x] `CreateDossierView` / `SupervisionView` / `StartWorkflowDialog` utilisent
      le helper (pas seulement `content` de la page 0)

### Vues admin
- [x] Pager Précédent/Suivant désactivé aux bornes
- [x] Changement de page → `router.replace` avec `?page=`
- [x] Hydratation depuis query au mount
- [x] `NotificationWebhooksAdminView` : création, erreur ProblemDetail,
      badge `secretConfigured`, pas d’affichage du secret saisi après submit

## Integration Tests

### Persistence
- [x] Insert N templates / connectors / types / webhooks / wf configs /
      purge rows → totaux et ordre du tri autorisé
- [x] Page hors range → `content=[]`, `totalElements` inchangé
- [x] Purge : `counts.pending` etc. indépendants de `page`/`size`
- [x] Templates `templateKey` + pagination combinés

### MockMvc
- [x] Chaque liste D-LISTS : 200 envelope ; item 0 dans `content`
- [x] Régression : `GET /api/dossiers` envelope inchangée
- [x] Régression : `GET /api/dossiers?ref=` **pas** une page
- [x] `GET /api/connector-definitions` **array** (non paginé)
- [x] Params invalides → 400 ProblemDetail
- [x] 403 rôles insuffisants (inchangé)
- [x] `app.openapi.enabled=false` : `/v3/api-docs` et `/swagger-ui.html`
      non accessibles (401/403/404 selon impl, **pas 200** anonyme)
- [x] `enabled=true` + `TENANT_ADMIN` ou `WORKFLOW_ADMIN` → 200 YAML/JSON
- [x] `enabled=true` + `DOSSIER_MANAGER` → 403
- [x] OpenAPI export : chemins listes = schema envelope ; pas de
      `multipart` / SSE / `/api/webhooks/{id}` POST ingress (D-SPEC)

### Contrat CI
- [x] Test ou step : YAML exporté **identique** à `openapi/openapi.yaml`
      (normaliser fins de ligne). Documenter la commande de régénération.

## End-to-End Tests

Playwright **obligatoire**. Mettre à jour `frontend/e2e/pilotage.spec.ts`
(et specs admin) : mocks **envelope** au lieu d’arrays ;
`purge-outbox` `content` + `counts` (plus `items`).

- [x] Admin types : liste page 0 visible ; pager si mock multi-pages
- [x] Admin templates / connectors / workflow-types : même contrat
- [x] Admin purge : compteurs + file (E2E-PURGE existants adaptés)
- [x] **Nouveau** : `/admin/notification-webhooks` — liste mockée, création
      (POST), lien nav TENANT_ADMIN
- [x] Sync URL : `?page=1` au clic Suivant (au moins une vue admin)
- [x] Régression Supervision pagination dossiers
- [x] Utilisateur non admin : pas de lien Webhooks

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

### Preuve 2026-08-19 (Phase 5)

| Commande | Résultat |
|---|---|
| `./mvnw verify -Dskip.frontend=true -Dmaven.compiler.release=21` | BUILD SUCCESS, **870** tests, Jacoco OK |
| `cd frontend && npm run test` | **129** passed (45 files) |
| `cd frontend && npm run lint` | exit 0 |
| `cd frontend && npm run test:e2e` | **35** passed |

## Manual Testing

- Activer `app.openapi.enabled=true` en local, login admin, ouvrir
  `/swagger-ui.html`, vérifier une liste paginée et l’absence de secret réel.
- Prod-like : flag false → pas d’UI docs.

## Performance Testing

Hors scope V1 (catalogues admin). Purge : s’appuyer sur l’index existant.

## Bug Tracking

Écart envelope / mocks E2E = P0 (casse le SPA). Drift OpenAPI CI = P0.
Secret en clair dans GET/spec = P0 sécu.
