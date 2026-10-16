---
phase: testing
title: Testing — notifier-dossier-et-brouillons
status: draft
created: 2026-10-16
feature: notifier-dossier-et-brouillons
---

# Testing — notifier-dossier-et-brouillons

Méthode : TDD, chaque scénario écrit en rouge avant le code. Objectif de couverture : 100 % des nouvelles branches, et
les seuils Jacoco du `pom.xml` sont tenus.

## Volet A — références des brouillons

`BlockReferenceVerifierTest` (domaine, port simulé) :
- [x] Mode `STRICT` + `ACTIVE` → aucune erreur, liste vide.
- [x] Mode `STRICT` + `INACTIVE` → `UnknownBlockReferenceException` (message inchangé).
- [x] Mode `DRAFT` + `INACTIVE` → aucune erreur, la référence est dans la liste (activité, paramètre, source, valeur).
- [x] Mode `DRAFT` + `MISSING` → `UnknownBlockReferenceException`.
- [x] `workflowStatuses` et `workflowTypes == processKey` : comportement inchangé dans les deux modes.
- [x] La surcharge à trois arguments reste stricte.

`BlockReferenceLookupAdapter` (test d'intégration avec dépôts réels ou simulés, selon les tests existants) :
- [x] Modèle avec une version `ACTIVE` → `ACTIVE` ; versions toutes en brouillon → `INACTIVE` ; aucune → `MISSING` ;
  **ajouté** : seulement archivé → `MISSING`.
- [x] Connecteur de rôle service désactivé → `INACTIVE` ; rôle non service → `MISSING` ; type inconnu → `MISSING`.
- [x] Webhook désactivé → `INACTIVE` ; supprimé → `MISSING`.
- [x] Type de dossier inactif → `INACTIVE`.
- [x] `exists` reste vrai seulement pour `ACTIVE`.

Use cases :
- [x] `DeployBpmnProcessUseCaseImpl.validateDraft` : modèle inactif → la liste le contient ; les contrôles DMN, portail et
  signature restent bloquants.
- [x] `DeployBpmnProcessUseCaseImpl.deploy` et `validate` : modèle inactif → refus (non-régression).
- [x] `ProposeDraftUseCaseImpl.proposeBpmn` : modèle inactif → brouillon enregistré, `Proposal.inactiveReferences` rempli.
- [x] `ManageDraftsUseCaseImpl.publish` : brouillon qui référence un modèle inactif → refus ; après activation → publié.

MCP :
- [x] `DeployWorkflowToolTest` : `dryRun` avec modèle inactif → `VALID` + `inactiveReferences` + message ; proposition →
  `DRAFT_PENDING_PUBLICATION` + `inactiveReferences` ; sans référence inactive → pas de clé `inactiveReferences`.
- [x] Publication directe avec modèle inactif → erreur de publication, brouillon conservé.

Parcours et import :
- [x] La compilation d'un parcours qui référence un modèle en brouillon réussit (brouillon déposé), et `warnings` contient la
  référence inactive.
- [x] ~~L'import de configuration d'un workflow qui référence un modèle importé en brouillon réussit.~~ **Invalidé** : l'import
  ne transporte pas de templates (`ArtifactType`). Il passe par `validateBpmn`, donc par le mode brouillon, et
  `ConfigurationPromotionUseCaseTest` reste vert (25 tests).

## Volet B — destinataire de « Notifier »

`BlockAttributeRulesTest` :
- [x] `notificationTo="dossierField:email"` et `"variable:adresse"` acceptés.
- [x] `"dossierField:"`, `"dossierField:a.b"`, `"variable:1x"` refusés avec un message explicite.
- [x] `"contact@example.org"` accepté (non-régression).

`NotificationDispatchTaskTest` :
- [x] Attribut `dossierField:email`, champ renseigné → `send` appelé avec `to` = valeur du champ.
- [x] Attribut `dossierField:email`, champ absent, nul ou blanc → `NotificationValidationException`, message qui nomme la
  source, sans valeur.
- [x] Attribut `variable:adresse` → lit la variable ; variable absente → exception.
- [x] Valeur non textuelle → exception.
- [x] **Ajouté** : `dossierField:` relu **à jour** (adresse corrigée après le démarrage) — `NotifierRecipientFromDossierIT`.
- [x] **Ajouté** : diagnostic de la palette (`blockDiagnostics.spec.ts`) — source mal formée signalée, l'avertissement
  « e-mail littéral » propose `dossierField:`.
- [x] **D-NOTIF-SOURCE-LITERAL** : pas d'attribut, et une variable `notificationTo="dossierField:email"` → `to` vaut la chaîne
  littérale, aucune résolution.
- [x] Attribut littéral `contact@example.org` → inchangé.

Intégration :
- [x] Test de bout en bout Flowable (dans le style des tests existants de `NotificationDispatchTask` ou du portail) :
  déploiement d'un BPMN avec `notificationTo="dossierField:email"`, démarrage sur un dossier, puis e-mail présent dans
  l'outbox avec la bonne adresse.
- [x] `ArchitectureRulesTest` vert, sans élargir la liste blanche.

## Volet C — dialogue de démarrage

Vitest :
- [x] `startVariables.spec.ts` : nom valide, invalide, réservé, en doublon ; lignes vides ignorées ; valeur conservée telle
  quelle ; nom `trim`.
- [x] `StartWorkflowDialog.spec.ts` : section repliée par défaut ; ajout et suppression de lignes ; `start` émis avec
  `(key, {})` sans variable et avec `(key, { priorite: 'haute' })` avec une variable ; erreur affichée et aucun émis pour
  `dossierId`.
- [x] La vue parente transmet `variables` à `startDossierWorkflow`.

Playwright (`frontend/e2e/pilotage.spec.ts`) :
- [x] Démarrer un workflow avec une variable : la requête `POST /api/dossiers/{id}/workflows` contient
  `variables.priorite`.
- [x] Nom réservé : message d'erreur visible, aucune requête envoyée.

## Correctifs de la revue adversariale

- [x] `FlowableSignalConnectorTest.execute_refusesReservedPlatformVariablesAndSignalsNothing` (I7).
- [x] `ManualStartOrchestratorTest` : `documentType` non autorisé refusé avant toute réservation, autorisé accepté (I8).
- [x] `WorkflowExceptionHandlerTest.handlesAFailedNotificationStep_as422WithTheSourceNamed` (M1).
- [x] `DeployWorkflowToolTest.directPublicationIsSkippedWhileAReferencedResourceIsInactive` (L1) ; forme de `draft` (I4).
- [x] `ConfigurationPromotionUseCaseTest.thePlanOfABpmnReferencingAnInactiveResourceSaysPublicationWaitsForItsActivation` (L2).
- [x] `BlockAttributesDelegatesTest` : adresse unique pour une source et pour la variable, littéral libre, `dossierId` invalide
  (L3, I2).

## Vérifications finales

- [ ] `./mvnw verify` vert (tests, IT, ArchUnit, Jacoco).
- [ ] `cd frontend && npm run test:all` vert.
- [ ] Golden OpenAPI inchangé.

## Résultats

Tests ciblés verts au fil des tâches (rapports surefire) :
- `BlockReferenceVerifierTest` 9, `BlockReferenceLookupAdapterTest` 11, `DeployBpmnProcessUseCaseImplTest` 19,
  `DraftUseCasesTest` 26, `DeployWorkflowToolTest` 11, `ParcoursApiTest` 10 ;
- `BlockAttributeRulesTest` 15, `BlockAttributesDelegatesTest` 10, `NotificationDispatchTaskTest` 3,
  `NotifierRecipientFromDossierIT` 2, `ArchitectureRulesTest` 10 ;
- Vitest 711 (124 fichiers) ; Playwright `E2E-4`, `E2E-START-VARS-1`, `E2E-START-VARS-2`.

Suites complètes : voir la section « Vérification finale » ci-dessous.
