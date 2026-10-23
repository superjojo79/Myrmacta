---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
reviewed: 2026-08-03
created: 2026-08-03
updated: 2026-08-03
---

# Requirements & Problem Understanding

## Problem Statement
**What problem are we solving?**

FlowForge authentifie déjà les utilisateurs (BFF OIDC) et applique une ACL
**au niveau type de dossier** (décision n° 20). En revanche, le contrôle de
**rôle** reste ad hoc : guards manuels dans les controllers / use cases, pas de
`@PreAuthorize` / method security, et le point 19 (liste des rôles) n’est pas
clos. Côté conformité légère, les `FieldDefinition` n’ont aucun moyen de
signaler un champ sensible (TODO §23).

- **Affectés :** admins tenant, managers / viewers dossiers, ops IdP (rôles
  realm), développeurs features aval (`frontend`, exports, connecteurs),
  reviewers sécurité.
- **Situation actuelle :** `AccessContext`, `DossierAccessPolicy`,
  `dossier_type_acl`, mapping `realm_access.roles` → `ROLE_*` livrés ;
  `TenantAdminGuard` infra HTTP ; checks `hasRole("TENANT_ADMIN")` dupliqués
  dans les use cases ; `WorkflowTypeConfigController.requireWorkflowAdmin`
  manuel ; aucun `@EnableMethodSecurity`. Analyse :
  `docs/specs/12-autorisation-conformite.md` + TODO §19 / §23.
- **UX :** pas d’écrans Vue — contrat REST + métadonnée `sensitive` pour la
  future SPA / exports ; pas de masquage à l’affichage dans cette tranche.

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- **Clôturer le point 19 (V1)** : valider la liste définitive des cinq rôles
  `TENANT_ADMIN`, `WORKFLOW_ADMIN`, `DOSSIER_MANAGER`, `DOSSIER_VIEWER`,
  `API_SERVICE`. Restriction par sous-ensemble de `DossierType` = ACL type
  (décision n° 20), déjà livrée. Mapping IdP → `ROLE_*` = inchangé
  (`authentification`).
- Activer **`@EnableMethodSecurity`** et annoter les controllers selon la
  **surface livrable** ci-dessous (source d’acceptation ; détail SpEL dans
  design).
- Introduire un **`TenantAdminGuard` domaine** (pur Java) utilisé par les use
  cases admin **types + ACL** (create/update type, grant/revoke) ; défense en
  profondeur avec `@PreAuthorize` HTTP. **Supprimer** le guard infra HTTP
  `infrastructure.web.dossier.TenantAdminGuard`.
- Remplacer `WorkflowTypeConfigController.requireWorkflowAdmin` par
  `@PreAuthorize` (`TENANT_ADMIN` **ou** `WORKFLOW_ADMIN`). Les use cases
  workflow-config restent sans check rôle (pas d’`AccessContext` aujourd’hui) —
  une seule garde HTTP pour cette surface.
- Conserver **`DossierAccessPolicy` + `AccessContext`** comme source de vérité
  pour VIEW/EDIT type (bypass `TENANT_ADMIN` / `WORKFLOW_ADMIN`, System OK,
  Service refus) sur dossiers + start/history/complete workflow.
- Ajouter **`FieldDefinition.sensitive`** (`boolean`, défaut `false`) :
  persisté dans le JSON `fieldDefinitions`, accepté en create/update type
  (**TENANT_ADMIN** uniquement), renvoyé dans les réponses types. **Pas de
  masquage** des valeurs dossier en lecture (TODO §23 partiel).
- Assurer **403 `ProblemDetail`** cohérent pour refus rôle (method security) et
  refus ACL / guard domaine (`com.flowforge.domain…AccessDeniedException`).
- Tests : unitaires guard / policy / `sensitive` ; MockMvc `@PreAuthorize` ;
  ArchUnit / Jacoco non régressés.

### Surface livrable (acceptation authz)

