---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: draft
created: 2026-08-03
updated: 2026-08-03
stakeholder_decisions: 2026-08-03
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
  (`DOSSIER_MANAGER` / `VIEWER`), `TENANT_ADMIN` / `WORKFLOW_ADMIN`, feature
  aval `frontend` / visualisation.
- **Situation actuelle :** `DocumentTemplate` + `TemplateResolver` livrés ;
  packages `infrastructure/pdf` et `infrastructure/storage` absents ; pas de
  table `dossier_element` ; analyse `07-generation-documents.md` +
  `08-templates-documents.md` (§ Flowable) + décision n° 18 ; contrainte
  DEFERRED §0 (port S3/FS, pas MinIO en dur).
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
     (**D-ELEMENT = 3**, **D-ELEMENT-SCOPE = 1**)
- **Contrat variables** (**D-VARS = 1**) : entrée `documentType` (templateKey),
  `documentVariant` / `documentLocale` optionnels ; `dossierId` requis pour
  attach ; sortie `generatedDocumentId` (= `storageRef`).
- **API HTTP** (**D-HTTP = 2**) :
  - `POST /api/documents/generate` — rendu **en mémoire** uniquement
    (**D-HTTP-STORE = 1**, **D-HTTP-ATTACH = 1**) → corps PDF
  - `GET /api/dossier-elements/{id}/content` — download octets via
    `storageRef` (**D-DOWNLOAD = 1**)
- **Authz** (**D-AUTHZ = 3**) :
  - Generate HTTP : `TENANT_ADMIN` \| `WORKFLOW_ADMIN` \| `DOSSIER_MANAGER`
  - Download : ACL type dossier `VIEW` ou `EDIT` sur le dossier porteur de
    l’élément (`AccessContext` existant)
- Tests unitaires + MockMvc/IT H2 + FS storage ; ArchUnit ; Jacoco ≥ 80 %.

### Surface API (acceptation)

| Méthode | Path | Authz / notes |
|---|---|---|
| POST | `/api/documents/generate` | Rôles generate — body `templateKey`, `variantKey?`, `locale?`, `variables` ; réponse `application/pdf` ; **pas** de store / attach |
| GET | `/api/dossier-elements/{id}/content` | ACL `VIEW`/`EDIT` sur le type du dossier de l’élément ; `Content-Type: application/pdf` |

### Secondary goals

- Port `PdfRenderer` stable pour un futur adaptateur Playwright (hors V1).
- Config `app.storage.type=filesystem|s3` + propriétés S3 (endpoint, bucket,
  credentials via env).
- Label élément : `Document généré — {activityName}` (**A3**).

### Non-goals (explicitly out of scope)

- Preview HTML / PDF inline (D-PREVIEW — différé `frontend` / lot suivant).
- Adaptateur Playwright / CSS3 avancé.
- Upload `ATTACHMENT`, types `INFORMATION` / `NOTE` / `LINK`, liste paginée
  des éléments.
- Table registre `generated_document` séparée (**A5**).
- Purge / TTL / droit à l’effacement cohérent (TODO §22).
- `API_SERVICE` sur generate HTTP (**A7** — follow-up M2M).
- OpenAPI formel, rate-limit PDF, antivirus.
- Couplage domaine à MinIO (nom, SDK propriétaire) — interdit.

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
4. **En tant que** `DOSSIER_VIEWER` avec ACL `VIEW`, **je veux** télécharger
   le contenu d’un élément `PRODUCED_DOC`, **afin de** consulter le document
   produit.
5. **En tant qu’** ops, **je veux** basculer filesystem ↔ S3 par config,
   **afin de** développer en local et déployer sur object store.

### Edge cases

- Template ACTIVE introuvable après cascade resolve → erreur métier 404/422.
- Variables Thymeleaf manquantes / expression invalide à l’évaluation → 400.
- `generatedDocumentId` absent ou storage manquant à l’attach → erreur task.
- `dossierId` absent / dossier inconnu à l’attach → erreur task.
- Download élément non-`PRODUCED_DOC` ou sans `storageRef` → 404/409.
- Utilisateur sans ACL sur le type → 403.
- CSRF manquant sur `POST` generate (chaîne BFF) → 403.

## Success Criteria
**How will we know when we're done?**

- [ ] `GenerateDocumentUseCase` produit un PDF non vide depuis un template
      ACTIVE + variables.
- [ ] `FilesystemStorageAdapter` et `S3StorageAdapter` implémentent
      `DocumentStoragePort` ; switch config.
- [ ] `documentGenerationTask` écrit `generatedDocumentId` ; smoke BPMN ou
      test delegate.
- [ ] `attachGeneratedDocumentTask` persiste `dossier_element` type
      `PRODUCED_DOC` avec `storageRef`.
- [ ] `POST /api/documents/generate` renvoie `application/pdf` sans écrire
      dans le storage.
