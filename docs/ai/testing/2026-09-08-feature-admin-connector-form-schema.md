---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
---

# Testing Strategy

## Test Coverage Goals

- Backend : **aucun** fichier Java. `./mvnw verify` = régression seulement, pas une
  exigence de nouveaux tests Jacoco. Feature FE-only.
- Frontend unit : Vitest, seuils `vite.config.ts` (lines 80 / statements 75 / functions 70 /
  branches 60) tenus ; fichiers nouveaux à ~100 % branches utiles.
- **Playwright obligatoire** (SPA). `cd frontend && npm run test:e2e`.

## Unit Tests

### `connectorConfig.ts` (T-UTIL)

- [x] T-UTIL-1 : `defaultsFromSchema` — STRING defaultValue, SELECT required → première option
      si pas de default, BOOLEAN → `false`, champs vides → `''`.
- [x] T-UTIL-2 : `configFromFormValues` — INTEGER `"3"` → `3`, BOOLEAN `true`, STRING conservée.
- [x] T-UTIL-3 : optionnel `''` / PASSWORD vide **omis** (D-COERCE / D-DESIGN-OMIT-BLANK).
- [x] T-UTIL-4 : INTEGER vide optionnel omis ; INTEGER invalide (`abc`) omis (le HTML `required`
      couvre le requis).
- [x] T-UTIL-5 : `schemaOf` — type inconnu / undefined → `[]`.
- [x] T-UTIL-6 : schéma `[]` → `configFromFormValues` → `{}`.
- [x] T-UTIL-7 : `ingressUrlFor` — `http-rest` → `/api/webhooks/{id}` avec préfixe tenant ;
      `flowable-callback` → `/api/callbacks/{id}` ; `smtp`/`scheduler`/… → `null`.

### `ConnectorConfigForm.vue` (T-FORM)

- [x] T-FORM-1 : rend un input text, password, select, checkbox, url, number selon le type.
- [x] T-FORM-2 : `required` HTML sur STRING requis, pas sur BOOLEAN.
- [x] T-FORM-3 : `helpText` affiché.
- [x] T-FORM-4 : `v-model` met à jour la valeur (input + select + checkbox).
- [x] T-FORM-5 : schéma vide → aucun contrôle, slot / message parent.

### `ConnectorsAdminView.vue` (T-VIEW) — étend `adminActions.spec.ts` / `adminViews.spec.ts`

- [x] T-VIEW-1 : catalogue avec `configSchema` http-rest → **pas** de `textarea` ; champs labelés.
- [x] T-VIEW-2 : submit poste `config` objet (SELECT + password + strings), pas une string JSON.
- [x] T-VIEW-3 : type `smtp` / schéma `[]` → pas de textarea ; submit `config: {}`.
- [x] T-VIEW-4 : changer de type reset les valeurs (pas de fuite `webhookSecret` vers scheduler).
- [x] T-VIEW-5 : `listConnectorDefinitions` `[]` → textarea JSON conservé (D-FALLBACK-NO-DEFS).
- [x] T-VIEW-6 : toggle / delete inchangés (régression `adminActions`).
- [x] T-VIEW-7 : liste `http-rest` → `POST {origin}/api/webhooks/{id}`.
- [x] T-VIEW-8 : liste `flowable-callback` → `POST {origin}/api/callbacks/{id}`.
- [x] T-VIEW-9 : liste `smtp` → pas d’URL ingress.

### `ConnectorIngressUrl.vue` (T-INGRESS)

- [x] T-INGRESS-1 : libellé webhook + `POST {url}`.
- [x] T-INGRESS-2 : libellé callback.
- [x] T-INGRESS-3 : Copier appelle `clipboard.writeText(url)` (sans le mot POST).

### `admin.ts` (T-API)

- [x] T-API-1 : `listConnectorDefinitions` type `ConnectorDefinition` (compile + appel inchangé).

## Integration Tests

