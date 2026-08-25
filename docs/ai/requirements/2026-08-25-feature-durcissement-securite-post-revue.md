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

## Phase 2: Review requirements

Revue 2026-08-25. D-* tranchés en Phase 1 par l'utilisateur (pas par la
reco revue seule : D-40 = B alors que la revue recommandait A). Aucune
question ouverte. Hypothèses restantes **nommées** (A-*) — pas des D-*.
Design / planning / testing remplis trop tôt : **hors phase** jusqu'à
Phase 3+.

Ébauche de code hors phase : **diverge** de D-40-ALLOW = B2 (SpEL coupé,
proche A/B1). Pin IP partiellement proche de D-44 = P. Le code suivra
les D-*, pas l'inverse.

## Problem Statement
**What problem are we solving?**

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

- **Affectés :** ops SaaS (blast radius inter-tenant) ; `TENANT_ADMIN`
  (templates / preview) ; dev local (Keycloak vs actuator, secrets démo) ;
  prod (boot avec secrets oubliés).
- **Situation actuelle :** findings ouverts dans TODO §40–44 ; aucune
  mitigation produit livrée sur `main` pour ces cinq points.

**Hors feature :** TODO §45 (clos PR #75), TODO §46 (ArchUnit
`application`), reliquats S-6…S-10, DMN, connecteur `http-rest` en rôle
SERVICE (le skeleton existant n'est pas activé ; le pin D-44 s'applique
quand même à ses appels HTTP, A-PIN-CALLERS).

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

1. Allowlist SpEL **B2** (D-40 = B, D-40-ALLOW = B2) sur generate **et**
   live preview : modèle + `#vars` + `#dates` / `#numbers` / `#strings` /
   `#lists` ; refus `T()`, `new`, `#beans`, request/servlet. Même moteur
   / allowlist pour le syntax checker (pas d'écart generate vs check).
2. Isolation SQL : rôle PG LOGIN par schéma (D-41 = A, D-41-SCOPE = S) —
   init Compose `tenant_dev` (superuser bootstrap ≠ user app) + runbook
   ops (GETTING-STARTED / `02-multi-tenant.md`) + IT Testcontainers.
   Pas de CLI/orchestrateur tenant.
3. Actuator : bind `127.0.0.1`, port défaut **8090**, `permitAll` conservé
   (D-42 = L / DD-MGMT-SEC). Keycloak reste 8081.
4. Fail-fast hors `local`/`test` si secrets défaut ou absents, HMAC M2M
   interdit hors ces profils (D-43 = F). GETTING-STARTED documente le
   profil `local` pour la démo.
5. Pin IP (D-44 = P) sur les clients HTTP qui appellent le garde puis
   connectent (A-PIN-CALLERS) : un lookup ; connexion à l'IP validée ;
   `Host` + SNI / vérif TLS sur le hostname d'origine ; redirects `NEVER`
   inchangé.

### Non-goals

- Activer `http-rest` SERVICE, DMN, Kafka, AV, headers CSP/HSTS, rate-limit
  distribué, ArchUnit `application` (§46).
- Database-par-tenant (D-41 ≠ B).
- Proxy egress allowlist (D-44 ≠ X).
- mTLS actuator (D-42 ≠ M).
- Changer le modèle « une instance = un tenant » (décision 13).
- Outillage CLI / orchestrateur de provisioning tenant (D-41-SCOPE = S).
- Changer la SPA (aucune route / store / écran visé).

## User Stories & Use Cases
**How will users interact with the solution?**

- En tant qu'**ops SaaS**, je ne veux pas qu'un `TENANT_ADMIN` compromis
  obtienne une RCE JVM via `T()` **et** lise `tenant_b` via le même user PG.
- En tant que **`TENANT_ADMIN`**, je peux toujours interpoler le modèle et
  formater via `#dates` / `#numbers` / `#strings` / `#lists` / `#vars` ;
  un template avec `T()` ou `#beans` ne s'évalue pas.
- En tant que **dev local**, Keycloak `:8081` et l'actuator `:8090` ne se
  marchent pas dessus ; `spring.profiles.active=local` conserve les secrets
  de démo.
- En tant que **prod**, un oubli `DB_PASSWORD=flowforge` ou un HMAC M2M
  empêche le boot.

**Cas limites :**

- Volume PG Compose existant : init SQL non rejouée → documenter
  `compose down -v` dans le runbook (D-41-SCOPE).
- Scrape Prometheus hors loopback : seulement si ops surcharge
  `MANAGEMENT_ADDRESS` (défaut `127.0.0.1`).
- Templates existants qui utiliseraient `T()` / `#beans` : cassent
  volontairement (T-SSTI).
- Validation URL webhook admin (`assertSafe` à l'enregistrement) : pas
  de connect HTTP, donc pas de pin (A-PIN-CALLERS).

## Success Criteria
**How will we know when we're done?**

- [ ] **T-SSTI** — generate et live preview : `${T(java.lang.Runtime)}` et
      `#beans` → non évalués (exception ou texte brut, A-SSTI-FAIL) ;
      `${title}`, `#vars`, `#dates` / `#numbers` / `#strings` / `#lists`
      évalués. Syntax checker aligné (même allowlist).
- [ ] **T-GRANT** — rôle app : `SELECT` sur son schéma OK ; `SELECT` sur
      un autre schéma tenant → permission denied (Testcontainers,
      SKIPPED sans Docker).
- [ ] **T-MGMT** — `management.server.address` = `127.0.0.1` ; défaut
      port **8090** ; `ObservabilityActuatorIT` vert (`MANAGEMENT_PORT=0`).
- [ ] **T-SECRETS** — hors `local`/`test` : denylist `DB_PASSWORD` (au
      moins `flowforge` / vide), master-key vide, `OIDC_CLIENT_SECRET`
      vide, HMAC M2M posé → fail-fast au boot ; profil `test` inchangé.
- [ ] **T-PIN** — un lookup DNS ; URI contactée = IP validée ; `Host` /
      SNI = hostname d'origine ; test de rebinding. Callers A-PIN-CALLERS.
- [ ] **T-DOCS** — GETTING-STARTED : profil `local`, actuator `:8090`,
      rôle PG `tenant_dev`, volume `down -v` ; notes `02` / `08` / `09`
      alignées ; TODO §40–44 clos à la fin du cycle.
- [ ] `./mvnw verify` vert ; Vitest + Playwright de non-régression (SPA
      non visée).

## Constraints & Assumptions
**What limitations do we need to work within?**

### Constraints

- Décision 13 inchangée. Hexagone inchangé (`domain` pur ; durcissements
  dans `infrastructure`).
- Branche cloud `cursor/durcissement-securite-post-revue-a95d` (lint git
  `[MISS]` `feature-*` accepté).
- Pas de worktree isolé. `npx ai-devkit@latest task` indisponible.
- Testcontainers : même contrat que `fiabilisation-tests-plateforme`.
- Jacoco bundle existant tenu (nouvelles classes au-dessus des seuils).
- JDK d'itération cloud : **21** si 25 absent (`-Dmaven.compiler.release=21`,
  `-Dskip.frontend=true` en itération) ; CI / `verify` selon le JDK du
  job.

### Named assumptions (Phase 2)

| ID | Hypothèse | Si faux |
|---|---|---|
| A-PIN-CALLERS | Pin sur les adaptateurs qui **connectent** après le garde : `WebhookRestAdapter` et `HttpRestServiceConnector` (skeleton). L'admin webhook (`assertSafe` à l'enregistrement) ne connecte pas. | Phase 3 revoit les call sites |
| A-SSTI-FAIL | « Non évalué » = exception **ou** texte brut ; le code HTTP exact (400 vs 200 + placeholder) est un DD Phase 3 | Si le métier exige 400 strict, le poser en Phase 3 |
| A-SSTI-ENGINE | Mécanisme B2 (évaluateur custom vs sandbox SpringEL) = design Phase 3 | — |
| A-DENYLIST | Liste exacte des mots de passe interdits hors local/test = design ; le minimum produit est `flowforge` et vide | — |
| A-LOCAL-DOC | GETTING-STARTED documente `--spring.profiles.active=local` (ou équivalent) pour la démo | Boot local sans profil = fail-fast D-43 |
| A-NO-SPA | Zéro fichier `frontend/**` attendu | Toute UI actuator/secrets serait hors scope |

