---
phase: implementation
title: Unicité d'un champ de dossier — implémentation
description: Lots A (unicité serveur et MCP) et B (brique de recherche) livrés ; lots C et D à venir
---

# Implémentation — `unicite-champ-dossier` (issue #184)

## Lot A — unicité (livré)

| Fichier | Rôle |
|---|---|
| `domain/dossier/model/FieldDefinition.java` | composant `unique`, règles U2-U4 et nom ≤ 100 caractères ; copies `withRequired`/`withHidden` le conservent |
| `domain/dossier/model/UniqueKey.java`, `UniqueFieldKeys.java` | clé canonique (U5-U7), `MAX_VALUE_CHARS = 500` ; `toString` masque la valeur |
| `domain/dossier/model/UniqueDuplicate.java` | valeur en double et références, pour le refus d'activation |
| `domain/dossier/model/DossierFieldValidator.java` | U13 : valeur unique > 500 caractères → `INVALID_VALUE` |
| `domain/dossier/model/FieldValidationError.java` | composant `reference`, fabrique `notUnique` |
| `domain/dossier/model/DossierFieldValidationException.java` | message qui nomme champ, code et explication (MCP) |
| `domain/dossier/model/DossierTypeSchemaIncompatibleException.java` | `duplicates` |
| `domain/dossier/DossierUniqueKeys.java` | clés d'un dossier après recalcul des règles ; doublons parmi des dossiers |
| `domain/dossier/DossierUniquenessCheck.java`, `port/out/DossierUniquenessPort.java` | pré-contrôle des use cases |
| `domain/dossier/usecase/CreateDossierUseCaseImpl`, `UpdateDossierFieldsUseCaseImpl` | pré-contrôle avec les autres erreurs |
| `domain/dossier/usecase/CheckDossierTypeSchemaCompatibilityUseCaseImpl` (+ port `Result.duplicates`) | U10 |
| `infrastructure/persistence/dossier/JdbcDossierUniqueValueStore.java` | table, verrous consultatifs, `findHolders` |
| `infrastructure/persistence/dossier/JpaDossierRepositoryAdapter.java` | verrou partagé, définitions relues, synchronisation |
| `infrastructure/persistence/dossier/JpaDossierTypeRepositoryAdapter.java` | verrou exclusif, reconstruction, 409 sur conflit |
| `infrastructure/web/dossier/DossierExceptionHandler.java` | `duplicates` dans le 409 |
| `infrastructure/mcp/tool/CreateDossierTool.java`, `DefineDossierTypeTool.java` | essai à blanc avec pré-contrôle ; description de `unique` ; doublons dans le refus |
| `infrastructure/config/DossierConfig.java` | câblage du port |
| `db/changelog/changes/037-dossier-unique-value.yaml` | table |
| `ArchitectureRulesTest` | `DossierUniqueKeys` dans la liste blanche de `Dossier.fields()` |
| `openapi/openapi.yaml` | `FieldDefinition.unique` |

## Écarts avec la conception

