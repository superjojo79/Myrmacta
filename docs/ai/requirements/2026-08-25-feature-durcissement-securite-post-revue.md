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

**Statut : brouillon.** Phase 2 n'est pas ouverte. Aucune décision D-*
n'est verrouillée. Une ébauche de code existe déjà sur le working tree
(hors phase) : elle n'a **pas** valeur de choix produit et sera réalignée
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

1. Couper ou restreindre l'évaluation SpEL / types Java dans les templates
   stockés, selon D-40.
2. Réduire le blast radius SQL inter-schémas, selon D-41 / D-41-SCOPE.
3. Rendre l'actuator non exposé par défaut hors loopback / départager le
   port Keycloak, selon D-42.
4. Empêcher un boot prod avec secrets de démo ou HMAC M2M, selon D-43.
5. Empêcher le rebinding DNS sur les sorties HTTP déjà gardées, selon D-44.

### Non-goals

- Activer `http-rest` SERVICE, DMN, Kafka, AV, headers CSP/HSTS, rate-limit
  distribué, ArchUnit `application` (§46).
- Database-par-tenant **sauf si D-41 = B**.
- Proxy egress allowlist **sauf si D-44 choisit cette option**.
- Changer le modèle « une instance = un tenant » (décision 13).
- Outillage CLI provisioning générique (TODO §31) — **sauf si D-41-SCOPE
  l'inclut** ; question posée après D-41, pas maintenant.

## User Stories & Use Cases

- En tant qu'**ops SaaS**, je ne veux pas qu'un `TENANT_ADMIN` compromis
  obtienne une RCE JVM **et** lise `tenant_b` via le même user PG.
- En tant que **dev local**, je veux que Keycloak et l'actuator ne se
  marchent pas dessus, et que le profil de démo reste utilisable.
- En tant que **prod**, je ne veux pas qu'un oubli de secret (`flowforge`,
  HMAC M2M) laisse l'instance démarrer.

Cas limites à trancher avec les D-* : templates qui utiliseraient déjà
`T()`, `#beans`, `#dates` ; volume PG Compose existant (init SQL non
rejouée) ; scrape Prometheus hors localhost.

## Success Criteria

Critères **candidats** — à confirmer / ajuster en Phase 2 une fois les
D-* tranchés :

- [ ] **T-SSTI** (si D-40 ≠ C) : `${T(java.lang.Runtime)}` / preprocessing
      SpEL → exception ou texte non évalué ; interpolation métier
      `th:text="${title}"` conservée si D-40 = A ou B.
- [ ] **T-GRANT** (si D-41 = A ou B) : le rôle d'application ne `SELECT`
      pas un autre schéma tenant (IT Testcontainers, SKIPPED sans Docker).
- [ ] **T-MGMT** : plus de conflit de port avec Keycloak 8081 en local ;
      surface management documentée selon D-42.
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

Ce tableau n'est **pas** un lock. Dire B ou C si le lot unique ne convient
pas. La première question ci-dessous suppose l'approche A.

## Questions & Open Items

**Toutes ouvertes.** Aucune n'est tranchée. Une seule est posée maintenant.

| ID | Point | Statut | Options (TODO / revue) |
|---|---|---|---|
| D-40 | SSTI Thymeleaf / SpEL | **posée maintenant** | A / B / C |
| D-41 | Isolation PG inter-schémas | en attente | A rôle LOGIN par schéma ; B database/tenant ; C confiance réseau |
| D-41-SCOPE | Périmètre provisioning (§31) | en attente (après D-41) | init local + runbook vs CLI générique |
| D-42 | Actuator bind / port / `permitAll` | en attente | bind localhost ; mTLS ; ne pas publier ; départager 8081 |
| D-43 | Secrets défaut + HMAC M2M | en attente | fail-fast hors `local`/`test` ; HMAC interdit hors ces profils ; secrets Compose obligatoires |
| D-44 | SSRF DNS TOCTOU | en attente | pin IP ; proxy egress allowlist |

### Question actuelle — D-40 (seule question de ce tour)

**Pourquoi ça compte :** c'est le RCE JVM. L'option C laisse S-1 ouvert et
rend la chaîne S-1+S-2 Critical si D-41 n'est pas A/B. L'option B garde une
surface SpEL à maintenir. L'option A coupe SpringEL.

**Options :**

- **A** — moteur Thymeleaf **sans** SpEL (`TemplateEngine` standard /
  interpolation sûre). Reco revue S-1.
- **B** — allowlist SpEL (sous-ensemble d'expressions autorisées).
- **C** — assumer `TENANT_ADMIN` = opérateur de confiance (incompatible
  SaaS si le point 41 reste ouvert).

**Recommandation revue :** A.

Ne pas répondre aux autres D-* dans le même tour.
