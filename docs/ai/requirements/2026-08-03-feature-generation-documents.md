---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
reviewed: 2026-08-03
created: 2026-08-03
updated: 2026-08-04
stakeholder_decisions: 2026-08-03
phase2: 2026-08-03
phase2_stakeholder_confirm: 2026-08-03
phase7_success_criteria: 2026-08-04
---

# Requirements & Problem Understanding

## Problem Statement
**What problem are we solving?**

FlowForge dispose de templates Thymeleaf versionnés (`templates-documents`)
mais **aucune génération PDF**, **aucun stockage objet abstrait**, et **aucun
rattachement de document produit** au dossier. Sans cette tranche, les
`ServiceTask` BPMN ne peuvent pas matérialiser un PDF ni le lier au dossier
métier.

- **Affectés :** auteurs de processus BPMN, gestionnaires de dossiers
  (`DOSSIER_MANAGER` / `DOSSIER_VIEWER`), `TENANT_ADMIN` / `WORKFLOW_ADMIN`,
  features aval `frontend` / visualisation.
- **Situation actuelle :** `DocumentTemplate` + `TemplateResolver` livrés ;
  packages `infrastructure/pdf` et `infrastructure/storage` absents ; pas de
  table `dossier_element` ; analyse `07-generation-documents.md` +
  `08-templates-documents.md` (§ Flowable) + décision n° 18 ; contrainte
  DEFERRED §0 (port S3/FS, pas MinIO en dur). Pattern delegate existant :
  `UpdateDossierStatusDelegate` (`AccessContext.system`, variable
  `dossierId` UUID string).
- **UX :** pas d’écrans Vue — API REST generate/download + tasks Flowable.
  Erreurs `ProblemDetail` alignées dossiers / templates / connecteurs.

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- Livrer le **socle génération** hexagonal (**D-SCOPE = 2**) :
  `PdfRenderer` (Flying Saucer — **D-ENGINE = 1**),
  `GenerateDocumentUseCase` (resolve template ACTIVE → Thymeleaf → XHTML →
  PDF), `DocumentStoragePort` + adaptateurs **S3** et **filesystem**
  (**D-STORE = 1**).
- **Deux ServiceTasks Flowable** (**D-FLOWABLE = 1**, décision n° 18) :
  1. `documentGenerationTask` — resolve + generate + store → variable
     `generatedDocumentId`
  2. `attachGeneratedDocumentTask` — crée `DossierElement` `PRODUCED_DOC`
     (**D-ELEMENT = 3**, **D-ELEMENT-SCOPE = 1**) via
     `AddDossierElementUseCase` + `AccessContext.system("flowable-delegate")`
     (**A9**)
- **Contrat variables** (**D-VARS = 1**) : entrée `documentType` (→
  `templateKey`), `documentVariant` / `documentLocale` optionnels ;
  `dossierId` (**UUID string**, **A10**) requis pour attach ; sortie
  `generatedDocumentId` (= `storageRef`).
- **API HTTP** (**D-HTTP = 2**) :
  - `POST /api/documents/generate` — rendu **en mémoire** uniquement
    (**D-HTTP-STORE = 1**, **D-HTTP-ATTACH = 1**) → corps PDF
  - `GET /api/dossier-elements/{id}/content` — download octets via
    `storageRef` (**D-DOWNLOAD = 1**)
  - **Pas** d’API HTTP de création d’élément V1 (uniquement task Flowable /
    use case domaine pour tests) (**A11**)
- **Authz** (**D-AUTHZ = 3**, précisé Phase 2) :
  - Generate HTTP : `@PreAuthorize` rôles `TENANT_ADMIN` \|
    `WORKFLOW_ADMIN` \| `DOSSIER_MANAGER` (**A7** : pas `API_SERVICE`)
  - Download : `DossierAccessPolicy.assertCanView` — ACL type `VIEW`
    (ou `EDIT` via `implies`) ; **bypass** `TENANT_ADMIN` /
    `WORKFLOW_ADMIN` et `AccessContext.System` (aligné dossiers) ;
    `DOSSIER_MANAGER` / `DOSSIER_VIEWER` **sans** bypass → grant ACL requis
    (**A12**)
- Tests unitaires + MockMvc/IT H2 + FS storage ; ArchUnit ; Jacoco ≥ 80 %.

### Surface API (acceptation)