| Surface | Authz attendue |
|---|---|
| `POST` / `PATCH /api/dossier-types` | `@PreAuthorize` `TENANT_ADMIN` + guard domaine |
| `POST` / `DELETE /api/dossier-types/{code}/acl…` | `@PreAuthorize` `TENANT_ADMIN` + guard domaine |
| `GET /api/dossier-types` (+ `/{code}`) | Session BFF ; filtrage / policy use case (inchangé) |
| `POST` / `GET` / `PATCH /api/dossiers/**` | Session BFF ; **`DossierAccessPolicy`** (pas de `@PreAuthorize` rôle métier) |
| `POST` / `PATCH /api/workflow-type-configs` | `@PreAuthorize` `hasAnyRole(TENANT_ADMIN, WORKFLOW_ADMIN)` ; retire check manuel |
| `GET /api/workflow-type-configs` (+ `/{key}`) | Session BFF (inchangé) |
| `POST /api/dossiers/{id}/workflows`, `GET …/workflows`, `POST /api/workflow-tasks/…/complete` | Session BFF + **policy dossier** (EDIT/VIEW) ; **pas** de `@PreAuthorize` rôle |
| `FieldDefinition.sensitive` | Create/update/get type ; défaut `false` ; GET dossier **en clair** |

### Secondary goals

- Documenter la matrice rôles pour `frontend` et la checklist IdP (roles
  realm).
- Ancrer la contrainte **stockage objet abstrait** (cible S3, adaptateur FS
  possible — **pas** de couplage MinIO) pour les features aval ; **doc seule**,
  aucun port code dans cette tranche.
- Mettre à jour le suivi TODO : §19 clos ; §23 partiel (flag livré, masquage /
  exports différés).

### Non-goals

- Rétention par `DossierType` (TODO §21) et purge PostgreSQL / object store
  (TODO §22).
- Masquage / redaction des valeurs `sensitive` à la lecture dossier ; exclusion
  exports ; UI d’administration conformité.
- Implémentation d’un `ObjectStoragePort` / adaptateurs S3 ou FS.
- Chaîne M2M `client_credentials` / webhooks HMAC (features
  `authentification` follow-up / `connecteurs`).
- Nouveaux rôles au-delà des cinq V1 ; ACL par instance de dossier.
- Guard domaine « workflow admin » / passage d’`AccessContext` dans les use
  cases workflow-config (garde HTTP seule).
- `@PreAuthorize` distinguant `DOSSIER_MANAGER` vs `DOSSIER_VIEWER` sur les
  endpoints dossier (la distinction reste **grant EDIT vs VIEW**).
- OpenAPI formel ; Testcontainers ; écrans Vue.
- Refonte générique d’un framework permissions multi-ressources.

## User Stories & Use Cases
**How will users interact with the solution?**

- En tant qu’**ops / sécurité**, je configure dans l’IdP exactement les cinq
  rôles V1 et je sais qu’ils mappent vers `ROLE_*` Spring.
- En tant que **TENANT_ADMIN**, j’appelle les endpoints d’admin types / ACL ;
  un utilisateur sans ce rôle reçoit **403** au bord HTTP **et** au use case.
- En tant que **DOSSIER_MANAGER** avec grant EDIT, je crée / instruit des
  dossiers ; sans grant, **403** via `DossierAccessPolicy`.
- En tant que **DOSSIER_VIEWER** avec grant VIEW, je lis ; sans grant EDIT je
  ne mute pas — refus **policy** (pas d’annotation rôle MANAGER/VIEWER sur
  l’endpoint).
- En tant que **WORKFLOW_ADMIN**, je bypass l’ACL type (déjà) et je mute les
  `workflow-type-configs` ; je **ne** remplace **pas** `TENANT_ADMIN` pour le
  CRUD types / ACL (403).
- En tant qu’**admin configurant un type**, je marque certains
  `FieldDefinition` avec `sensitive: true` pour documenter le caractère
  sensible ; la valeur reste visible en clair dans `GET` dossier (V1).
- En tant que **développeur aval**, je m’appuie sur la doc : stockage futur =
  port abstrait S3/FS, pas MinIO en dur.

### Scénarios clés

1. `POST /api/dossier-types` sans `ROLE_TENANT_ADMIN` → **403** (method
   security), même si le body est valide.
2. `TENANT_ADMIN` crée un type avec un champ `sensitive: true` → persisté ;
   `GET` type renvoie le flag.
