# §126 — Champs de dossier gérés par le workflow (`managedByWorkflow`)

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (issue [#9](https://github.com/superjojo79/Myrmacta/issues/9)). Cadrage validé le 2026-11-10. Livré : drapeau
`managedByWorkflow`, enforcement d'écriture, UI, migration `exemples/onboarding-collaborateur`. Comportement :
[spec 04](../specs/04-dossiers.md) § « Implémentation — champs gérés par le workflow ».

Issue [#9](https://github.com/superjojo79/Myrmacta/issues/9) (arbitrage, constat H1 de
[§112](112-orchestration-technique-requete-construite-par-le-bpmn.md) / #260).

**Constat.** Un identifiant technique rendu par une application (compte IAM, matricule SIRH) ne peut pas vivre en
sécurité dans un champ de dossier modifiable par toute personne qui a le droit EDIT (création, `PATCH .../fields`,
formulaires `dossier-fields`). Un `DELETE` / `PUT` piloté par ce champ viserait un autre objet. L'exemple
`exemples/onboarding-collaborateur` contourne le problème avec des variables de process et une discipline de conception
non vérifiée.

**Option retenue (arbitrage #9).** Un drapeau `managedByWorkflow` sur `FieldDefinition` : refusé à toute écriture hors
`fieldMappingTask` ; lisible par `dossier.fields` dans les expressions (sauf si `sensitive`). Écarté : variables de
process « protégées » ; statu quo documenté.

**Décisions (utilisateur, 2026-11-10).**

- **D-MANAGED-NAME** — nom du drapeau : `managedByWorkflow` (pas `readOnly`, pour le distinguer des champs `formula`).
- **D-MANAGED-SCHEMA** — incompatible avec `required`, `formula` et `defaultValue` ; interdit sur un sous-champ de
  `REPEATABLE_GROUP` ; `sensitive` reste orthogonal ; `requiredWhen` interdit (pas de `required`) ; `searchable` et
  `unique` suivent les règles existantes de `FieldDefinition`. `visibleWhen` reste autorisé pour l'UI / l'aperçu, mais
  n'efface pas une valeur gérée et n'émet pas `NOT_APPLICABLE` (sinon un `fieldMappingTask` ou un PATCH voisin
  perdrait l'identifiant technique).
- **D-MANAGED-WRITE** — seule `fieldMappingTask` écrit ces champs. Toutes les autres surfaces (création, `PATCH`,
  `dossier-fields`, portail, MCP y compris `dryRun`, routage courrier, seeds hors mapping) refusent une clé touchée, y
  compris une valeur égale à l'existante. Signal : booléen `workflowMapping` sur `UpdateDossierFieldsCommand` (défaut
  `false`), posé à `true` uniquement par `FieldMappingDelegate`. La création n'a jamais ce signal. On n'utilise pas
  `AccessContext.System` comme autorisation : `RouteMailDelegate` est aussi `System` et doit rester refusé.
- **D-MANAGED-HTTP** — erreur de champ `MANAGED_BY_WORKFLOW` (400, `errors[]`), message sans recopier la valeur (comme
  `COMPUTED_READ_ONLY`).
- **D-MANAGED-UI** — absent des contrôles de saisie ; visible en lecture seule sur le détail (même rendu que les champs
  calculés dans `DynamicFields`, avec un hint distinct « géré par le workflow ») ; drapeau configurable dans
  `FieldDefinitionDefsEditor` (mode type de dossier seulement).
- **D-MANAGED-EXAMPLE** — migrer `exemples/onboarding-collaborateur` : champs « Provisioning » en `managedByWorkflow` ;
  les `PUT` / `DELETE` et chaînes d'identifiants relisent `dossier.fields` plutôt que des variables remises à `null` ;
  guide et `OnboardingOrchestrationExampleIT` mis à jour.

**Faits vérifiés dans le code (conception).**

- `FieldDefinition` est un `record` sérialisé en JSON dans `DossierTypeJpaEntity.fieldDefinitions` : nouveau booléen sans
  changeSet Liquibase.
- Les champs `formula` sont déjà refusés à la saisie via `DossierFieldRules.apply` + `COMPUTED_READ_ONLY`
  (`DossierFieldRules.java`, `FieldValidationError.computedReadOnly`).
- `FieldMappingDelegate` appelle déjà `UpdateDossierFieldsUseCase` avec `AccessContext.system("flowable-field-mapping")` ;
  `RouteMailDelegate` et le portail appellent le même use case sans pouvoir être distingués par le seul `System`.
- Le formulaire `dossier-fields` (FE) écrit via `PATCH /api/dossiers/{id}/fields` : le garde du use case couvre cette
  surface.
- Les expressions métier (`BusinessExpressionContext`) exposent les champs déclarés non sensibles non calculés : un champ
  `managedByWorkflow` non sensible y est lisible (objectif H1).

**Impacte.** Specs [04](../specs/04-dossiers.md), [17](../specs/17-schema-champs-dynamiques.md),
[32](../specs/32-modele-champs-enrichi.md), [39](../specs/39-palette-metier-bpmn.md) ; guides
`GUIDE-CONCEPTION-BPMN.md`, `GUIDE-UTILISATEUR.md` ; exemple onboarding ; `docs/decisions/identifiants.md` ;
frontend (`types`, éditeur, `DynamicFields`, validation).

**Lots.** Voir le document de travail. Clôture : `Closes #9`, revue indépendante, suppression du document de travail.
