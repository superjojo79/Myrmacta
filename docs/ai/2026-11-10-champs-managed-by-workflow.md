# Champs de dossier gérés par le workflow (`managedByWorkflow`) — document de travail

Issue : [#9](https://github.com/superjojo79/Myrmacta/issues/9) · Spec du domaine : [docs/specs/04-dossiers.md](../specs/04-dossiers.md), [17-schema-champs-dynamiques.md](../specs/17-schema-champs-dynamiques.md), [32-modele-champs-enrichi.md](../specs/32-modele-champs-enrichi.md), [39-palette-metier-bpmn.md](../specs/39-palette-metier-bpmn.md) · Points du registre : [§126](../decisions/126-champs-managed-by-workflow.md) (H1 : [§112](../decisions/112-orchestration-technique-requete-construite-par-le-bpmn.md))

## Cadrage

- Problème et utilisateurs :
  - Un identifiant technique rendu par une application (compte IAM, matricule SIRH) ne peut pas vivre en sécurité dans un champ de dossier modifiable par toute personne qui a le droit EDIT (création, `PATCH .../fields`, formulaires `dossier-fields`). Un `DELETE` / `PUT` piloté par ce champ viserait alors un autre objet (constat H1, §112 / #260).
  - Contournement actuel : variables de process + discipline de conception non vérifiée (`exemples/onboarding-collaborateur`).
  - Utilisateurs : intégrateurs BPMN / admins de type de dossier ; agents métier (ne doivent plus pouvoir forger ces champs) ; le runtime workflow (`fieldMappingTask`).
- Périmètre / hors-périmètre :
  - **Dans** : drapeau `managedByWorkflow` sur `FieldDefinition` (champ racine) ; refus d’écriture sur toutes les surfaces hors `fieldMappingTask` ; lecture via `dossier.fields` dans les expressions ; UI lecture seule (détail) / absent des formulaires de saisie ; migration de l’exemple onboarding ; doc guides + specs ; point de registre §126.
  - **Hors** : variables de process « protégées » (option 2 de l’issue) ; nouveau régime de redaction / export lié au drapeau ; `managedByWorkflow` sur sous-champs de `REPEATABLE_GROUP` ; cumul avec `formula`.
- Critères de succès (vérifiables) :
  1. Définir un champ `managedByWorkflow: true` sur un type de dossier est accepté (admin) ; incompatible avec `required`, `formula`, `defaultValue`, et avec un sous-champ de groupe.
  2. Création de dossier, `PATCH /api/dossiers/{id}/fields`, complétion `dossier-fields`, portail, MCP d’écriture de champs, et tout autre appelant hors `fieldMappingTask` : refus si la payload touche un champ géré (y compris valeur égale à l’existante).
  3. `fieldMappingTask` écrit ces champs ; les expressions lisent `dossier.fields.<nom>` (sauf si `sensitive`).
  4. UI : champ absent des contrôles de saisie ; visible en lecture seule sur le détail ; drapeau configurable dans l’éditeur de schéma du type.
  5. L’exemple onboarding utilise des champs `managedByWorkflow` pour les identifiants techniques ; `OnboardingOrchestrationExampleIT` prouve qu’une modification humaine est refusée et qu’un abandon / compensation ne peut plus être détourné en forgeant le champ.
  6. `./mvnw verify`, contrôles docs, et si le SPA / contrats FE changent : `npm run lint` + `npm run test:all`.
- Contraintes (stack, tenant, sécurité, MCP, documentation à mettre à jour) :
  - Domaine pur + ports ; persistance JPA seulement ; JSON `fieldDefinitions` — pas de changeSet Liquibase.
  - `sensitive` orthogonal ; `searchable` / `unique` / `visibleWhen` : règles existantes ; `requiredWhen` interdit.
  - Doc : `GUIDE-CONCEPTION-BPMN.md`, `GUIDE-UTILISATEUR.md`, specs 04 / 17 / 32 / 39, exemple + README onboarding, registre §126 + identifiants.
- Validé par l'utilisateur le : 2026-11-10

## Questions ouvertes

| Question | Options | Recommandation | Statut |
|---|---|---|---|
| Nom du drapeau | `managedByWorkflow` / `readOnly` / les deux | `managedByWorkflow` | tranchée → §126 D-MANAGED-NAME |
| `required` | interdit / ignoré / contrôlé au mapping | interdit | tranchée → D-MANAGED-SCHEMA |
| Cumul avec `formula` | exclusifs / … | exclusifs | tranchée → D-MANAGED-SCHEMA |
| Surfaces d’écriture | tout sauf `fieldMappingTask` | idem | tranchée → D-MANAGED-WRITE |
| Migrer onboarding | oui | oui | tranchée → D-MANAGED-EXAMPLE |
| UI | lecture seule détail | idem (+ hint distinct) | tranchée → D-MANAGED-UI |
| Compatibilité `sensitive` | orthog. | orthog. | tranchée → D-MANAGED-SCHEMA |
| `defaultValue` | interdit | interdit | tranchée → D-MANAGED-SCHEMA |
| Sous-champ de groupe | non V1 | non V1 | tranchée → D-MANAGED-SCHEMA |
| Signal d’appelant | booléen `workflowMapping` sur la commande | booléen (pas `AccessContext.System`) | tranchée → D-MANAGED-WRITE |

## Conception

- Faits vérifiés dans le code ou les sources (fichier, méthode) :
  - `FieldDefinition` record + JSON dans `DossierTypeJpaEntity` → nouveau `Boolean managedByWorkflow` sans migration SQL.
  - Refus des champs calculés : `DossierFieldRules.apply` + `FieldValidationError.computedReadOnly` (`COMPUTED_READ_ONLY`).
  - `FieldMappingDelegate` → `UpdateDossierFieldsUseCase` ; `RouteMailDelegate` et portail aussi → le signal ne peut pas être `AccessContext.System` seul.
  - Formulaire FE `dossier-fields` → `PATCH .../fields` (couvert par le use case).
  - Contexte d’expression : champs déclarés, non sensibles, non calculés — un champ géré non sensible y figure.
- Composants touchés :
  - **Domaine** : `FieldDefinition` (+ constructeurs / `withRequired` / `withHidden` / `toString`), `FieldValidationError.managedByWorkflow`, garde `ManagedByWorkflowFields` (ou équivalent), `UpdateDossierFieldsCommand.workflowMapping`, `CreateDossierUseCaseImpl` + `UpdateDossierFieldsUseCaseImpl`.
  - **Infra** : `FieldMappingDelegate` pose `workflowMapping=true` ; pas d’autre appelant.
  - **Frontend** : `types.ts`, `FieldDefinitionDefsEditor`, `fieldRules` / `DynamicFields` (rendu lecture seule + hint), `validateFieldDefinitions`, i18n, tests Vitest + Playwright.
  - **Exemple / docs** : type-dossier, `generer-bpmn.py` / XML, README, guide conception, IT onboarding, specs.
- Scénarios de test :
  - **Unitaires** : constructeur `FieldDefinition` (incompatibilités) ; garde d’écriture (clé touchée / absente) ; use cases create/update avec et sans `workflowMapping` ; delegate mapping accepte.
  - **HTTP** : `PATCH` et création → 400 `MANAGED_BY_WORKFLOW` ; type admin accepte le drapeau.
  - **IT workflow** : `fieldMappingTask` écrit un champ géré ; onboarding : forge humaine refusée, compensation cible l’id workflow.
  - **FE** : Vitest éditeur + DynamicFields ; Playwright : champ géré non saisissable, visible en détail, mapping admin.

## Lots

| Lot | Contenu | Statut | PR |
|---|---|---|---|
| 1 | Domaine + enforcement écriture + tests backend (unitaires, HTTP, mapping smoke) | à faire | (cette branche / #54) |
| 2 | Frontend (éditeur, DynamicFields, validation, Vitest + Playwright) | à faire | |
| 3 | Exemple onboarding + guides + specs + IT onboarding ; clôture §126 / `Closes #9` | à faire | |

Prochaine action : livrer le lot 1 en TDD.

## Preuves

| Date | Commande | Résultat |
|---|---|---|

## Revue de clôture

| Constat | Sévérité | Correctif ou issue |
|---|---|---|
