---
phase: design
title: Design — LC5 concepteur de parcours
status: reviewed
created: 2026-10-01
feature: parcours-concepteur
lot: LC5
todo_ref: TODO.md §88
---

# Design — LC5

## Architecture
- Domaine `domain/parcours` : `ParcoursDefinition` (statuts, statuts de dossier, étapes), `ParcoursCompiler` (fonction pure, statique),
  ports `ManageParcoursUseCase` / `CompileParcoursUseCase`, port sortant `ParcoursRepository`.
- Persistance : table `parcours` (migration 034), définition en JSON, verrou optimiste par `version`.
- Compilation : `CompileParcoursUseCaseImpl` → `ProposeDraftUseCase.proposeBpmn` (chemin commun D6) ; mode à blanc = `validateBpmn`.
- Web : `/api/parcours` (GET liste/détail, PUT, DELETE, POST `/{key}/compile`, POST `/preview`) ; MCP : `ParcoursTools`.
- Front : Admin → Parcours (éditeur par étapes, aperçu BPMN, avertissements, lien vers Brouillons).

## Génération
Une étape = un élément BPMN ; id d'étape = id BPMN ; flux `flow_<de>_<vers>` ; une étape sans `next` passe à la suivante de la liste.
Décision = passerelle exclusive, conditions `${variable op littéral}` (grammaire existante), flux `default`. Document = génération puis
rattachement (deux tâches). Diagramme BPMNDI posé automatiquement pour que bpmn-js ouvre le brouillon.

## Sécurité
Toute valeur est validée (forme, longueur, caractères interdits) puis échappée XML ; seuls les paramètres connus par type d'étape sont
acceptés (`allowOnly`) ; aucune expression libre ; le parseur du déploiement refait tous les contrôles à la compilation.
