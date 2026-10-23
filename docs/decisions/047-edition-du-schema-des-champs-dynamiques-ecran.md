# §47 — Édition du schéma des champs dynamiques — écran admin `DossierType`

> **Statut :** clos · **Section :** [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) · ancien `TODO.md` §47 · [registre](README.md)

**Constat :** [`docs/guides/GUIDE-UTILISATEUR.md` §1](../guides/GUIDE-UTILISATEUR.md#1-préalable--configurer-un-type-de-dossier)
documente déjà la limite : le formulaire `DossierTypesAdminView.vue` crée toujours un
`DossierType` avec `fieldDefinitions: []` en dur — aucun écran ne permet à un `TENANT_ADMIN` de
définir la **liste des `FieldDefinition`** d'un type. Le backend est déjà prêt (`POST`/`PATCH
/api/dossier-types/{code}` acceptent déjà `fieldDefinitions` en entrée, domaine et validation déjà
en place) : c'est un écran manquant, pas un trou d'architecture. À ne pas confondre avec
`edition-champs-dossier` (PR #53, déjà livrée) qui édite les **valeurs** de champs sur un dossier,
pas la définition du schéma sur le type.

**Découverte notable :** `CheckDossierTypeSchemaCompatibilityUseCase` existe déjà côté domaine
(rejoue `DossierFieldValidator` sur les dossiers existants avec le nouveau schéma proposé) mais
n'est aujourd'hui invoqué que par l'outil MCP `define_dossier_type` — `PATCH
/api/dossier-types/{code}` (REST) accepte silencieusement une modification de schéma qui rendrait
des dossiers existants incohérents, sans avertissement ni blocage. `DossierExceptionHandler` ne
mappe pas non plus `DossierTypeSchemaIncompatibleException` vers un statut HTTP.

**Points ouverts (à trancher en phase requirements/design, pas silencieusement) :**

1. **D-UI-SCHEMA-COMPAT** — comment l'écran s'articule avec le check de compatibilité :
   (a) nouvel endpoint REST de préflight (`POST
   /api/dossier-types/{code}/schema-compatibility`, miroir du `dryRun` MCP) puis confirmation
   explicite avant `PATCH` ; (b) `UpdateDossierTypeUseCaseImpl` invoque le check et bloque par
   défaut (aligne REST sur le comportement MCP, mappe
   `DossierTypeSchemaIncompatibleException` en 409) ; (c) V1 sans avertissement, incohérence §
   ci-dessus non résolue. Choix produit (bloquer vs avertir), pas seulement technique.
2. **Unicité de `name` dans `fieldDefinitions`** — validation formulaire seule, ou garde-fou
   domaine (`DossierType`/`DossierFieldValidator`) pour couvrir aussi MCP/API directe ?
3. **`allowUnknownFields`** — à confirmer dans le périmètre de cette feature (proposé) plutôt
   qu'un lot séparé ; absent de l'écran actuel malgré son exposition API complète.
4. **Alignement maquette** — [11-frontend.md](../specs/11-frontend.md) impose
   `FlowForge - Maquettes UI.html` comme référence ; aucun écran de ce fichier ne couvre
   aujourd'hui l'édition du schéma de champs — à vérifier en phase design.

**À produire :** décisions ci-dessus, puis écran admin (création + édition), avec mise à jour de
`docs/guides/GUIDE-UTILISATEUR.md` §1 (limite actuelle devient obsolète) une fois livrée.

**Impacte :** `frontend/src/views/admin/DossierTypesAdminView.vue`, `DossierTypeController`,
`UpdateDossierTypeUseCaseImpl`, `DossierExceptionHandler`, `docs/guides/GUIDE-UTILISATEUR.md` §1.

**Détail :** [17-schema-champs-dynamiques.md](../specs/17-schema-champs-dynamiques.md).

**Implémenté (2026-08-28, feature `schema-champs-dynamiques`, Phase 5 dev-lifecycle) :**
`UpdateDossierTypeUseCaseImpl` branche désormais `CheckDossierTypeSchemaCompatibilityUseCase`
(déjà écrit, jusqu'ici invoqué uniquement par le MCP) avant `save` — bloque (409, nouveau
`@ExceptionHandler(DossierTypeSchemaIncompatibleException.class)`) toute modification de schéma
qui rendrait des dossiers existants incohérents, alignant enfin REST sur le comportement déjà
strict du MCP `define_dossier_type` (lui-même simplifié : ne fait plus son propre appel au check
sur le chemin d'écriture réel, seulement pour `dryRun`). `DossierType` gagne une contrainte
d'unicité de `name` dans `fieldDefinitions` (compact constructor, 400 générique). Nouvel écran :
`FieldDefinitionDefsEditor.vue` (liste éditable de `FieldDefinition`, HTML natif + scoped CSS,
réutilisé création + panneau d'édition inline « Modifier les champs », pas de modale) intégré à
`DossierTypesAdminView.vue`, plus la bascule `allowUnknownFields`. Validation formulaire
(`validateFieldDefinitions.ts`) bloque côté client avant tout appel réseau. Écart trouvé en
implémentation (pas en design) : le panneau d'édition se pré-remplit directement depuis l'objet
déjà chargé par la liste paginée plutôt que par un `GET /api/dossier-types/{code}` dédié envisagé
au design — la réponse de liste porte déjà le schéma complet. `./mvnw verify` 1382 tests (0
échec), Jacoco tenu ; frontend 247 Vitest (0 échec), typecheck/lint propres, 59/59 Playwright
(4 nouveaux `E2E-SCHEMA-*`). 1 bug réel trouvé et corrigé en TDD :
`FieldDefinitionDefsEditor.vue` liait son rendu directement sur la prop `modelValue` au lieu de
l'état local réactif, donc n'affichait pas ses propres modifications avant un aller-retour par le
parent (ex. `type=SELECT` ne révélait pas l'éditeur d'options). Détail :
[`docs/ai/implementation/2026-08-28-feature-schema-champs-dynamiques.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-08-28-feature-schema-champs-dynamiques.md). Phases 7 (check
implementation — 1 écart trouvé et corrigé, ordre validation/check), 8 (write tests — 1383 tests
backend, 247 Vitest + 59 Playwright) et 9 (revue finale, aucun blocage) closes.
[PR #87](https://github.com/superjojo79/FlowForge/pull/87).
