---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-09-20
updated: 2026-09-20
feature: headers-http-globaux
lot: A4
phase1: 2026-09-20
phase2: 2026-09-20
phase3: 2026-09-20
stakeholder_decisions: 2026-09-20
todo_ref: FILE-ATTENTE.md lot A4 · REVUE-ALIGNEMENT-SECURITE.md S-9
---

# Requirements & Problem Understanding — `headers-http-globaux` (lot A4)

## Phase 2: Review requirements

Revue 2026-09-20. Cinq décisions produit tranchées une par une en Phase 1
(`AskQuestion`) : **D-HEADERS-SCOPE = B**, **D-HEADERS-CHAINS = B**,
**D-CSP-POLICY = B**, **D-HSTS = B**, **D-CONFIG = C** précisé par
**D-LOCAL-HEADERS = A**. Aucune question produit ouverte. Hypothèses
nommées **A-*** ci-dessous — pas des D-*. La chaîne CSP exacte
(directives bpmn-js / dmn-js / PrimeVue) et le mécanisme Spring
(`headers()` vs filtre) restent des questions **design Phase 3**, pas
des arbitrages produit.

Template README respecté (problem, goals/non-goals, stories, success,
constraints, questions). Pas de contradiction avec FILE-ATTENTE.md
(surface `SecurityConfig`, CSRF/S-8 non rouvert, Compose/context-path
intouchés). S-8 cookies CSRF hors scope confirmé.

## Problem Statement
**What problem are we solving?**

Le reliquat **S-9** de
[REVUE-ALIGNEMENT-SECURITE.md](../../../REVUE-ALIGNEMENT-SECURITE.md)
constate l’absence d’en-têtes HTTP applicatifs de défense en profondeur
(`Content-Security-Policy`, `Strict-Transport-Security`,
`X-Frame-Options`) sur les réponses de FlowForge.

- **Affectés :** tout utilisateur du SPA (clickjacking, XSS résiduel,
  MIME sniffing) ; partenaires qui appellent les webhooks/callbacks
  exposés Internet ; ops qui terminent TLS sur le reverse-proxy.
- **Situation actuelle :** `SecurityConfig` configure 4 filter chains
  (actuator, M2M, webhooks/callbacks, BFF) **sans** `http.headers(…)`.
  La seule CSP livrée est celle du preview HTML
  (`default-src 'none'; sandbox`, TODO §57 M13) sur
  `POST /api/documents/preview`. L’iframe admin utilise `srcdoc` +
  `sandbox=""`. Pas de meta CSP dans `frontend/index.html`.
  `server.forward-headers-strategy: framework` est déjà actif (HSTS
  derrière proxy possible).
- **Contournement actuel :** s’en remettre aux défauts implicites de
  Spring Security (non testés, pas de CSP) et/ou au reverse-proxy.

Lot nommé **A4** dans [FILE-ATTENTE.md](../../../FILE-ATTENTE.md) :
optionnel, surface exclusive `SecurityConfig.java` (+ en-têtes de
réponse SPA). B1 (`connecteurs-cible`) est en cours en parallèle —
ne pas prendre Compose ni le CSRF.

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

1. Poser **explicitement** et **tester** le jeu d’en-têtes suivant sur
   les **4** chaînes Spring Security (D-HEADERS-SCOPE = B,
   D-HEADERS-CHAINS = B) :
   - `Content-Security-Policy` (politique SPA enforce, D-CSP-POLICY = B)
   - `Strict-Transport-Security` (D-HSTS = B)
   - `X-Frame-Options`
   - `X-Content-Type-Options: nosniff`
   - `Referrer-Policy`
2. CSP **enforce** pour la SPA : `default-src 'self'` et sources
   scripts/styles/images/workers bornées ; `frame-ancestors 'none'` ;
   `object-src 'none'` ; `base-uri 'self'`. La CSP M13 du preview HTML
   **reste** et n’est **pas** affaiblie (A-PREVIEW).
3. HSTS explicite **hors profils `local`/`test`** : `max-age=31536000`,
   `includeSubDomains`, **pas** de `preload`. En `local`/`test` : pas
   d’en-tête HSTS (D-HSTS = B). S’appuie sur `X-Forwarded-Proto` déjà
   honoré.
