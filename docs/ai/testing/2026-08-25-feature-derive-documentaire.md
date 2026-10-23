---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: verified
created: 2026-08-25
updated: 2026-08-25
feature: derive-documentaire
---

# Testing Strategy — `derive-documentaire`

## Phase 8: Write tests

Feature **doc-only**. Pas de Jacoco / Vitest / Playwright (aucun `.java`,
`.vue`, contrat API). Vérification = `grep` + `git diff --stat`.

Playwright : **non applicable** (SPA et contrats API non touchés) — pas un
contournement Phase 8.

## Contrôles (`grep`)

- [x] **T-TENANT** — `README.md` ne contient plus « résolu depuis le token OIDC à chaque requête ».
- [x] **T-GARDE** — `README.md` mentionne instance / `app.tenant.id` **et** claim OIDC comme garde.
- [x] **T-DMN-README** — README ne présente plus DMN comme capacité livrée sans qualifier « hors V1 » / « non activé ».
- [x] **T-COCKPIT** — `docs/specs/12-autorisation-conformite.md` : plus de « accès au cockpit Flowable » dans la table des rôles.
- [x] **T-JSOUP** — plus de `1.22.x` dans `README.md`, `00-index.md`, `07-generation-documents.md`.
- [x] **T-YAML** — `application.yml` ne commence plus par « until real adapters land ».
- [x] **T-CATALOG** — `06-connecteurs.md` mentionne le V1 livré (`http-rest` TRIGGER, `flowable-signal`) distinct de la cible.
- [x] **T-DIFF** — `git diff --stat` sans `.java` / `.vue` / `frontend/` / `db/changelog`.

## Evidence (2026-08-25, cette session)

```
# T-TENANT / T-JSOUP — 0 match
rg -n "résolu depuis le token OIDC à chaque requête|jsoup 1\\.22" \
  README.md "docs/specs/00-index.md" "docs/specs/07-generation-documents.md"
# (aucune ligne)

# T-COCKPIT — 0 match après retrait de la parenthèse « pas de cockpit… »
rg -n "cockpit Flowable" "docs/specs/12-autorisation-conformite.md"
# (aucune ligne)

# T-YAML — 0 match
rg -n "until real adapters land" src/main/resources/application.yml
# (aucune ligne)

# T-GARDE — présent
# README.md L70 : app.tenant.id + garde d'appartenance + tenant_id

# T-DMN-README — présent, qualifié
# README.md L50 : « n'est pas activé en V1 » ; diagramme « DMN hors »

# T-CATALOG — présent
# 06-connecteurs.md : colonnes Cible / V1 livré ; http-rest TRIGGER ;
# flowable-signal Oui

# T-JSOUP positif — 1.23.1 dans README, 00-index, 07

# T-DIFF
git diff --name-only origin/main...HEAD | rg '\.(java|vue)$|frontend/|db/changelog'
# → aucun .java/.vue/changelog
```

## Integration / E2E

Aucun.

## Manual Testing

README § Multi-tenant (L70, décision 13 + garde) et § Modèle de déploiement
(L136–145, instance dédiée + `context-path` cible) disent la même chose.