| Méthode | Path | Authz / notes |
|---|---|---|
| POST | `/api/documents/generate` | Rôles generate + CSRF ; body `templateKey`, `variantKey?`, `locale?`, `variables?` (null → `{}`) ; `200 application/pdf` ; **pas** de store / attach |
| GET | `/api/dossier-elements/{id}/content` | Auth + policy VIEW (bypass admin) ; `200 application/pdf` ; `Content-Disposition: attachment` avec filename dérivé du label (**A13**) |

### Mapping HTTP ↔ Flowable

| HTTP body / query | Variable Flowable | Notes |
|---|---|---|
| `templateKey` | `documentType` | Même sémantique resolve |
| `variantKey` | `documentVariant` | blank → null |
| `locale` | `documentLocale` | blank → null |
| `variables` | `execution.getVariables()` | Merge Thymeleaf |
| — | `dossierId` | Attach only (UUID string) |
| — | `generatedDocumentId` | Sortie generate task = storageRef |

### Secondary goals

- Port `PdfRenderer` stable pour un futur adaptateur Playwright (hors V1).
- Config `app.storage.type=filesystem|s3` + propriétés S3 (endpoint, bucket,
  credentials via env) ; défaut **filesystem** (dev/IT).
- Label élément : `Document généré — {activityName}` (**A3**).

### Non-goals (explicitly out of scope)

- Preview HTML / PDF inline (D-PREVIEW — différé `frontend` / lot suivant).
- Adaptateur Playwright / CSS3 avancé.
- Upload `ATTACHMENT`, types `INFORMATION` / `NOTE` / `LINK`, liste paginée
  des éléments, API CRUD éléments.
- Table registre `generated_document` séparée (**A5**).
- Purge / TTL / droit à l’effacement cohérent (TODO §22).
- `API_SERVICE` sur generate HTTP (**A7** — follow-up M2M).
- OpenAPI formel, rate-limit PDF, antivirus.
- Couplage domaine à MinIO (nom, SDK propriétaire) — interdit.
- SLA perf PDF en CI (ordres de grandeur analyse = cible soft hors critère
  bloquant — **A14**).

## Alternatives considérées

| Approche | Trade-off | Verdict |
|---|---|---|
| Socle seul vs + Flowable vs tranche analyse complète | Flowable était différé depuis templates | **+ Flowable** (D-SCOPE 2) |
| FS seul / S3 seul / S3+FS | Dual = surface + flex ops | **S3+FS** (D-STORE 1) |
| Sans élément / registre / PRODUCED_DOC | Élément = ancrage métier download ACL | **PRODUCED_DOC** (D-ELEMENT 3) |
| 1 vs 2 ServiceTasks | 2 = séparabilité BPMN (analyse 04/08) | **2 tasks** (D-FLOWABLE 1) |
| HTTP store vs mémoire | Store HTTP → orphelins | **Mémoire** (D-HTTP-STORE 1) |
| Download par storageRef vs elementId | storageRef brut casse ACL | **elementId** (D-DOWNLOAD 1) |
| Playwright V1 | Coût browser | **Écarté** (D-ENGINE 1) |
| DOSSIER_MANAGER bypass download | Dilue ACL type | **Écarté** — grant ACL (A12) |
| 422 vs 404 template missing | Templates = 404 `TemplateNotFound` | **404** (A4 précisé) |

## User Stories & Use Cases

1. **En tant que** modélisateur BPMN, **je veux** un ServiceTask de génération
   qui résout le template ACTIVE et stocke le PDF, **afin que** le processus
   produise un document sans code custom.
2. **En tant que** modélisateur BPMN, **je veux** un ServiceTask d’attach qui
   crée un `PRODUCED_DOC` sur le dossier, **afin que** le document soit
   consultable hors instance Flowable.
3. **En tant que** `DOSSIER_MANAGER`, **je veux** générer un PDF via API pour
   un template donné et des variables, **afin de** valider un modèle sans
   démarrer un workflow.
4. **En tant que** `DOSSIER_VIEWER` avec ACL `VIEW` sur le type, **je veux**
   télécharger le contenu d’un élément `PRODUCED_DOC`, **afin de** consulter
   le document produit.
5. **En tant que** `TENANT_ADMIN` / `WORKFLOW_ADMIN`, **je veux** télécharger
   sans grant ACL individuel (bypass), **afin de** ops / support.
6. **En tant qu’** ops, **je veux** basculer filesystem ↔ S3 par config,
   **afin de** développer en local et déployer sur object store.

### Edge cases