Pas d’IT Java. Le contrat `GET /api/connector-definitions` est déjà couvert backend.

## End-to-End Tests

Mocks `page.route('**/api/connector-definitions*')` **doivent** renvoyer un envelope avec
`configSchema` (aujourd’hui `envelope([])` — à enrichir sans casser les tests existants qui
ne visitent pas l’écran).

- [x] E2E-CONN-1 : Admin → Connecteurs ; créer `http-rest` via SELECT + champs ; POST body
      `config.webhookAuthType` / `webhookSecret` / `processKey` / `dossierTypeCode` (pas
      `configJson`).
- [x] E2E-CONN-2 : créer `smtp` ; POST `config` `{}` ; pas de textarea ; pas d’URL ingress.
- [x] E2E-CONN-3 : créer `flowable-callback` ; liste affiche `POST …/api/callbacks/{id}`.
- [x] Régression : suite `pilotage.spec.ts` existante verte.

## Test Data

Fixture http-rest minimale (sous-ensemble réel de `HttpRestConnector.getConfigSchema()`) :

```json
{
  "type": "http-rest",
  "label": "HTTP REST",
  "capability": "BOTH",
  "configSchema": [
    { "name": "webhookAuthType", "label": "Webhook auth", "type": "SELECT", "required": true,
      "defaultValue": "HMAC_SHA256", "options": ["HMAC_SHA256", "SHARED_SECRET_TOKEN"] },
    { "name": "webhookSecret", "label": "Webhook secret", "type": "PASSWORD", "required": true },
    { "name": "processKey", "label": "Process key", "type": "STRING", "required": true },
    { "name": "dossierTypeCode", "label": "Dossier type", "type": "STRING", "required": true }
  ]
}
```

Fixture smtp : `{ "type": "smtp", "label": "SMTP", "capability": "SERVICE", "configSchema": [] }`.

## Test Reporting & Coverage

```bash
cd frontend && npm run test
cd frontend && npm run test:coverage
cd frontend && npm run test:e2e
```

Jacoco backend non applicable aux fichiers de cette feature. Régression `./mvnw verify`
non exigée (aucun `.java` dans le diff — `git diff --stat origin/main` vérifié).

### Preuves Phase 8 (2026-09-08)

| Commande | Résultat |
|---|---|
| `cd frontend && npm run test` | **324** passed, 0 failed, exit 0 |
| `cd frontend && npm run typecheck` | exit 0 |
| `cd frontend && npm run lint` | exit 0 |
| `cd frontend && npm run test:coverage` | statements 87.4 / branches 80.34 / functions 86.5 / lines 89.03 (seuils tenus). `components/connectors` : lines 98.55 %, branches 95.83 % |
| `cd frontend && npx playwright test` | **68** passed (65 existants + `E2E-CONN-1` + `E2E-CONN-2` + `E2E-CONN-3`), exit 0 |

Écart de test (pas produit) : premier passage E2E, POST ok mais liste vide — le GET
post-création `fallback()` vers le mock session (liste vide). Corrigé : même `page.route`
gère GET+POST avec liste accumulée.

Écart D-WEBHOOK-URL (produit, 2026-09-08) : `resolveApiBaseUrl()` au rendu sur
`/admin/connectors` avec Vite `base: './'` prenait `admin` comme préfixe tenant
(`…/admin/api/webhooks/{id}`). Corrigé : `apiContextPath()` = `axios.defaults.baseURL`
fixé au boot.

## Manual Testing

Walkthrough navigateur : `/admin/connectors`, créer http-rest (URL webhook), créer
`flowable-callback` (URL callback), créer smtp (pas d’URL), switch de type, Copier.
Vérifier desktop (écran admin, pas de layout mobile dédié).

## Performance Testing

N/A (formulaire < 10 champs).

## Bug Tracking

Écarts trouvés en Phase 5/7/8 consignés dans le doc implementation.