## Alternatives (Phase 1, revalidées Phase 2)

| Approche | Trade-off | Verdict |
|---|---|---|
| A — Un seul lot 40–44 | Une PR, chaîne S-1+S-2 ensemble | **Retenue** (nom de feature, non contestée) |
| B — 40 seul, 41+ plus tard | Chaîne Critical ouverte | Écartée |
| C — Docs / runbooks only | SSTI / TOCTOU exploitables | Écartée |

Écart volontaire vs reco revue : **D-40 = B** (revue : A). Conservé.

## Questions & Open Items
**What do we still need to clarify?**

**Aucune question ouverte.** Toutes closes ou nommées (A-*).

| ID | Point | Choix |
|---|---|---|
| D-40 | SSTI Thymeleaf / SpEL | **B** allowlist (écarté A, C) |
| D-40-ALLOW | Périmètre allowlist | **B2** modèle + `#vars` + `#dates` `#numbers` `#strings` `#lists` ; refus `T()` `#beans` `new` request (écarté B1, B3) |
| D-41 | Isolation PG | **A** rôle LOGIN par schéma (écarté B database, C réseau) |
| D-41-SCOPE | Provisioning | **S** init Compose + runbook + IT ; pas de CLI (écarté C, L) |
| D-42 | Actuator | **L** `127.0.0.1:8090`, `permitAll` conservé (écarté M, N) |
| D-43 | Secrets / HMAC | **F** fail-fast hors local/test ; HMAC interdit hors ces profils (écarté H, R) |
| D-44 | SSRF TOCTOU | **P** pin IP (écarté X, N) |

Prochaine étape **proposée**, pas exécutée : Phase 3 `dev-design` (réécrire
le design hors phase pour coller à B2 + A-* ; ne pas recycler l'ébauche
A/B1 comme reviewed).
