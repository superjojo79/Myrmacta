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

## Suites (hors lot A)

- Webhook entrant : une erreur de validation de champ (dont un doublon) n'est mappée par aucun gestionnaire. Voir la revue
  adversariale dans le registre (§102).
