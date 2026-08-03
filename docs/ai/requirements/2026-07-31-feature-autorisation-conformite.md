---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: draft
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
  dans les use cases ; aucun `@EnableMethodSecurity`. Analyse :
  `analysis doc/12-autorisation-conformite.md` + TODO §19 / §23.
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
- Activer **`@EnableMethodSecurity`** et annoter les **controllers** avec
  `@PreAuthorize` selon la matrice rôles × endpoints (voir design).
- Introduire un **`TenantAdminGuard` domaine** (pur Java) utilisé par les use
  cases admin (CRUD types, grant/revoke ACL) ; conserver la défense en
  profondeur avec les annotations HTTP. Retirer / ne plus s’appuyer sur le
  guard infra HTTP une fois les annotations en place.
- Conserver **`DossierAccessPolicy` + `AccessContext`** comme source de vérité
  pour VIEW/EDIT type (bypass `TENANT_ADMIN` / `WORKFLOW_ADMIN`, System OK,
  Service refus).
- Ajouter **`FieldDefinition.sensitive`** (`boolean`, défaut `false`) :
  persisté dans le JSON `fieldDefinitions`, accepté en create/update type,
  renvoyé dans les réponses types. **Pas de masquage** des valeurs dossier en
  lecture dans cette tranche (TODO §23 partiel — documentation seule).
- Assurer **403 `ProblemDetail`** cohérent pour refus rôle (method security) et
  refus ACL domaine (`AccessDeniedException`).
- Tests : unitaires guard / policy / sérialisation `sensitive` ; MockMvc
  `@PreAuthorize` (rôle insuffisant → 403) ; suite ArchUnit / Jacoco non
  régressée.

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
- OpenAPI formel ; Testcontainers ; écrans Vue.
- Refonte générique d’un framework permissions multi-ressources.

## User Stories & Use Cases
**How will users interact with the solution?**

- En tant qu’**ops / sécurité**, je configure dans l’IdP exactement les cinq
  rôles V1 et je sais qu’ils mappent vers `ROLE_*` Spring.
- En tant que **TENANT_ADMIN**, j’appelle les endpoints d’admin types / ACL ;
  un utilisateur sans ce rôle reçoit **403** au bord HTTP **et** au use case.
- En tant que **DOSSIER_MANAGER** avec grant EDIT, je crée / instruit des
  dossiers ; sans grant, **403** via `DossierAccessPolicy` (inchangé).
- En tant que **DOSSIER_VIEWER**, je lis selon VIEW ; je ne peux pas muter
  (controller + policy).
- En tant que **WORKFLOW_ADMIN**, je bypass l’ACL type (déjà) et j’accède aux
  endpoints workflow admin annotés ; je ne remplace pas `TENANT_ADMIN` pour le
  CRUD types / ACL.
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
4. `WORKFLOW_ADMIN` sur endpoint admin type → **403** ; sur bypass lecture
   dossier → OK (policy).
5. JSON type historique sans clé `sensitive` → désérialisé comme `false`.
6. `./mvnw test` vert ; Jacoco ≥ 80 % ; ArchUnit OK.

### Edge cases

- Method security vs `AccessDeniedException` domaine : les deux → HTTP 403
  ProblemDetail (pas 401 si session authentifiée).
- `API_SERVICE` : toujours refus accès dossier (policy) ; pas de nouveaux
  endpoints connecteurs ici.
- Champ `sensitive` absent / null en entrée API → `false`.

## Success Criteria
**How will we know when we're done?**

- [ ] Point 19 documenté comme **clos V1** (cinq rôles + renvoi ACL type / mapping
  existant).
- [ ] `@EnableMethodSecurity` actif ; controllers métier annotés selon matrice
  design.
- [ ] `TenantAdminGuard` domaine branché sur use cases admin ; guard infra HTTP
  retiré ou réduit à néant.
- [ ] `FieldDefinition.sensitive` (défaut false) bout-en-bout (domaine, JSON
  JPA, API).
- [ ] Aucun masquage lecture dossier basé sur `sensitive`.
- [ ] MockMvc : rôle insuffisant → 403 ; admin OK ; régressions dossiers /
  workflow / auth.
- [ ] Contrainte stockage S3/FS (anti-MinIO) documentée (requirements + design).
- [ ] `./mvnw test` vert ; ArchUnit ; Jacoco ≥ 80 %.
- [ ] Docs requirements / design / testing / planning alignés ; TODO §19 / §23
  mis à jour.

**Performance benchmarks :** N/A — chemins authz déjà présents ; pas d’IO
stockage nouveau.

## Constraints & Assumptions
**What limitations do we need to work within?**

### Technical constraints

- Java 25 · Spring Boot 4.1 · Security 7 · BFF session déjà livré.
- Domaine pur (décision n° 17) : pas d’annotations Spring dans
  `domain.*` sauf ce qui est déjà exclu par ArchUnit ; le guard domaine est
  du Java pur.
- `fieldDefinitions` déjà en JSON/JSONB : pas de changeset colonne SQL
  obligatoire pour `sensitive` (évolution du document JSON).
- Hexagonal : method security au bord HTTP seulement.

### Business / product

- Liste des rôles V1 figée ; pas de custom roles par tenant dans cette
  tranche.
- Conformité « légère » = classification documentaire des champs, pas
  moteur RGPD complet.

### Assumptions

- Les cinq noms de rôles realm Keycloak correspondent exactement aux chaînes
  sans préfixe `ROLE_` déjà utilisées dans le code.
- Les endpoints workflow existants restent sous les mêmes règles métier ;
  on ajoute surtout des annotations cohérentes.
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
| Q5 | Checks admin use case | Guard domaine factorisé + annotations HTTP |
| Q6 | Port stockage | Doc seule (S3 cible, FS possible) ; pas de skeleton |

### Différé / hors tranche

- Masquage / exports champs `sensitive`.
- Rétention / purge (§21–22).
- `ObjectStoragePort` + adaptateurs.
- Validation empirique des rôles avec premiers clients réels (peut rouvrir
  point 19 plus tard si besoin produit).