- Template ACTIVE introuvable après cascade resolve → **404** ProblemDetail.
- `templateKey` / `documentType` blank → **400**.
- Variables Thymeleaf / expression invalide à l’évaluation → **400**.
- `variables` JSON null ou absent → traité comme `{}`.
- `generatedDocumentId` absent ou objet storage manquant à l’attach → échec
  task (exception métier / IllegalState).
- `dossierId` absent, mal formé (non-UUID) ou dossier inconnu → échec task.
- Download élément inconnu / `storageRef` cassé → **404**.
- Download type ≠ `PRODUCED_DOC` ou `storageRef` null → **409**.
- Utilisateur sans ACL (et sans bypass) → **403**.
- CSRF manquant sur `POST` generate → **403** ; anonyme → **401**.
- Plusieurs éléments peuvent partager le même `storageRef` V1 (pas de
  contrainte d’unicité storage — **A15**).

## Success Criteria
**How will we know when we're done?**

- [x] `GenerateDocumentUseCase` produit un PDF non vide (`%PDF`) depuis un
      template ACTIVE + variables.
- [x] `FilesystemStorageAdapter` et `S3StorageAdapter` implémentent
      `DocumentStoragePort` ; switch `app.storage.type`.
- [x] `documentGenerationTask` écrit `generatedDocumentId` ; test delegate
      (et smoke BPMN si pattern connecteurs réutilisable).
- [x] `attachGeneratedDocumentTask` persiste `dossier_element` type
      `PRODUCED_DOC` avec `storageRef` + label A3 ; contexte System.
- [x] `POST /api/documents/generate` renvoie `application/pdf` sans écrire
      dans le storage FS de test.
- [x] `GET /api/dossier-elements/{id}/content` : ACL VIEW OK ; sans ACL 403 ;
      bypass `TENANT_ADMIN` / `WORKFLOW_ADMIN` OK.
- [x] Liquibase `005-dossier-element-tables` (ou équivalent) appliqué.
- [x] `./mvnw verify` vert ; Jacoco instructions ≥ 80 % ; ArchUnit OK.
- [x] `npx ai-devkit lint --feature generation-documents` OK.

Preuve phase 7 (2026-08-04) : `./mvnw clean verify` → **353 / 0**,
Jacoco **87,20 %** ; SchemaIT 005 via `-Dtest=DossierElementSchemaIT` **2 / 0**.

## Constraints & Assumptions

- Java 25 · Spring Boot 4.x · hexagone mono-module · H2 MODE=PostgreSQL IT.
- Tenant = `TenantProperties` (décision n° 13).
- Templates = feature `templates-documents` (resolve ACTIVE cascade).
- ACL = `DossierTypeAcl` / `DossierPermission` / `DossierAccessPolicy`
  (décision n° 20).
- **A1** — Stack rendu : Thymeleaf → jsoup XHTML → Flying Saucer → OpenPDF.
- **A2** — `storageRef` = clé opaque préfixée tenant
  (`{tenantId}/{uuid}.pdf`).
- **A3** — Label attach : `Document généré — {activityName}`.
- **A4** — Erreurs `ProblemDetail` : resolve miss **404** ; validation /
  Thymeleaf **400** ; authz **401/403** ; mauvais type élément **409**.
- **A5** — Pas de table `generated_document` ; métadonnées = `dossier_element`.
- **A6** — IT avec filesystem ; S3 via client mockable ; Jacoco ≥ 80 %.
- **A7** — `API_SERVICE` non autorisé generate HTTP V1.
- **A8** — Polices TTF/OTF classpath uniquement (pas CDN / WOFF).
- **A9** — Tasks Flowable : `AccessContext.system("flowable-delegate")`
  (pattern `UpdateDossierStatusDelegate`).
- **A10** — `dossierId` process variable = UUID **string**
  (`UUID.fromString(...)`).
- **A11** — Pas d’HTTP POST/PATCH éléments V1 ; `AddDossierElementUseCase`
  pour task (+ tests).
- **A12** — Bypass download = `TENANT_ADMIN` \| `WORKFLOW_ADMIN` \| System ;
  pas `DOSSIER_MANAGER`.
- **A13** — `Content-Disposition: attachment; filename="<sanitized-label>.pdf"`.
- **A14** — Perf analyse (50–500 ms) = cible soft, pas gate CI.
- **A15** — Pas d’unicité `storage_ref` en base V1.

## Questions & Open Items

