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
- [ ] Mode `STRICT` + `ACTIVE` → aucune erreur, liste vide.
- [ ] Mode `STRICT` + `INACTIVE` → `UnknownBlockReferenceException` (message inchangé).
- [ ] Mode `DRAFT` + `INACTIVE` → aucune erreur, la référence est dans la liste (activité, paramètre, source, valeur).
- [ ] Mode `DRAFT` + `MISSING` → `UnknownBlockReferenceException`.
- [ ] `workflowStatuses` et `workflowTypes == processKey` : comportement inchangé dans les deux modes.
- [ ] La surcharge à trois arguments reste stricte.

`BlockReferenceLookupAdapter` (test d'intégration avec dépôts réels ou simulés, selon les tests existants) :
- [ ] Modèle avec une version `ACTIVE` → `ACTIVE` ; versions toutes en brouillon → `INACTIVE` ; aucune → `MISSING`.
- [ ] Connecteur de rôle service désactivé → `INACTIVE` ; rôle non service → `MISSING` ; type inconnu → `MISSING`.
- [ ] Webhook désactivé → `INACTIVE` ; supprimé → `MISSING`.
- [ ] Type de dossier inactif → `INACTIVE`.
- [ ] `exists` reste vrai seulement pour `ACTIVE`.

Use cases :
- [ ] `DeployBpmnProcessUseCaseImpl.validateDraft` : modèle inactif → la liste le contient ; les contrôles DMN, portail et
  signature restent bloquants.
- [ ] `DeployBpmnProcessUseCaseImpl.deploy` et `validate` : modèle inactif → refus (non-régression).
- [ ] `ProposeDraftUseCaseImpl.proposeBpmn` : modèle inactif → brouillon enregistré, `Proposal.inactiveReferences` rempli.
- [ ] `ManageDraftsUseCaseImpl.publish` : brouillon qui référence un modèle inactif → refus ; après activation → publié.

MCP :
- [ ] `DeployWorkflowToolTest` : `dryRun` avec modèle inactif → `VALID` + `inactiveReferences` + message ; proposition →
  `DRAFT_PENDING_PUBLICATION` + `inactiveReferences` ; sans référence inactive → pas de clé `inactiveReferences`.
- [ ] Publication directe avec modèle inactif → erreur de publication, brouillon conservé.

Parcours et import :
- [ ] La compilation d'un parcours qui référence un modèle en brouillon réussit (brouillon déposé), et `warnings` contient la
  référence inactive.
- [ ] L'import de configuration d'un workflow qui référence un modèle importé en brouillon réussit.

## Volet B — destinataire de « Notifier »

`BlockAttributeRulesTest` :
- [ ] `notificationTo="dossierField:email"` et `"variable:adresse"` acceptés.
- [ ] `"dossierField:"`, `"dossierField:a.b"`, `"variable:1x"` refusés avec un message explicite.
- [ ] `"contact@example.org"` accepté (non-régression).

`NotificationDispatchTaskTest` :
- [ ] Attribut `dossierField:email`, champ renseigné → `send` appelé avec `to` = valeur du champ.
- [ ] Attribut `dossierField:email`, champ absent, nul ou blanc → `NotificationValidationException`, message qui nomme la
  source, sans valeur.
- [ ] Attribut `variable:adresse` → lit la variable ; variable absente → exception.
- [ ] Valeur non textuelle → exception.
- [ ] **D-NOTIF-SOURCE-LITERAL** : pas d'attribut, et une variable `notificationTo="dossierField:email"` → `to` vaut la chaîne
  littérale, aucune résolution.
- [ ] Attribut littéral `contact@example.org` → inchangé.

Intégration :
- [ ] Test de bout en bout Flowable (dans le style des tests existants de `NotificationDispatchTask` ou du portail) :
  déploiement d'un BPMN avec `notificationTo="dossierField:email"`, démarrage sur un dossier, puis e-mail présent dans
  l'outbox avec la bonne adresse.
- [ ] `ArchitectureRulesTest` vert, sans élargir la liste blanche.

## Volet C — dialogue de démarrage

Vitest :
- [ ] `startVariables.spec.ts` : nom valide, invalide, réservé, en doublon ; lignes vides ignorées ; valeur conservée telle
  quelle ; nom `trim`.
- [ ] `StartWorkflowDialog.spec.ts` : section repliée par défaut ; ajout et suppression de lignes ; `start` émis avec
  `(key, {})` sans variable et avec `(key, { priorite: 'haute' })` avec une variable ; erreur affichée et aucun émis pour
  `dossierId`.
- [ ] La vue parente transmet `variables` à `startDossierWorkflow`.

Playwright (`frontend/e2e/pilotage.spec.ts`) :
- [ ] Démarrer un workflow avec une variable : la requête `POST /api/dossiers/{id}/workflows` contient
  `variables.priorite`.
- [ ] Nom réservé : message d'erreur visible, aucune requête envoyée.

## Vérifications finales

- [ ] `./mvnw verify` vert (tests, IT, ArchUnit, Jacoco).
- [ ] `cd frontend && npm run test:all` vert.
- [ ] Golden OpenAPI inchangé.
