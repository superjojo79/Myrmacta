---
phase: planning
title: Planning — notifier-dossier-et-brouillons
status: draft
created: 2026-10-16
feature: notifier-dossier-et-brouillons
todo_ref: TODO.md §93
---

# Planning — notifier-dossier-et-brouillons

Chaque tâche suit le TDD : test rouge, code, puis test vert. Les trois volets sont indépendants. On les fait dans l'ordre
A, B, C, avec un commit par tâche.

## Volet A — §8 références des brouillons

- [x] **A1.** `ReferenceStatus` (domaine) et `BlockReferenceLookupPort.status(...)`. `exists` devient une méthode par défaut
  (`status == ACTIVE`). Tests du port simulé.
- [x] **A2.** `BlockReferenceLookupAdapter.status` selon la table du design. Tests de l'adaptateur (modèle, connecteur,
  webhook, type de dossier).
- [x] **A3.** `BlockReferenceVerifier.verify(..., Mode)` renvoie `List<InactiveBlockReference>`, et la surcharge stricte est
  conservée. `BlockReferenceVerifierTest`.
- [x] **A4.** `DeployBpmnProcessUseCase.validateDraft`, puis `ProposeDraftUseCase.validateBpmn` (nouveau type de retour) et
  `Proposal.inactiveReferences`. Tests des use cases (proposition acceptée, publication refusée puis acceptée).
- [x] **A5.** Appelants :
  - `DeployWorkflowTool` et `DraftToolResults` (`inactiveReferences` et message) ;
  - `CompileParcoursUseCaseImpl` (`warnings`) ;
  - `ImportConfigurationUseCaseImpl` (compilation seulement).

  Tests MCP et parcours.

## Volet B — §3 destinataire de « Notifier »

- [x] **B1.** `BlockAttributeRules` : règle `notificationTo` (forme de source) et texte d'aide du catalogue.
  `BlockAttributeRulesTest`, et `BuildingBlockCatalogTest` si un instantané couvre l'aide.
- [x] **B2.** `NotificationDispatchTask` : résolution depuis l'attribut littéral seulement, via `PortalRecipientResolverPort`.
  Valeur vide ou non textuelle → `NotificationValidationException`. Commentaires du port et de l'adaptateur mis à jour.
  `NotificationDispatchTaskTest`.
- [x] **B3.** Test d'intégration Flowable : `dossierField:email` produit un e-mail dans l'outbox. `ArchitectureRulesTest` vert.

## Volet C — variables de démarrage

- [x] **C1.** `startVariables.ts` (validation pure) et `startVariables.spec.ts`.
- [x] **C2.** `StartWorkflowDialog.vue` (section repliée, lignes, erreurs, `emit('start', key, variables)`), i18n,
  `StartWorkflowDialog.spec.ts`.
- [x] **C3.** `DossierDetailView.vue` transmet `variables`. Spec et `componentCoverage.spec.ts` si besoin.
- [x] **C4.** Playwright `pilotage.spec.ts` : variable transmise ; nom réservé bloqué.

## Documentation et clôture

- [x] **D1.** `GUIDE-CONCEPTION-BPMN.md` (sources de `notificationTo`, règle des brouillons), `GUIDE-UTILISATEUR.md`
  (variables de démarrage), `analysis doc/15-serveur-mcp.md` (`inactiveReferences`).
- [x] **D2.** `TODO.md` §93 (énoncé, décisions D-DRAFT-REF-STATUS, D-NOTIF-SOURCE-LITERAL, D-NOTIF-REUSE-PORTAL-SOURCE, champ
  « Impacte »), `DEFERRED-FEATURES.md` §7 (statuts ; variables de démarrage déclarées ajoutées au backlog).
- [ ] **D3.** Une fois la PR #172 mergée : mettre à jour l'exemple `exemples/autorisation-riverain/` (`dossierField:email`,
  suppression de `emailConnu` et de la variable de démarrage, DIFFICULTES §3 et §8), avec un rebase sur `main`.
- [ ] **D4.** `./mvnw verify` et `npm run test:all` verts, puis `CHANGELOG.md` à l'ouverture de la PR.

## Risques

- **Volet A : changement de signature de `validateBpmn`.** Trois appelants et leurs tests simulés sont concernés. Le
  compilateur les signale tous.
- **Volet B : tests de bout en bout Flowable lents.** On réutilise un test d'intégration existant du portail ou des
  notifications plutôt que d'en créer un nouveau.
- **D3 : dépend de la PR #172.** Si elle n'est pas mergée à temps, D3 part dans une PR de suivi.