| # | Item | Disposition |
|---|---|---|
| Q1 | Scope V1 (Flowable + store + élément) | **Clos** — D-SCOPE 2 |
| Q2 | Stockage S3 + FS | **Clos** — D-STORE 1 |
| Q3 | Inclure DossierElement | **Clos** — D-ELEMENT 3 |
| Q4 | Périmètre types éléments | **Clos** — D-ELEMENT-SCOPE 1 |
| Q5 | Une vs deux ServiceTasks | **Clos** — D-FLOWABLE 1 |
| Q6 | Surface HTTP | **Clos** — D-HTTP 2 |
| Q7 | Authz | **Clos** — D-AUTHZ 3 + A12 |
| Q8 | HTTP attach ? | **Clos** — D-HTTP-ATTACH 1 |
| Q9 | Forme download | **Clos** — D-DOWNLOAD 1 |
| Q10 | Moteur PDF | **Clos** — D-ENGINE 1 |
| Q11 | HTTP store ? | **Clos** — D-HTTP-STORE 1 |
| Q12 | Noms variables Flowable | **Clos** — D-VARS 1 |
| Q13 | Preview | **Différé** — D-PREVIEW |
| Q14 | Playwright | **Différé** — non-goal |
| Q15 | Purge orphelins / §22 | **Différé** — TODO §22 |
| Q16 | M2M `API_SERVICE` | **Différé** — A7 |
| Q17 | Bypass admin download | **Clos stakeholder** — option 1 = A12 |
| Q18 | Codes HTTP resolve/type | **Clos stakeholder** — option 1 = A4 (404 / 409) |
| Q19 | API create élément | **Clos stakeholder** — option 1 = A11 (non) |
| Q20 | Unicité `storage_ref` | **Clos stakeholder** — option 1 = A15 |
| Q21 | Format `dossierId` | **Assumption** — A10 (UUID string, pattern workflow) |
| Q22 | Contexte Flowable | **Assumption** — A9 (`AccessContext.system`) |
| Q23 | Content-Disposition | **Assumption** — A13 |

### Décisions actées

| Id | Sujet | Choix |
|---|---|---|
| D-SCOPE | Tranche | Rendu + storage + 2 tasks Flowable |
| D-STORE | Object store | Port + S3 + filesystem ; pas MinIO domaine |
| D-ELEMENT | Éléments | `PRODUCED_DOC` inclus |
| D-ELEMENT-SCOPE | Types | PRODUCED_DOC seul |
| D-FLOWABLE | Tasks | generate puis attach |
| D-HTTP | REST | POST generate + GET content |
| D-AUTHZ | Rôles / ACL | Generate rôles ; download ACL VIEW + bypass admin |
| D-HTTP-ATTACH | Attach HTTP | Non |
| D-DOWNLOAD | GET | Par `dossierElementId` uniquement |
| D-ENGINE | PDF | Flying Saucer seul |
| D-HTTP-STORE | Store HTTP | Non (mémoire) |
| D-VARS | Variables | `documentType` / `documentVariant?` / `documentLocale?` / `dossierId` / `generatedDocumentId` |
| D-PREVIEW | Preview | Différé |

**Phase 1 close** (2026-08-03) — décisions Q1–Q12 ; assumptions A1–A8.
**Phase 2** — première passe docs + écarts ; **reprise stakeholder** :
Q17–Q20 confirmés explicitement (options 1) ; A9/A10/A13 restent
assumptions alignées code existant (non bloquantes).
**Phase 2 close** (2026-08-03) — statut `reviewed` ; A4/A11/A12/A15
validés stakeholder ; section Alternatives ; mapping HTTP↔Flowable.
**Phase 3 close** — design `reviewed` (DQ1–DQ5). **Phase 4** — plan
(`dev-planning`).

## Sources

- `analysis doc/07-generation-documents.md`, `08-templates-documents.md`,
  `04-dossiers.md` (§ DossierElement / AttachGeneratedDocumentTask),
  `01-architecture-hexagonale.md`, `12-autorisation-conformite.md`
- Décisions TODO n° 13, 17, 18, 20 ; TODO §22 (différé)
- `DEFERRED-FEATURES.md` §0 stockage + ordre features
- Features livrées : `templates-documents`, `dossiers` (`DossierAccessPolicy`),
  `workflow-engine` (`UpdateDossierStatusDelegate`)
- Code : `DossierAccessPolicy`, `UpdateDossierStatusDelegate`
- Mémoire : `generation-documents V1 scope decisions`
- Clarifications lifecycle 2026-08-03 (D-SCOPE…D-VARS + Phase 2)
