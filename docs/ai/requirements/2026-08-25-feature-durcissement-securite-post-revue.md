---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-08-25
updated: 2026-08-25
feature: durcissement-securite-post-revue
phase1: 2026-08-25
phase2: 2026-08-25
stakeholder_decisions: 2026-08-25
todo_ref: TODO.md §40–44 · FILE-ATTENTE.md · REVUE-ALIGNEMENT-SECURITE.md
---

# Requirements & Problem Understanding — `durcissement-securite-post-revue`

## Phase 1: Requirements

Regroupement acté des findings S-1…S-5
([REVUE-ALIGNEMENT-SECURITE.md](../../../REVUE-ALIGNEMENT-SECURITE.md) §4).
Séquencement interne : 40+41 (chaîne) → 42+43 → 44.

## Phase 2: Review requirements

Agent cloud : pas d'`AskUserQuestion`. Le cycle a été **demandé explicitement**
sur cette feature. Les options **recommandées par la revue** sont retenues
comme D-* (pas un arbitrage silencieux hors source : la revue est la source).

## Problem Statement

Cinq durcissements ouverts après la revue post-implémentation 2026-08-25.
Le code suit l'hexagone et la décision 13, mais :

1. **SSTI (S-1 / §40)** — `SpringTemplateEngine` évalue du SpEL dans les
   templates `TENANT_ADMIN` (generate / live preview) → RCE JVM.
2. **GRANT PG (S-2 / §41)** — `search_path` n'est pas une barrière ; un seul
   rôle PG superuser de base peut lire les autres schémas. Chaîne avec S-1 =
   Critical SaaS.
3. **Actuator (S-3 / §42)** — `permitAll` sur le port management, bind toutes
   interfaces, défaut **8081** en conflit avec Keycloak local.
4. **Secrets / HMAC M2M (S-4 / §43)** — fallbacks `flowforge` ; HMAC HS256
   optionnel accepte des JWT `API_SERVICE` hors local.
5. **SSRF TOCTOU (S-5 / §44)** — `OutboundUrlGuard` résout le DNS puis le
   client HTTP se reconnecte sans pin IP.

**Hors feature :** TODO §45 (clos PR #75), §46 (ArchUnit `application`),
reliquats S-6…S-10, provisioning CLI complet (point 31).

## Goals & Objectives

1. Templates Thymeleaf **sans SpEL** : `${T(java.lang.Runtime)}` non évalué.
2. Rôle PG d'application **limité au schéma du tenant** (local `tenant_dev` +
   runbook + IT Testcontainers). Pas d'outillage provisioning générique.
3. Actuator bound **localhost** ; port management **≠** Keycloak 8081.
4. Hors profils `local`/`test` : fail-fast si secrets défaut / HMAC M2M.
5. Sorties HTTP webhook (et skeleton SERVICE) **pinnées** sur l'IP validée.

## Non-goals

- Activer `http-rest` SERVICE, DMN, Kafka, AV, headers CSP/HSTS, rate-limit
  distribué, ArchUnit `application` (§46).
- mTLS Prometheus, database-par-tenant, proxy egress allowlist.
- Changer le modèle « une instance = un tenant ».

## User Stories

- En tant qu'**ops SaaS**, un `TENANT_ADMIN` compromis ne doit pas RCE + lire
  `tenant_b` via le même user PG.
- En tant que **dev local**, Keycloak 8081 et l'actuator ne se marchent pas
  dessus ; `spring.profiles.active=local` conserve les secrets de démo.
- En tant que **prod**, démarrer avec `DB_PASSWORD=flowforge` ou un HMAC M2M
  doit **refuser** de booter.

## Success Criteria

- [ ] **T-SSTI** : `${T(java.lang.Runtime)}` / preprocessing SpEL → exception
      ou texte non évalué ; `th:text="${title}"` continue de marcher.
- [ ] **T-GRANT** : rôle app `SELECT` sur un autre schéma → permission denied
      (Testcontainers, SKIPPED si pas de Docker).
- [ ] **T-MGMT** : `management.server.address` = `127.0.0.1` ; défaut de port
      ≠ 8081 ; `ObservabilityActuatorIT` reste vert.
- [ ] **T-SECRETS** : hors `local`/`test`, mot de passe défaut / HMAC M2M →
      fail-fast au boot ; profil `test` inchangé.
- [ ] **T-PIN** : le client HTTP contacte l'IP validée, pas un second lookup
      (test de rebinding).
- [ ] `./mvnw verify` vert ; FE Vitest + Playwright de régression (pas de
      changement SPA attendu — preuve de non-régression).

## Constraints & Assumptions

- Décision 13 inchangée. Hexagone inchangé.
- Branche cloud `cursor/durcissement-securite-post-revue-a95d`.
- `npx ai-devkit@latest task` indisponible. Pas de worktree isolé.
- Testcontainers : même contrat que `fiabilisation-tests-plateforme`
  (écrits/compilés, SKIPPED sans Docker).

## Questions & Open Items

**Aucun restant.** Décisions figées :

| ID | Point | Choix | Écarté |
|---|---|---|---|
| D-40 | SSTI | **A** `TemplateEngine` standard, pas `SpringTemplateEngine` / SpEL | B allowlist ; C confiance admin |
| D-41 | Isolation PG | **A** un rôle LOGIN par schéma tenant ; Compose : superuser bootstrap ≠ user app ; runbook (point 31) | B database/tenant ; C confiance réseau |
| D-41-SCOPE | Provisioning | Init SQL `tenant_dev` + doc GETTING-STARTED / `02` ; **pas** de CLI provisioning | Outillage point 31 |
| D-42 | Actuator | Bind `127.0.0.1` ; `MANAGEMENT_PORT` défaut **8090** ; `permitAll` **conservé** sur le matcher management (DD-MGMT-SEC scrape local) | mTLS ; publier 8081 |
| D-43 | Secrets | Fail-fast hors `local`/`test` si secrets connus/absents ; HMAC M2M **interdit** hors ces profils | Garder HMAC en prod |
| D-44 | SSRF | Pin IP (pas proxy egress) | Allowlist proxy |

## Alternatives (Phase 1)

| Approche | Trade-off | Verdict |
|---|---|---|
| A — Lot unique 40–44 | Une PR, chaîne S-1+S-2 traitée ensemble | **Retenue** (FILE-ATTENTE) |
| B — 40 seul puis 41 plus tard | Chaîne Critical reste ouverte | Écartée |
| C — Docs-only runbooks | SSTI/TOCTOU restent exploitables | Écartée |