- [ ] `GET /api/dossier-elements/{id}/content` respecte l’ACL type.
- [ ] Liquibase `005-dossier-element-tables` (ou équivalent) appliqué.
- [ ] `./mvnw verify` vert ; Jacoco instructions ≥ 80 % ; ArchUnit OK.
- [ ] `npx ai-devkit lint --feature generation-documents` OK.

## Constraints & Assumptions

- Java 25 · Spring Boot 4.x · hexagone mono-module · H2 MODE=PostgreSQL IT.
- Tenant = `TenantProperties` (décision n° 13).
- Templates = feature `templates-documents` (resolve ACTIVE cascade).
- ACL = `DossierTypeAcl` / `DossierPermission` existants (décision n° 20).
- **A1** — Stack rendu : Thymeleaf → jsoup XHTML → Flying Saucer → OpenPDF.
- **A2** — `storageRef` = clé opaque préfixée tenant
  (`{tenantId}/{uuid}.pdf`).
- **A3** — Label attach : `Document généré — {activityName}`.
- **A4** — Erreurs `ProblemDetail` (400/403/404/409/422 selon cas).
- **A5** — Pas de table `generated_document` ; métadonnées = `dossier_element`.
- **A6** — IT avec filesystem ; S3 testé via adaptateur mockable / IT légère ;
  Jacoco ≥ 80 %.
- **A7** — `API_SERVICE` non autorisé generate HTTP V1.
- **A8** — Polices TTF/OTF classpath uniquement (pas CDN / WOFF).

## Questions & Open Items

| # | Item | Disposition |
|---|---|---|
| Q1 | Scope V1 (Flowable + store + élément) | **Clos** — D-SCOPE 2 |
| Q2 | Stockage S3 + FS | **Clos** — D-STORE 1 |
| Q3 | Inclure DossierElement | **Clos** — D-ELEMENT 3 |
| Q4 | Périmètre types éléments | **Clos** — D-ELEMENT-SCOPE 1 (PRODUCED_DOC) |
| Q5 | Une vs deux ServiceTasks | **Clos** — D-FLOWABLE 1 |
| Q6 | Surface HTTP | **Clos** — D-HTTP 2 |
| Q7 | Authz | **Clos** — D-AUTHZ 3 |
| Q8 | HTTP attach ? | **Clos** — D-HTTP-ATTACH 1 (non) |
| Q9 | Forme download | **Clos** — D-DOWNLOAD 1 |
| Q10 | Moteur PDF | **Clos** — D-ENGINE 1 |
| Q11 | HTTP store ? | **Clos** — D-HTTP-STORE 1 (non) |
| Q12 | Noms variables Flowable | **Clos** — D-VARS 1 |
| Q13 | Preview | **Différé** — D-PREVIEW |
| Q14 | Playwright | **Différé** — non-goal |
| Q15 | Purge orphelins / §22 | **Différé** — TODO §22 |
| Q16 | M2M `API_SERVICE` | **Différé** — A7 |

### Décisions actées

| Id | Sujet | Choix |
|---|---|---|
| D-SCOPE | Tranche | Rendu + storage + 2 tasks Flowable |
| D-STORE | Object store | Port + S3 + filesystem ; pas MinIO domaine |
| D-ELEMENT | Éléments | `PRODUCED_DOC` inclus |
| D-ELEMENT-SCOPE | Types | PRODUCED_DOC seul |
| D-FLOWABLE | Tasks | generate puis attach |
| D-HTTP | REST | POST generate + GET content |
| D-AUTHZ | Rôles / ACL | Generate rôles ; download ACL VIEW/EDIT |
| D-HTTP-ATTACH | Attach HTTP | Non |
| D-DOWNLOAD | GET | Par `dossierElementId` uniquement |
| D-ENGINE | PDF | Flying Saucer seul |
| D-HTTP-STORE | Store HTTP | Non (mémoire) |
| D-VARS | Variables | `documentType` / `documentVariant?` / `documentLocale?` / `dossierId` / `generatedDocumentId` |
| D-PREVIEW | Preview | Différé |

**Phase 1 close** (2026-08-03) — statut `draft` ; décisions Q1–Q12
confirmées stakeholder ; assumptions A1–A8 acceptées.
**Phase 2** — revue requirements (`dev-requirements` review).

## Sources

- `analysis doc/07-generation-documents.md`, `08-templates-documents.md`,
  `04-dossiers.md` (§ DossierElement / AttachGeneratedDocumentTask),
  `01-architecture-hexagonale.md`, `12-autorisation-conformite.md`
- Décisions TODO n° 13, 17, 18, 20 ; TODO §22 (différé)
- `DEFERRED-FEATURES.md` §0 stockage + ordre features
- Feature livrée : `templates-documents` (resolve, admin, checker)
- Clarifications lifecycle 2026-08-03 (D-SCOPE…D-VARS)