4. Kill switch unique `app.security.headers.enabled` (défaut `true`),
   CSP/HSTS non paramétrables (D-CONFIG = C). **Pas** `false` par
   défaut en local : seul HSTS est absent en local/test
   (D-LOCAL-HEADERS = A). Ainsi MockMvc et Playwright voient la CSP.

### Secondary goals

- Documenter `APP_SECURITY_HEADERS_ENABLED` dans `.env.example` et
  [GETTING-STARTED.md](../../../GETTING-STARTED.md) (convention projet
  : nouvelle variable consommée au démarrage).
- Mentionner dans [DEPLOIEMENT.md](../../../DEPLOIEMENT.md) que l’app
  émet HSTS, pour éviter un HSTS proxy plus court qui le contredirait.
- `CHANGELOG.md` à l’ouverture de PR code.

### Non-goals

- Cookies CSRF / `HttpOnly=false` (S-8, décision 15) — **ne pas
  rouvrir**.
- `server.servlet.context-path`, `docker-compose*.yml`.
- Redis, rate-limit distribué, SSE multi-pod.
- `Permissions-Policy` (écarté, D-HEADERS-SCOPE ≠ C).
- CSP `Report-Only` comme livrable (écarté, D-CSP-POLICY ≠ C).
- Meta CSP dans `index.html` (les assets SPA passent déjà par la
  chaîne BFF).
- Nouvel écran admin ; [GUIDE-UTILISATEUR.md](../../../GUIDE-UTILISATEUR.md)
  inchangé (pas de champ UI).
- ClamAV (Vague C), Kafka / import batch / ERP (B1).
- Portail externe (analyse 26) — nouvelle chaîne publique hors de ce
  lot ; A4 ne doit pas hypothéquer un futur `securityMatcher` portal,
  d’où le helper partagé (D-HEADERS-CHAINS = B).

## User Stories & Use Cases
**How will users interact with the solution?**

- En tant que **navigateur d’un utilisateur authentifié**, je reçois
  sur chaque réponse BFF (HTML SPA et API) une CSP enforce +
  `X-Frame-Options: DENY` + `nosniff` + `Referrer-Policy`, afin de
  réduire clickjacking et XSS d’injection de script tiers.
- En tant que **ops**, je veux HSTS 1 an + `includeSubDomains` dès que
  l’instance n’est pas en profil `local`/`test` et que la requête est
  HTTPS (y compris via `X-Forwarded-Proto`), sans inscription
  `preload`.
- En tant que **ops en urgence**, je peux poser
  `APP_SECURITY_HEADERS_ENABLED=false` pour couper le paquet custom
  sans rebuild (kill switch, défaut `true`).
- En tant que **partenaire webhook**, les réponses
  `/api/webhooks/**` et `/api/callbacks/**` portent les mêmes
  en-têtes que le BFF (défense si un navigateur suit l’URL).
- En tant que **`TENANT_ADMIN` qui prévisualise un template**,
  `POST /api/documents/preview` conserve `Content-Security-Policy:
  default-src 'none'; sandbox` (M13) ; l’iframe `sandbox=""` +
  `srcdoc` continue de fonctionner.
- En tant que **dev local**, je n’ai pas de HSTS (HTTP
  `localhost`) mais j’ai la CSP, pour que Playwright détecte une
  casse PrimeVue / bpmn-js / dmn-js **avant** la prod.

Edge cases :

- Kill switch `false` : plus de paquet A4 ; les défauts Spring
  éventuels ne sont pas un objectif de ce lot (Phase 3).
- Actuator loopback HTTP : HSTS absent ou ignoré par le navigateur
  (requête non secure) ; CSP/XFO restent posés (D-HEADERS-CHAINS = B).
- Preview HTML : deux CSP possibles sur la même réponse (globale ∧
  M13). Le navigateur les **AND** : le preview reste au moins aussi
  strict que M13 (A-PREVIEW).

## Success Criteria
**How will we know when we're done?**

