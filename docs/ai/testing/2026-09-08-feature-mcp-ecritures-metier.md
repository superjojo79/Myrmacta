---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: in-progress
feature: mcp-ecritures-metier
---

# Testing Strategy — `mcp-ecritures-metier`

## Test Coverage Goals

- Backend : `./mvnw verify` — Jacoco LINE ≥ 85 % / BRANCH ≥ 75 % (bundle).
  Classes **nouvelles** : viser 100 % des branches propres à la feature.
- Frontend : **régression uniquement** (zéro fichier `frontend/**`). Vitest si
  le bac le permet ; Playwright non exigé (FILE-ATTENTE A3 : backend-only).
- IT HTTP MCP (`MockMvc` + JWT M2M) pour create / add / tools/list = 20.

## Unit Tests

### `McpArgs`

- [x] `T-ARGS-UUID` UUID valide / blanc / mal formé
- [x] `T-ARGS-MAP` map présente / absente / type invalide → `{}` ou IAE
- [x] `T-ARGS-UUID-NULL` / `T-ARGS-MAP-KEY` / `T-ARGS-NONBLANK` (Phase 8 — branches)

### `CreateDossierTool`

- [x] `T-CREATE-OK` délègue `CreateDossierCommand` (tenant, type, label, fields, createdBy=sub)
- [x] `T-CREATE-DEFAULT-FIELDS` `fields` null → `Map.of()`
- [x] `T-CREATE-BLANK` type/label blank → IAE, use case non appelé
- [x] `T-CREATE-DRY` `dryRun=true` : charge le type, valide, **ne** persiste **pas**
- [x] `T-CREATE-DRY-INACTIVE` type inactif → `InactiveDossierTypeException`
- [x] `T-CREATE-DRY-FIELDS` champs invalides → `DossierFieldValidationException`

### `AddDossierElementTool`

- [x] `T-ADD-NOTE` type NOTE + metadata.text → `AddCommand` correct
- [x] `T-ADD-REJECT-BINARY` `PRODUCED_DOC` / `ATTACHMENT` → IAE, use case non appelé
- [x] `T-ADD-BAD-TYPE` type inconnu → IAE
- [x] `T-ADD-DRY` dryRun : get dossier + pas d'`add`

### `StartWorkflowTool`

- [x] `T-START-OK` `orchestrator.start(dossierId, key, vars, idempotency, actor)`
- [x] `T-START-NO-KEY` idempotencyKey blank → IAE, orchestrateur non appelé
- [x] `T-START-RESERVED` variables avec `tenantId` → IAE
- [x] `T-START-ACL` `assertCanEdit` après get ; refus → orchestrateur non appelé
- [x] `T-START-REPLAY` `replay=true` dans le résultat
- [x] `T-START-DRY` dryRun : get + edit + reserved check, orchestrateur non appelé

### `GenerateDocumentTool`

- [x] `T-GEN-OK` generate → store → add `PRODUCED_DOC` ; label `Document généré — {templateKey}`
- [x] `T-GEN-TEMPLATE` template absent du type → `AccessDeniedException`, pas de generate
- [x] `T-GEN-VARS` overlay fusionné avec `DossierMcpView.fields` ; reserved overlay → IAE
- [x] `T-GEN-DRY` pas de generate / store / add
- [x] `T-GEN-ADDED-BY` `addedBy` = sujet JWT, pas `"system"`
- [x] `T-GEN-VIEW` sortie = `DossierElementView` (pas de storageRef)
- [x] `T-GEN-META` variant/locale absents ou blank omis des metadata (Phase 8)

## Integration Tests

- [x] `IT-LIST-20` `McpEndpointApiTest` : `EXPECTED_TOOLS` contient les 4 nouveaux (20 au total)
- [x] `IT-CREATE` `tools/call create_dossier` persiste ; `createdBy` = `agent-1` ; relisible
- [x] `IT-ADD` `add_dossier_element` NOTE sur le dossier créé
- [x] `IT-CREATE-ERROR` type inconnu → `isError=true` (pas de stack)

`start_workflow` / `generate_document` bout-en-bout réel (Flowable + template HTML)
sont **hors IT MCP** si le setup dépasse le pattern existant : couverts en unitaire
contre les vrais ports mockés. Un IT generate n'est ajouté que s'il existe déjà un
template seed facile à réutiliser — sinon pas d'IT disproportionné.

## End-to-End Tests

- [x] N/A SPA — zéro contrat frontend. Pas de nouveau `frontend/e2e/**`.
- [ ] Régression Vitest seulement si exécutable dans le bac (optionnel A3).

## Test Data

- JWT M2M `tenant_dev` + `API_SERVICE` (`OidcClientTestConfiguration`, pattern
  `McpToolCallIntegrationTest`).
- Types de dossier créés en IT avec un code unique (`MCPIT` + UUID).
- Unitaires : mocks ports + `McpAccessContextFactory` réel + JWT `sub=agent-1`.

## Test Reporting & Coverage

```bash
./mvnw test -Dtest=CreateDossierToolTest,AddDossierElementToolTest,StartWorkflowToolTest,GenerateDocumentToolTest,McpArgsTest,McpEndpointApiTest,McpToolCallIntegrationTest
./mvnw verify -Dskip.frontend=true
```

Jacoco : balayage **classe par classe** des 4 tools + `McpArgs` en Phase 8
(chiffres dans cette section après `./mvnw verify` frais de la session).

## Manual Testing

Smoke local optionnel : `tools/list` après JWT M2M (GETTING-STARTED §12).
