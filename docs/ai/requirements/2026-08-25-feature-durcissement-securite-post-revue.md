---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: draft
created: 2026-08-25
updated: 2026-08-25
feature: durcissement-securite-post-revue
phase1: 2026-08-25
todo_ref: TODO.md §40–44 · FILE-ATTENTE.md · REVUE-ALIGNEMENT-SECURITE.md
---

# Requirements & Problem Understanding — `durcissement-securite-post-revue`

## Phase 1: New Requirement

**Statut : brouillon.** Phase 2 n'est pas ouverte. D-40 = **B** / **B2**,
D-41 = **A** / **S**, D-42 = **L**. D-43 et D-44 restent ouverts. Une
ébauche de code existe déjà sur le working tree (hors phase) : elle
coupe SpEL (proche de A / B1) et **diverge** de B2 ; elle sera réalignée
ou revertée après Phase 2–4.

Les autres fichiers `docs/ai/{design,planning,testing,implementation,deployment,monitoring}/`
de cette feature ont été remplis trop tôt. Ils sont **hors phase** et
non opposables.

## Problem Statement

Cinq findings de la revue post-implémentation 2026-08-25
([REVUE-ALIGNEMENT-SECURITE.md](../../../REVUE-ALIGNEMENT-SECURITE.md) §4,
TODO.md §40–44) restent ouverts. Le code suit l'hexagone et la décision 13,
mais la surface d'attaque SaaS n'est pas au niveau visé :

1. **SSTI (S-1 / §40)** — `ThymeleafHtmlTemplateProcessor` utilise
   `SpringTemplateEngine` (SpringEL). Un `TENANT_ADMIN` peut faire
   évaluer `${T(java.lang.Runtime)…}` via generate / live preview.
   L'iframe SPA `sandbox=""` ne couvre que le XSS navigateur.
2. **GRANT PG (S-2 / §41)** — `search_path` n'est pas une barrière. Un
   seul rôle PG (Compose : superuser de la base `flowforge`) peut lire
   les autres schémas du cluster. Combiné au point 40 : RCE sur une
   instance → données des autres tenants (chaîne Critical).
3. **Actuator (S-3 / §42)** — matcher Security Order 0 `permitAll` sur le
   port management ; pas de `management.server.address` ; défaut **8081**
   en conflit avec Keycloak local.
4. **Secrets / HMAC M2M (S-4 / §43)** — fallback `${DB_PASSWORD:flowforge}` ;
   HMAC HS256 optionnel accepte des JWT `API_SERVICE` hors profil local.
5. **SSRF TOCTOU (S-5 / §44)** — `OutboundUrlGuard` résout le DNS puis le
   client HTTP se reconnecte sans pin IP (rebinding).

**Hors feature (déjà acté par FILE-ATTENTE / revue, pas un D-* de cette
Phase 1) :** TODO §45 (clos PR #75), TODO §46 (ArchUnit `application`),
reliquats S-6…S-10, DMN, connecteur `http-rest` en rôle SERVICE.

## Goals & Objectives

Objectifs **candidats** (dépendent des D-* encore ouverts) :

1. Restreindre SpEL dans les templates stockés par **allowlist B2**
   (D-40 = B, D-40-ALLOW = B2) : modèle + `#vars` + `#dates` / `#numbers` /
   `#strings` / `#lists` ; refus `T()`, `new`, `#beans`, request/servlet.
2. Isolation SQL par **rôle PG LOGIN par schéma tenant** (D-41 = A,
   D-41-SCOPE = S) : init Compose `tenant_dev` + runbook ops + IT
   Testcontainers. Pas de CLI/orchestrateur tenant.
3. Actuator bound **localhost**, port management défaut **8090** (Keycloak
   reste 8081), `permitAll` conservé (D-42 = L / DD-MGMT-SEC).
4. Empêcher un boot prod avec secrets de démo ou HMAC M2M, selon D-43.
5. Empêcher le rebinding DNS sur les sorties HTTP déjà gardées, selon D-44.

### Non-goals

- Activer `http-rest` SERVICE, DMN, Kafka, AV, headers CSP/HSTS, rate-limit
  distribué, ArchUnit `application` (§46).
- Database-par-tenant (D-41 ≠ B).
- Proxy egress allowlist **sauf si D-44 choisit cette option**.
- Changer le modèle « une instance = un tenant » (décision 13).
- Outillage CLI / orchestrateur de provisioning tenant (hors file produit ;
  D-41-SCOPE = S).

## User Stories & Use Cases

- En tant qu'**ops SaaS**, je ne veux pas qu'un `TENANT_ADMIN` compromis
  obtienne une RCE JVM **et** lise `tenant_b` via le même user PG.
- En tant que **dev local**, je veux que Keycloak et l'actuator ne se
  marchent pas dessus, et que le profil de démo reste utilisable.
- En tant que **prod**, je ne veux pas qu'un oubli de secret (`flowforge`,
  HMAC M2M) laisse l'instance démarrer.

Cas limites restants : volume PG Compose existant (init SQL non rejouée) ;
scrape Prometheus hors loopback seulement si ops surcharge
`MANAGEMENT_ADDRESS` (D-42 = L, défaut `127.0.0.1`). `#dates` / `#numbers`
/ `#strings` / `#lists` sont **dans** l'allowlist (D-40-ALLOW = B2) ;
`T()` / `#beans` hors.

## Success Criteria

Critères **candidats** — à confirmer / ajuster en Phase 2 une fois les
D-* tranchés :

- [ ] **T-SSTI** (D-40 = B, D-40-ALLOW = B2) : `${T(java.lang.Runtime)}` et
      `#beans` → exception ou texte non évalué ; `th:text="${title}"`,
      `#vars`, `#dates` / `#numbers` / `#strings` / `#lists` évalués.
