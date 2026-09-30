---
phase: planning
title: Planning — LC2 langage d'expression métier borné
description: Jalons et état d'avancement
status: done
created: 2026-09-30
updated: 2026-09-30
feature: lc2-langage-expression-metier
lot: LC2
requirements_ref: docs/ai/requirements/2026-09-30-feature-lc2-langage-expression-metier.md
design_ref: docs/ai/design/2026-09-30-feature-lc2-langage-expression-metier.md
testing_ref: docs/ai/testing/2026-09-30-feature-lc2-langage-expression-metier.md
---

# Planning — `lc2-langage-expression-metier`

Taille **M**. TDD à chaque jalon (rouge constaté avant le vert).

| Jalon | Contenu | État |
|---|---|---|
| M1 | Domaine : limites, exceptions, port, use cases gardés | done |
| M2 | Adaptateur JSONata borné : tests fonctionnels puis adversariaux (27 tests) | done |
| M3 | Configuration `app.expression.*`, câblage, API REST, handler d'erreurs | done |
| M4 | Outils MCP (+ appels réels via le transport M2M) | done |
| M5 | Brique : catalogue (12ᵉ bloc), allowlist, règles de forme, validation de syntaxe au parseur | done |
| M6 | Delegate + contexte (champs sensibles exclus) + test de bout en bout sur le vrai moteur | done |
| M7 | ArchUnit (bibliothèque confinée, jamais d'UEL), golden OpenAPI additif | done |
| M8 | Documentation : guide de conception §3sexies, guide utilisateur, analyse 40, TODO §75, CHANGELOG | done |
| M9 | Revue adversariale (Phase 10) et correctifs | voir implémentation |
