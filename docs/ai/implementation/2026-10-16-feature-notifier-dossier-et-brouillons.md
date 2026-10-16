---
phase: implementation
title: Implementation — notifier-dossier-et-brouillons
status: in-progress
created: 2026-10-16
feature: notifier-dossier-et-brouillons
todo_ref: TODO.md §93
---

# Implementation — notifier-dossier-et-brouillons

## Volet A — références des brouillons (D-DRAFT-REF-STATUS)

| Fichier | Changement |
|---|---|
| `domain/workflow/palette/port/out/ReferenceStatus.java` | nouveau : `ACTIVE`, `INACTIVE`, `MISSING` |
| `domain/workflow/palette/port/out/BlockReferenceLookupPort.java` | `status(...)` ; `exists` devient une méthode par défaut (`status == ACTIVE`) |
| `infrastructure/palette/BlockReferenceLookupAdapter.java` | `status` par source ; `isServiceType` extrait de `isUsableService` |
| `domain/workflow/palette/InactiveBlockReference.java` | nouveau record |
| `domain/workflow/palette/BlockReferenceVerifier.java` | `Mode {STRICT, DRAFT}`, renvoie les références inactives ; surcharge stricte conservée |
| `domain/workflow/port/in/DeployBpmnProcessUseCase.java` + `usecase/DeployBpmnProcessUseCaseImpl.java` | `validateDraft` ; `validate` et `deploy` inchangés (stricts) |
| `domain/workflow/port/in/ProposeDraftUseCase.java` + `usecase/ProposeDraftUseCaseImpl.java` | `validateBpmn` renvoie les références inactives ; `Proposal.inactiveReferences` (constructeur à 4 arguments conservé) |
| `domain/parcours/usecase/CompileParcoursUseCaseImpl.java` | références inactives ajoutées aux `warnings` (compile, dryRun, preview) |
| `infrastructure/mcp/tool/DraftToolResults.java`, `DeployWorkflowTool.java` | `inactiveReferences` + `inactiveReferencesMessage` ; description de l'outil |

**Écart au design.** Un template qui n'a que des versions **archivées** est `MISSING`, pas `INACTIVE` : un template retiré
n'a pas à revenir par un brouillon. Seule une version `DRAFT` le rend `INACTIVE`.

**Import de configuration.** Le lot n'importe pas de templates (`ArtifactType`) : rien à adapter, il bénéficie du mode
brouillon via `validateBpmn`.

## Volet B — destinataire de « Notifier » (D-NOTIF-SOURCE-LITERAL)

| Fichier | Changement |
|---|---|
| `domain/workflow/portal/model/PortalRecipientSource.java` | `hasSourcePrefix` ; commentaire (sert aussi à Notifier) |
| `domain/workflow/palette/BlockAttributeRules.java` | `checkRecipient` : source mal formée → `InvalidBpmnException` |
| `domain/workflow/palette/BuildingBlockCatalog.java` | aide du paramètre `notificationTo` |
| `infrastructure/flowable/NotificationDispatchTask.java` | `recipient(...)` : source lue dans l'attribut littéral seulement ; `dossierField` via `PortalRecipientResolverPort`, `variable` via `execution.getVariable` |
| `infrastructure/flowable/PortalRecipientResolverAdapter.java` | commentaire |
| `frontend/.../bpmn-blocks/blockDiagnostics.ts` | source mal formée signalée ; l'avertissement « e-mail littéral » propose `dossierField:` |

**Écart au design.** `variable:<nom>` est lu par `execution.getVariable` et non par le résolveur du portail
(`RuntimeService.getVariable`) : la valeur posée plus tôt dans la même transaction est visible, et les variables locales
d'un sous-processus sont respectées.

**Cas limites traités.** Valeur nulle, vide après `strip`, ou non textuelle (liste, nombre) → `NotificationValidationException`
nommant la source ; `dossierId` absent → même exception ; adresse rognée.

## Volet C — variables de démarrage

| Fichier | Changement |
|---|---|
| `frontend/src/components/workflow/startVariables.ts` | validation pure (identifiant, réservés, doublons, lignes vides) |
| `frontend/src/components/workflow/StartWorkflowDialog.vue` | section repliée, lignes nom/valeur, erreurs, `emit('start', key, variables)`, remise à zéro à l'ouverture |
| `frontend/src/views/pilotage/DossierDetailView.vue` | `onStart(key, variables)` → `store.startWorkflow(key, variables)` |
| `frontend/src/i18n/messages/{fr,en}/workflow.ts` | `workflow.startDialog.variables.*` |

## Documentation

`GUIDE-CONCEPTION-BPMN.md`, `GUIDE-UTILISATEUR.md`, `analysis doc/15-serveur-mcp.md`, `TODO.md` §93.

**Renumérotation.** Le cadrage visait TODO §92, déjà pris par l'assignation des tâches (PR #171) : le lot est §93.

## Suivis

- Mise à jour de l'exemple `exemples/autorisation-riverain/` et de `DEFERRED-FEATURES.md` §7 : après le merge de la PR #172
  (ces fichiers n'existent pas encore sur `main`).
- Variables de démarrage **déclarées** par workflow : backlog.