- [ ] **T-GRANT** (D-41 = A) : le rôle d'application ne `SELECT` pas un
      autre schéma tenant (IT Testcontainers, SKIPPED sans Docker).
- [ ] **T-MGMT** (D-42 = L) : `management.server.address` = `127.0.0.1` ;
      défaut de port **8090** ; `ObservabilityActuatorIT` reste vert
      (`MANAGEMENT_PORT=0`).
- [ ] **T-SECRETS** : hors profils de démo, secrets connus / HMAC M2M
      selon D-43 → fail-fast ; profil `test` inchangé.
- [ ] **T-PIN** (si D-44 = pin IP) : le client HTTP contacte l'IP validée,
      pas un second lookup.
- [ ] `./mvnw verify` vert ; Vitest + Playwright de non-régression (SPA
      non visée).

## Constraints & Assumptions

- Décision 13 inchangée. Hexagone inchangé (`domain` pur).
- Branche cloud `cursor/durcissement-securite-post-revue-a95d` (pas
  `feature-<name>` — contrainte environnement ; lint git `[MISS]` accepté).
- Pas de worktree isolé (un seul workspace cloud).
- `npx ai-devkit@latest task` indisponible (`unknown command 'task'`).
- Testcontainers : même contrat que `fiabilisation-tests-plateforme`
  (écrits/compilés, SKIPPED sans Docker).
- FILE-ATTENTE : ne **pas** trancher silencieusement un point TODO
  « à décider ». Une question à la fois. Phase 2 seulement quand toutes
  les questions matérielles sont closes par l'utilisateur.
- L'ébauche de code hors phase n'est **pas** une contrainte : si un D-*
  diverge, le code suit le D-*, pas l'inverse.

## Alternatives (brainstorm Phase 1)

| Approche | Trade-off | Reco |
|---|---|---|
| A — Un seul lot 40–44 | Une PR, chaîne S-1+S-2 traitée ensemble | **Recommandée** (nom de feature FILE-ATTENTE) |
| B — 40 seul, 41+ plus tard | Chaîne Critical reste ouverte | Non recommandée |
| C — Docs / runbooks only | SSTI et TOCTOU restent exploitables | Non recommandée |

Lot unique 40–44 : reco FILE-ATTENTE, pas contestée à ce stade.

## Questions & Open Items

| ID | Point | Statut | Choix / options |
|---|---|---|---|
| D-40 | SSTI Thymeleaf / SpEL | **B** | Allowlist SpEL (pas A sans SpEL, pas C confiance admin) |
| D-40-ALLOW | Périmètre de l'allowlist | **B2** | B1 + `#dates` `#numbers` `#strings` `#lists` ; refus `T()` / `#beans` |
| D-41 | Isolation PG inter-schémas | **A** | Rôle LOGIN par schéma ; écarté B database/tenant, C confiance réseau |
| D-41-SCOPE | Périmètre provisioning | **S** | Init Compose + runbook + IT ; pas de CLI tenant |
| D-42 | Actuator bind / port / `permitAll` | **L** | Bind `127.0.0.1`, port 8090, `permitAll` conservé |
| D-43 | Secrets défaut + HMAC M2M | **posée maintenant** | F / H / R ci-dessous |
| D-44 | SSRF DNS TOCTOU | en attente | pin IP ; proxy egress allowlist |

### Décisions enregistrées

- **D-40 = B** — allowlist SpEL. Écarté : A, C. Reco revue était A.
- **D-40-ALLOW = B2** — modèle + `#vars` + utilitaires dialecte standard
  (`#dates`, `#numbers`, `#strings`, `#lists`). Écarté : B1, B3.
  L'ébauche hors phase (interpolation seule) **diverge** toujours.
- **D-41 = A** — un rôle PG LOGIN par schéma tenant. Écarté : B, C.
- **D-41-SCOPE = S** — init SQL Compose (`tenant_dev`, superuser bootstrap
  ≠ user app) + runbook GETTING-STARTED / `02-multi-tenant.md` + IT
  Testcontainers. Écarté : C (CLI générique), L (SQL local sans runbook).
  Volume PG existant : l'init ne se rejoue pas (`compose down -v`) —
  à documenter dans le runbook.

- **D-42 = L** — bind `127.0.0.1` ; `MANAGEMENT_PORT` défaut **8090** ;
  `permitAll` conservé (DD-MGMT-SEC). Écarté : M (mTLS), N (ne pas
  publier). Keycloak reste 8081.

### Question actuelle — D-43 (seule question de ce tour)

**Pourquoi ça compte :** fallbacks `${DB_PASSWORD:flowforge}` et HMAC
`M2M_JWT_SECRET` acceptent un artefact copié en prod sans override, ou
un HMAC qui remplace l'issuer OIDC. OK en local. `permitAll` actuator
est déjà borné au loopback (D-42) ; les secrets JDBC/OIDC/HMAC restent
la porte d'entrée si le boot réussit.

**Options :**

- **F** — fail-fast **hors** profils `local`/`test` si secrets défaut ou
  absents (`DB_PASSWORD` denylist type `flowforge` / vide, master-key
  connecteur vide, `OIDC_CLIENT_SECRET` vide) **et** HMAC M2M **interdit**
  hors ces profils. Local/test inchangés (démo + HMAC de test). Compose
  peut garder des secrets de démo pour la stack locale.
- **H** — fail-fast JDBC/OIDC comme F, mais HMAC M2M encore autorisé en
  prod s'il est posé (la revue juge ça dangereux).
- **R** — documentation seulement, pas de fail-fast au boot.

**Recommandation revue : F.**

Ne pas répondre à D-44 dans ce tour.
