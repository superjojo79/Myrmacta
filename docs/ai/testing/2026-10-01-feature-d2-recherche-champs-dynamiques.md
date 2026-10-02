---
phase: testing
title: Tests — D2 recherche sur champs dynamiques
---

- **PostgreSQL réel** (`DossierFieldSearchPostgresIT`, ignoré sans Docker ni `FLOWFORGE_TEST_PG_URL`) : égalité, `IN`, intervalles nombre/date, préfixe, combinaisons ; entrées hostiles (apostrophe, `%`, `_`, JSON forgé) ; refus (champ non cherchable, sensible, inconnu, opérateur, valeur, sans type, > 5 critères) ; colonne `jsonb` + index GIN servant `@>`.
- Domaine : `FieldSearchModelTest`, `SearchDossiersUseCaseImplTest` (+5), `FieldDefinition` (searchable).
- API : `DossierFieldSearchApiTest` (400 explicites, refus H2, liste inchangée sans filtre), `OpenApiContractIT` (golden régénéré).
- MCP : `SearchDossiersToolTest`.
- Frontend : `fieldSearch.spec.ts`, `FieldDefinitionDefsEditor.spec.ts`, `validateFieldDefinitions.spec.ts`, `SupervisionView.spec.ts` ; e2e `E2E-SEARCH-1`.