3. `DOSSIER_MANAGER` sans grant → refus ACL sur create/get dossier (régression
   policy OK).
4. `WORKFLOW_ADMIN` sur `POST /api/dossier-types` → **403** ; lecture dossier
   (bypass policy) → OK ; `POST /api/workflow-type-configs` → OK.
5. JSON type historique sans clé `sensitive` → désérialisé comme `false`.
6. `DOSSIER_VIEWER` (authentifié) sur `PATCH …/status` sans grant EDIT →
   **403** policy (pas 401).
7. `./mvnw test` vert ; Jacoco ≥ 80 % ; ArchUnit OK.

### Edge cases

- Method security vs `AccessDeniedException` **domaine** : les deux → HTTP 403
  ProblemDetail (pas 401 si session authentifiée). Attention au homonyme
  Spring `AccessDeniedException` — ne pas confondre dans les handlers.
- `API_SERVICE` : toujours refus accès dossier (policy) ; pas de nouveaux
  endpoints connecteurs ici.
- Champ `sensitive` absent / null en entrée API → `false`.
- Start / complete / history workflow : aucune annotation de rôle ; EDIT/VIEW
  policy seule.

## Success Criteria
**How will we know when we're done?**

- [x] Point 19 documenté comme **clos V1** (cinq rôles + renvoi ACL type /
  mapping existant).
- [x] `@EnableMethodSecurity` actif ; **surface livrable** respectée
  (annotations présentes / absentes comme tabulaire).
- [x] `TenantAdminGuard` domaine branché sur use cases types + ACL ; classe
  infra HTTP **supprimée**.
- [x] Check manuel `requireWorkflowAdmin` **supprimé** au profit de
  `@PreAuthorize`.
- [x] `FieldDefinition.sensitive` (défaut false) bout-en-bout (domaine, JSON
  JPA, API) ; seul un `TENANT_ADMIN` peut l’écrire via API types.
- [x] Aucun masquage lecture dossier basé sur `sensitive` (assert test).
- [x] MockMvc : rôle insuffisant → 403 ProblemDetail ; admin OK ; régressions
  dossiers / workflow / auth.
- [x] Contrainte stockage S3/FS (anti-MinIO) documentée (requirements + design).
- [x] `./mvnw test` vert ; ArchUnit ; Jacoco ≥ 80 %.
- [x] Docs requirements / design / testing / planning alignés ; TODO §19 / §23
  mis à jour.

**Performance benchmarks :** N/A — chemins authz déjà présents ; pas d’IO
stockage nouveau.

## Constraints & Assumptions
**What limitations do we need to work within?**

### Technical constraints

- Java 25 · Spring Boot 4.1 · Security 7 · BFF session déjà livré.
- Domaine pur (décision n° 17) : pas d’annotations Spring dans `domain.*` ;
  le guard domaine est du Java pur.
- `fieldDefinitions` déjà en JSON/JSONB : pas de changeset colonne SQL
  obligatoire pour `sensitive` (évolution du document JSON).
- Hexagonal : method security au bord HTTP seulement.
- Homonymie `AccessDeniedException` domaine vs Spring : handlers existants
  ciblent le type **domaine** ; le bridging method security doit mapper le
  type Spring (ou équivalent Boot 4 / Security 7) vers 403 ProblemDetail.

### Business / product

- Liste des rôles V1 figée ; pas de custom roles par tenant dans cette
  tranche.
- Conformité « légère » = classification documentaire des champs, pas
  moteur RGPD complet.

### Time / rollout

- Branche cloud `cursor/autorisation-conformite-f54c` / PR ; pas de feature
  flag.
- Smoke IdP réel (cinq rôles realm) = ops, hors CI.
- Traçage `task` CLI indisponible sur cet environnement.

### Assumptions

- Les cinq noms de rôles realm Keycloak correspondent exactement aux chaînes
  sans préfixe `ROLE_` déjà utilisées dans le code.
- Les règles métier dossiers / workflow (policy) restent valides ; cette
  tranche **ajoute** method security sur les surfaces admin et uniformise les
  guards, sans changer la sémantique ACL type.