- **Clés recalculées par les règles** (`DossierUniqueKeys`) au lieu des valeurs stockées, à l'écriture comme à la
  reconstruction. Trouvé en écrivant A5 : un champ calculé unique ajouté à un type existant n'a pas de valeur stockée, et un
  changement de statut aurait effacé la clé posée par la reconstruction. Prouvé par
  `DossierUniqueTypeIT.aComputedUniqueFieldAddedLater…` (rouge si l'on retire le recalcul).
- **Reconstruction complète** du type à chaque enregistrement d'un type qui a un champ unique (suppression puis insertion),
  au lieu d'un différentiel par champ : plus simple, et couvre le changement de formule. Le contrôle de compatibilité charge
  déjà tous les dossiers du type à chaque mise à jour : même ordre de coût.
- **Code `INVALID_VALUE`** pour une valeur unique trop longue, au lieu de `TOO_LONG` : convention existante du validateur.
- **Message de `DossierFieldValidationException` enrichi** : l'appelant MCP ne recevait que « Dossier field validation
  failed ». Le `detail` du 400 REST en profite.
- **Essai à blanc MCP** (`create_dossier`, `dryRun`) : pré-contrôle ajouté, sinon il annonçait « valide » un doublon.
- Le tenant figure dans la clé primaire, par convention des tables du schéma (changeSet 036).

## Cas limites traités

- Dossier dont le type est introuvable : aucune clé (définitions vides).
- Valeur vide, blanche ou absente : aucune clé.
- Le dossier corrigé ne se gêne pas lui-même (pré-contrôle et table).
- Insertion des clés dans un ordre stable : deux transactions concurrentes ne s'interbloquent pas.
- Détenteur introuvable après conflit (supprimé entre-temps) : référence « ? ».

## Lot B — brique « Rechercher des dossiers » (livré)

| Fichier | Rôle |
|---|---|
| `domain/workflow/palette/DossierSearchSpec.java` | grammaire de `searchCriteria` (1 à 3 lignes, sources `dossierField:`/`variable:`) |
| `domain/workflow/palette/BuildingBlockCatalog.java`, `BlockParameter.java`, `BlockAttributeRules.java` | bloc `search-dossiers`, paramètre multiligne, sorties validées comme `formulaResult`, distinctes, lecture littérale |
| `domain/workflow/AllowedBpmnDelegates.java` | `dossierSearchTask` |
| `domain/dossier/port/in/SearchSimilarDossiersUseCase.java`, `usecase/SearchSimilarDossiersUseCaseImpl.java` | règles R2-R8, conversion typée |
| `domain/dossier/port/out/DossierSearchPort.java` | `findOpenMatches`, `MatchSummary` |
| `infrastructure/persistence/dossier/JpaDossierSearchAdapter.java`, `DossierSpecifications.java` | requête : type, statuts ouverts, exclusion, plus ancien |
| `infrastructure/flowable/DossierSearchDelegate.java` | délégué |
| `infrastructure/config/DossierConfig.java`, `ArchitectureRulesTest` | câblage ; liste blanche `Dossier.fields()` |

Écarts avec la conception : une valeur de `SELECT` hors options est cherchée telle quelle (revue adversariale) ; la
conversion des valeurs est propre au use case (pas de réutilisation de `FieldCondition`, dont la borne de 200 caractères
produirait de faux 0 pour un texte long).

## Lot C — interface (livré)

| Fichier | Rôle |
|---|---|
| `frontend/src/components/dossier/FieldDefinitionDefsEditor.vue` | case « Valeur unique », désactivée hors `STRING`/`NUMBER`/`DATE` et sur un champ sensible ; la raison est un texte visible relié par `aria-describedby` ; le drapeau est retiré si le champ quitte ces cas |
| `frontend/src/dossier/validateFieldDefinitions.ts` | miroir U2, U3, U4, U13 et refus sur un sous-champ |
| `frontend/src/api/types.ts`, `api/http.ts` | `FieldError`, `SchemaDuplicate` (avec `referenceCount`), `fieldErrorsOf`, `schemaDuplicatesOf` |
| `frontend/src/dossier/fieldErrors.ts` | texte affiché : `UNIQUE` traduit (référence seule), autres codes = message serveur |
| `frontend/src/components/dossier/DynamicFields.vue`, `DynamicFieldInput.vue` | erreur sous le champ, hors du `<label>`, `role="alert"` ; `aria-invalid` et `aria-describedby` sur la saisie, sur `<output>` (champ calculé) et sur `MONEY`, `EXTERNAL_REF`, `REPEATABLE_GROUP` |
| `CreateDossierView.vue` | `errors[]` sous le champ ; bannière `role="alert"` pour un corps sans `errors[]` ou un champ masqué ou inconnu ; rétractation quand la liste hors écran redevient vide |
| `DossierFieldsTaskForm.vue` | bannière propre au formulaire (`task-form-banner`). Après un PATCH accepté, seules les erreurs dont le champ est absent du correctif sont republées ; la bannière tombe s'il n'en reste aucune. Un correctif vide ne la retire pas |
| `MobileTasksView.vue`, `DossierDetailView.vue`, `TaskList.vue` | ne recopient plus ce `null` dans la bannière de page ; la fiche garde la bannière de l'enregistrement des champs |
| `DossierFieldsEditor.vue`, `DossierDetailView.vue` | écran « corriger » : `errors[]` sous le champ, `detail` seulement sans `errors[]`, bannière réservée au champ masqué ou inconnu, phrase traduite |
| `DossierTypesAdminView.vue` | 409 : un paragraphe pour `detail`, une liste pour les doublons (interpolation, pas de `v-html`) ; la valeur n'est pas découpée ; au plus 20 références, avec le total |
| `DossierUniqueKeys.java`, `UniqueDuplicate.java` | les références d'une valeur sont bornées à 20 ; `referenceCount` et l'ensemble des détenteurs restent complets |
| `blockDiagnostics.ts` | critères, noms réservés, sorties distinctes, plafond `maxLength` et caractères de contrôle de `search-dossiers` |

Écarts avec la conception : l'identifiant de brique est `search-dossiers` (lot B), pas `dossier-search`. La case n'est
proposée que dans l'éditeur de champs de dossier (`mode === 'fields'`). Sur la création, la tâche et l'écran
« corriger », le message serveur anglais d'un `UNIQUE` (`field UNIQUE (Value already used by dossier REF)`) n'est pas
affiché : la référence n'apparaît que dans la phrase traduite, sous le champ s'il est visible, dans la bannière s'il
est masqué ou inconnu. Une valeur en double du refus 409 est montrée entière, y compris si elle contient un retour
à la ligne. Au-delà de 20 valeurs, le `detail` du 409 indique le total (`duplicateCount`) et le nombre cité. Le golden
OpenAPI documente `duplicates`, `referenceCount` et `duplicateCount`. La raison d'une case inactive est hors du
`<label>`, lue une seule fois via `aria-describedby`. Les bannières de la fiche et de Mes tâches ont `role="alert"`.
Un enregistrement accepté qui envoie `null` pour un champ masqué retire l'erreur de ce champ de la bannière de la tâche.
Une erreur hors schéma absente du correctif, et un envoi sans aucun changement, la laissent affichée.

## Suites

- Lot D : exemple riverain, guides, clôture du §102, vidage de `docs/ai/`.