- [ ] MockMvc sur les 4 chaînes (BFF authentifié, M2M, webhook
      anonyme, actuator) : présence de CSP, XFO, nosniff,
      Referrer-Policy quand `headers.enabled=true`.
- [ ] HSTS présent hors `local`/`test` sur une requête secure ; **absent**
      sous profil `local` ou `test`.
- [ ] `headers.enabled=false` : le paquet custom n’est pas émis
      (prouvé par test).
- [ ] Preview HTML : en-tête M13 inchangé
      (`default-src 'none'; sandbox`) ; test existant
      `DocumentPreviewApiTest` toujours vert.
- [ ] Playwright : parcours SPA réel (login → supervision ou éditeur
      BPMN/DMN) sans erreur console CSP bloquante ; iframe preview
      templates toujours rendue.
- [ ] `./mvnw verify` vert ; `cd frontend && npm run test` et
      `npm run test:e2e` verts (convention FE).
- [ ] `.env.example` + GETTING-STARTED.md mentionnent
      `APP_SECURITY_HEADERS_ENABLED` ; DEPLOIEMENT.md note HSTS
      applicatif.

## Constraints & Assumptions
**What limitations do we need to work within?**

### Constraints

- Surface d’écriture : `SecurityConfig.java` et tests sécurité
  associés ; docs `.env.example` / GETTING-STARTED / DEPLOIEMENT /
  CHANGELOG. Pas Compose, pas CSRF, pas `context-path`.
- B1 en cours : limiter le diff GETTING-STARTED à **une** variable.
- Architecture hexagonale : pas d’annotation Spring dans
  `com.flowforge.domain`. Ce lot est 100 % infrastructure sécurité.
- SPA same-origin (décision n° 27) : `connect-src 'self'` suffit ;
  pas de CORS applicatif à ouvrir dans la CSP.
- Playwright obligatoire (CSP peut casser la SPA).

### Assumptions (nommées)

| ID | Hypothèse |
|---|---|
| **A-FRAME** | `X-Frame-Options: DENY` pour coller à `frame-ancestors 'none'`. L’iframe preview `srcdoc` n’est pas affectée. |
| **A-REFERRER** | `Referrer-Policy: strict-origin-when-cross-origin`. Pas d’impact OIDC BFF (redirect, pas fetch IdP depuis le SPA). |
| **A-PREVIEW** | CSP M13 conservée telle quelle. Une CSP globale plus large sur la même réponse est acceptable (AND navigateur). |
| **A-HSTS-SECURE** | Hors `local`/`test`, HSTS n’est écrit que si la requête est reconnue HTTPS (Spring + `forward-headers-strategy`). |
| **A-NO-META** | Pas de `<meta http-equiv="Content-Security-Policy">` dans `index.html`. |
| **A-NO-GUIDE-UI** | Pas de mise à jour GUIDE-UTILISATEUR (aucun écran/champ admin). |

## Questions & Open Items
**What do we still need to clarify?**

**Produit : néant.** Toutes les D-* Phase 1 sont tranchées.

**Design Phase 3 close** (2026-09-20) : D-MECHANISM = B
(`SecurityHeadersCustomizer`), D-CSP-STRING = A (constante documentée
dans le design), D-SWITCH-OFF = A (kill switch = no-op, défauts Spring
conservés). Plus de question ouverte avant Phase 4.

## Décisions Phase 1 (stakeholder)

| ID | Choix | Signification |
|---|---|---|
| D-HEADERS-SCOPE | **B** | Trio S-9 + `X-Content-Type-Options: nosniff` + `Referrer-Policy` |
| D-HEADERS-CHAINS | **B** | 4 chaînes, helper partagé (leçon F0 / matcher oublié) |
| D-CSP-POLICY | **B** | CSP enforce SPA ; preview M13 plus stricte conservée |
| D-HSTS | **B** | max-age 1 an, includeSubDomains, pas de preload ; absent `local`/`test` |
| D-CONFIG | **C** | Un booléen `app.security.headers.enabled`, CSP/HSTS non paramétrables |
| D-LOCAL-HEADERS | **A** | Kill switch défaut `true` partout ; seul HSTS est retiré en local/test |