- Le port objet S3/FS sera introduit quand `DossierElement` / génération
  documents arriveront — hors code ici.

## Questions & Open Items
**What do we still need to clarify?**

### Tranché (lifecycle 2026-08-03)

| # | Décision | Choix |
|---|---|---|
| Q1 | Périmètre tranche | Authz V1 + `sensitive` métadonnée ; §21–22 hors scope |
| Q2 | Point 19 rôles | Cinq rôles de référence V1 |
| Q3 | Placement `@PreAuthorize` | Controllers uniquement |
| Q4 | Comportement `sensitive` | Métadonnée seule, pas de masquage lecture |
| Q5 | Checks admin types/ACL | Guard domaine factorisé + annotations HTTP |
| Q6 | Port stockage | Doc seule (S3 cible, FS possible) ; pas de skeleton |
| Q7 | Start/complete/history workflow | **Pas** de `@PreAuthorize` rôle — policy seule |
| Q8 | Workflow type-config | `@PreAuthorize` admin\|workflow-admin ; **pas** de guard domaine |
| Q9 | MANAGER vs VIEWER sur API dossier | Distinction via **ACL EDIT/VIEW**, pas via rôle HTTP |

### Différé / hors tranche

- Masquage / exports champs `sensitive`.
- Rétention / purge (§21–22).
- `ObjectStoragePort` + adaptateurs.
- Validation empirique des rôles avec premiers clients réels (peut rouvrir
  point 19 plus tard si besoin produit).
- Guard domaine workflow-admin si les use cases config gagnent un
  `AccessContext`.

### Bloquant ouvert

**Aucun** — prêt pour revue design (phase 3).

## Alternatives brainstormées

| Approche | Trade-off | Verdict |
|---|---|---|
| Authz only (sans `sensitive`) | Plus petit, laisse §23 intact | Écarté — option 2 retenue |
| Authz + masquage lecture | Sémantique UX sans SPA | Écarté — meta seule |
| Authz + rétention/purge | Bloque sur storage / éléments | Écarté |
| `@PreAuthorize` aussi sur use cases | Couplage SpEL / domaine | Écarté |
| Controller seul (sans guard domaine) | Moins de défense en profondeur | Écarté — Q5 option 3 |
| Double garde non factorisée | Duplication `requireTenantAdmin` | Écarté |
| Skeleton `ObjectStoragePort` | Code mort | Écarté — doc seule |
| `@PreAuthorize` sur start/complete | Rôle unique inadapté (ACL type) | Écarté — Q7 |
| Fusion WORKFLOW→TENANT | Moins de finesse BPMN | Écarté — 5 rôles |

## Clarifications actées

1. **Périmètre :** option 2 — method security + guard domaine types/ACL +
   `sensitive` meta ; §21–22 et port storage hors code.
2. **Rôles V1 :** cinq noms ; mapping `ROLE_*` existant ; ACL type = sous-ensemble
   types.
3. **Surface authz :** tabulaire ci-dessus (Q7–Q9 inclus).
4. **`sensitive` :** écrit via API types (TENANT_ADMIN) ; lu sur types ; valeurs
   dossier non redactées.
5. **403 :** method security + domaine → ProblemDetail ; 401 réservé à
   l’anonyme `/api/**`.
6. **Stockage :** contrainte S3/FS anti-MinIO documentée uniquement.

## Review notes (phase 2)

| Point | Résolution |
|---|---|
| Conformité template README | Sections OK + surface + rollout + alternatives + clarifications |
| Story VIEWER « controller + policy » | Corrigée → **policy EDIT** seule |
| Matrice workflow floue (design draft) | **Q7/Q8** actés dans requirements (source d’acceptation) |
| Asymétrie guard workflow-config | Acceptée — HTTP only (pas d’AccessContext use case) |
| Qui écrit `sensitive` | **TENANT_ADMIN** via create/update type |
| Homonymie AccessDeniedException | Contrainte technique + critère 403 |
| Questions bloquantes | **Aucune** |
| Alignement design/testing/planning | À propager en phase 3 (design) ; testing scénario 4/6 déjà proches |

**Prochaine phase :** `dev-design` (phase 3 — Review Design).
