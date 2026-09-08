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

- [ ] T-UTIL-1 : `defaultsFromSchema` — STRING defaultValue, SELECT required → première option
      si pas de default, BOOLEAN → `false`, champs vides → `''`.
- [ ] T-UTIL-2 : `configFromFormValues` — INTEGER `"3"` → `3`, BOOLEAN `true`, STRING conservée.
- [ ] T-UTIL-3 : optionnel `''` / PASSWORD vide **omis** (D-COERCE / D-DESIGN-OMIT-BLANK).
- [ ] T-UTIL-4 : INTEGER vide optionnel omis ; INTEGER invalide (`abc`) omis (le HTML `required`
      couvre le requis).
- [ ] T-UTIL-5 : `schemaOf` — type inconnu / undefined → `[]`.
- [ ] T-UTIL-6 : schéma `[]` → `configFromFormValues` → `{}`.

### `ConnectorConfigForm.vue` (T-FORM)

- [ ] T-FORM-1 : rend un input text, password, select, checkbox, url, number selon le type.
- [ ] T-FORM-2 : `required` HTML sur STRING requis, pas sur BOOLEAN.
- [ ] T-FORM-3 : `helpText` affiché.
- [ ] T-FORM-4 : `v-model` met à jour la valeur (input + select + checkbox).
- [ ] T-FORM-5 : schéma vide → aucun contrôle, slot / message parent.

### `ConnectorsAdminView.vue` (T-VIEW) — étend `adminActions.spec.ts` / `adminViews.spec.ts`

- [ ] T-VIEW-1 : catalogue avec `configSchema` http-rest → **pas** de `textarea` ; champs labelés.
- [ ] T-VIEW-2 : submit poste `config` objet (SELECT + password + strings), pas une string JSON.
- [ ] T-VIEW-3 : type `smtp` / schéma `[]` → pas de textarea ; submit `config: {}`.
- [ ] T-VIEW-4 : changer de type reset les valeurs (pas de fuite `webhookSecret` vers scheduler).
- [ ] T-VIEW-5 : `listConnectorDefinitions` `[]` → textarea JSON conservé (D-FALLBACK-NO-DEFS).
- [ ] T-VIEW-6 : toggle / delete inchangés (régression `adminActions`).

### `admin.ts` (T-API)

- [ ] T-API-1 : `listConnectorDefinitions` type `ConnectorDefinition` (compile + appel inchangé).

## Integration Tests

Pas d’IT Java. Le contrat `GET /api/connector-definitions` est déjà couvert backend.

## End-to-End Tests

Mocks `page.route('**/api/connector-definitions*')` **doivent** renvoyer un envelope avec
`configSchema` (aujourd’hui `envelope([])` — à enrichir sans casser les tests existants qui
ne visitent pas l’écran).

- [ ] E2E-CONN-1 : Admin → Connecteurs ; créer `http-rest` via SELECT + champs ; POST body
      `config.webhookAuthType` / `webhookSecret` / `processKey` / `dossierTypeCode` (pas
      `configJson`).
- [ ] E2E-CONN-2 : créer `smtp` ; POST `config` `{}` ; pas de textarea.
- [ ] Régression : suite `pilotage.spec.ts` existante verte.

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
recommandée en Phase 8/9 (aucun `.java` touché — smoke).

## Manual Testing

Walkthrough navigateur : `/admin/connectors`, créer http-rest, créer smtp, switch de type.
Vérifier desktop (écran admin, pas de layout mobile dédié).

## Performance Testing

N/A (formulaire < 10 champs).

## Bug Tracking

Écarts trouvés en Phase 5/7/8 consignés dans le doc implementation.
