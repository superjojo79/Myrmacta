---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
---

# Requirements & Problem Understanding

> Feature `questions-utilisateur-taches`. Origine : question utilisateur (2026-09-05) — cadrage
> initial dans [docs/specs/23-questions-utilisateur-taches.md](../../specs/23-questions-utilisateur-taches.md),
> TODO §55. Toutes les décisions de cadrage ci-dessous ont été tranchées explicitement par
> l'utilisateur via `AskUserQuestion` avant rédaction de ce document (2 séries de questions,
> 7 décisions au total).

## Problem Statement

Le mécanisme `flowable:formKey` (TODO §52, livré) permet déjà à un `userTask` BPMN d'afficher un
formulaire avant complétion, via un registre frontend (`taskFormRegistry.ts`) volontairement
extensible — mais seulement en écrivant un **nouveau composant Vue codé à la main** pour chaque
besoin (`dossier-fields`, `document-generation`). Un concepteur de workflow qui veut poser une
simple question ou un choix à l'utilisateur, **indépendamment des données du dossier**, n'a
aujourd'hui aucun moyen de le faire sans une intervention développeur.

Affecte : les concepteurs de workflow (rôles `TENANT_ADMIN`/`WORKFLOW_ADMIN`) qui conçoivent des
processus BPMN, et par ricochet les porteurs de tâches (tout rôle capable de compléter une
`userTask`) qui répondront à ces questions.

## Goals & Objectives

**Objectifs principaux**

- Permettre à un `TENANT_ADMIN`/`WORKFLOW_ADMIN` de définir, sans code, un schéma de
  mini-formulaire (une ou plusieurs questions : texte, nombre, date, choix) via un nouvel écran
  d'admin.
- Permettre à un concepteur BPMN de rattacher ce schéma à un `userTask` via un nouveau kind
  `formKey` (`user-question:<schemaKey>`), au même titre que `dossier-fields`/`document-generation`.
- La réponse de l'utilisateur part comme variables de process Flowable (une par champ, nom =
  `FieldDefinition.name`) — **jamais écrite dans le dossier** — et reste consultable après coup via
  une nouvelle traçabilité dédiée.

**Objectifs secondaires**

- Détecter à la sauvegarde du schéma une collision entre un nom de champ choisi et un nom de
  variable de process déjà réservé/connu ailleurs dans le produit (D-USERQ-VAR-COLLISION).

**Non-goals (hors périmètre V1)**

- Pas de génération dynamique des options à partir d'un endpoint métier (contrairement à
  `document-generation`) — les options sont **statiques**, saisies dans le schéma admin.
- Pas de versionnage du schéma : une modification s'applique immédiatement, y compris aux
  instances de process déjà démarrées référençant le même `schemaKey` (D-USERQ-SCHEMA-VERSIONING).
- Pas d'agrégation/reporting construit sur la traçabilité des réponses — seule la donnée brute est
  stockée, consultable au besoin (D-USERQ-TRACE-SCOPE). Pas de lien avec un futur module
  reporting/SLA à ce stade.
- Pas d'internationalisation des libellés (question/options) — un seul texte par schéma
  (D-USERQ-I18N), cohérent avec `DossierType.fieldDefinitions` aujourd'hui.
- Pas de mécanisme d'extension XML structurée dans le BPMN (`extensionElements`) — le schéma vit
  entièrement dans une nouvelle entité admin, le BPMN ne porte qu'une référence stable
  (`schemaKey`).

## User Stories & Use Cases

- En tant que `WORKFLOW_ADMIN`, je veux créer un schéma de questions (ex. « Motif de rejet » avec
  un champ `SELECT` à options fixes) dans un écran d'admin dédié, afin de le réutiliser sur
  plusieurs `userTask` sans redévelopper un composant à chaque fois.
- En tant que concepteur BPMN, je veux rattacher un `userTask` à un schéma existant via
  `formKey = "user-question:<schemaKey>"`, afin que le porteur de la tâche voie le formulaire
  correspondant avant de compléter.
- En tant que porteur de tâche, je veux répondre au mini-formulaire (un ou plusieurs champs) avant
  de compléter la tâche ; ma réponse part comme variables de process, utilisables ensuite par une
  passerelle exclusive ou un `serviceTask` en aval — sans jamais modifier le dossier.
- En tant qu'auditeur/administrateur, je veux pouvoir retrouver après coup la réponse donnée à une
  tâche donnée (dossier, tâche, process instance, date, auteur), via la nouvelle traçabilité
  dédiée.
- **Cas limite** : un schéma est modifié ou son `schemaKey` supprimé pendant qu'un process en
  cours référence encore l'ancien `formKey` → dernière version affichée si le schéma existe
  toujours (pas de version figée) ; dégradation documentée (comportement `D-FORM-BACKWARD-COMPAT`,
  TODO §52) si le `schemaKey` n'existe plus.
- **Cas limite** : un `FieldDefinition.name` du schéma collisionne avec un nom de variable de
  process déjà réservé/connu (`documentType`, `documentVariant`, `documentLocale`, et les 4
  variables plateforme de `WorkflowVariables`) → refusé à la sauvegarde du schéma, avant tout
  déploiement BPMN.

## Success Criteria

- Un `TENANT_ADMIN`/`WORKFLOW_ADMIN` peut créer, lister, modifier et supprimer un schéma de
  questions dans un nouvel écran d'admin, avec les mêmes types de champ que
  `FieldDefinitionDefsEditor.vue` (`STRING`, `NUMBER`, `DATE`, `BOOLEAN`, `SELECT`, `FILE` —
  `FILE` documenté comme non pertinent ici, à exclure de l'UI si non applicable, voir Design).
- Un `userTask` déclarant `formKey = "user-question:<schemaKey>"` affiche le mini-formulaire
  correspondant avant de permettre la complétion ; la soumission produit une variable de process
  par champ, lisible par une passerelle exclusive/`serviceTask` en aval.
- Aucune donnée du dossier n'est lue ni modifiée par ce kind (vérifiable : le composant frontend
  ne reçoit/n'utilise `dossierId` que pour la traçabilité, jamais pour un appel `PATCH
  .../fields`).
- La réponse est historisée (nouvelle table) et consultable après complétion de la tâche.
- La sauvegarde d'un schéma dont un `FieldDefinition.name` collisionne avec un nom réservé/connu
  est rejetée avec un message explicite.
- `formKey` reste opaque côté backend pour tout ce qui concerne le **choix de rendu/logique
  métier** (aucun `switch`/`enum` sur le kind pour cela) — seul le frontend (`taskFormRegistry.ts`)
  en décide, comme pour TODO §52. **Exception bornée et documentée (D-USERQ-FORMKEY-PARSING,
  Phase 3)** : le backend lit `formKey` pour reconnaître le préfixe `user-question:` et en
  extraire le `schemaKey` à valider — nécessaire pour ne pas faire confiance à une valeur envoyée
  par le client (aurait réintroduit la faille D-TASK-FORM-DOCTYPE-SCOPE déjà corrigée sur
  `document-generation`, TODO §52 Phase 9).
- `./mvnw verify` vert, Jacoco tenu ; Vitest et Playwright verts (nouveaux scénarios `E2E-*` pour
  ce kind, zéro régression sur les kinds existants).

## Constraints & Assumptions

- Architecture hexagonale : nouvelle entité domaine pure (package à trancher en design), ports
  in/out, adaptateurs JPA — aucune annotation Spring/JPA/Flowable dans `domain`.
- Multi-tenant : nouvelle table scopée par tenant (schéma PostgreSQL dédié), pas de résolution
  dynamique du tenant.
- Réutilise `FieldDefinition`/`FieldType` (`domain.dossier.model`) tel quel (D-USERQ-FIELD-REUSE)
  — dépendance `domain.workflow` → `domain.dossier.model` assumée et documentée en design, pas
  contournée par une duplication.
- RBAC : écran admin réservé à `TENANT_ADMIN`+`WORKFLOW_ADMIN` (cohérent avec la gestion BPMN
  existante — `WorkflowTypeConfigController`, `WorkflowBpmnDeploymentController`).
- Validation serveur obligatoire à la complétion de tâche (champs requis, valeur dans `options`
  pour un `SELECT`) — même pattern que `CompleteTaskUseCaseImpl.assertDocumentTypeAllowed`, ne pas
  faire confiance au frontend seul.
- Hypothèse : le porteur de tâche a déjà les permissions nécessaires pour compléter la tâche
  (mécanisme générique existant) — ce kind n'ajoute pas de contrôle d'accès supplémentaire au-delà
  de la validation de schéma.
- **`CompleteTaskUseCase.complete(...)` (port existant) doit évoluer** pour recevoir l'identité de
  l'appelant (`actor`/`userId`), alimentée depuis `Authentication` au niveau du contrôleur —
  actuellement non transmise, nécessaire pour renseigner `submittedBy` sur `TaskQuestionAnswer`
  (trouvé en revue Phase 2 : gap réel, pas une simple addition). Port interne, pas d'API publique
  impactée.
- **L'endpoint runtime `GET .../{schemaKey}/runtime` doit recevoir `taskId`** et vérifier que le
  `formKey` de cette tâche référence bien ce `schemaKey` avant de servir le schéma — un
  `TaskQuestionSchema` n'a, par construction, aucun `DossierType`/permission `VIEW` à vérifier
  (contrairement à `document-generation`), ce contrôle est donc le seul rempart contre un balayage
  de tous les `schemaKey` existants par un utilisateur authentifié quelconque (trouvé en revue
  Phase 2).

## Questions & Open Items

Toutes les décisions de cadrage identifiées par l'analyse initiale, les 2 gaps trouvés en revue
Phase 2, et les 3 points trouvés en revue Phase 3 (Design — voir
[document design](../design/2026-09-05-feature-questions-utilisateur-taches.md)), ont été
tranchés (voir tableau). Aucun point matériel ouvert avant planification.

| Décision | Choix retenu |
|---|---|
| D-USERQ-FIELD-REUSE | Réutiliser `FieldDefinition`/`FieldType` tel quel |
| D-USERQ-VAR-COLLISION | Garde-fou : validation à la sauvegarde du schéma |
| D-USERQ-TRACE-SCOPE | Réponses brutes uniquement, pas de lien reporting/SLA |
| D-USERQ-SCHEMA-VERSIONING | Non-versionné, modification directe |
| D-USERQ-RBAC-ADMIN | `TENANT_ADMIN` + `WORKFLOW_ADMIN` |
| D-USERQ-I18N | Pas d'i18n, un seul texte par schéma |
| Nommage | Kind `user-question`, entité `TaskQuestionSchema` |
| D-USERQ-SUBMITTED-BY (Phase 2) | `CompleteTaskUseCase.complete(...)` évolue pour porter l'identité de l'appelant |
| D-USERQ-RUNTIME-SCOPE (Phase 2) | Endpoint runtime scopé par `taskId` — vérifie que le `formKey` de la tâche référence le `schemaKey` demandé |
| D-USERQ-FORMKEY-PARSING (Phase 3) | Le backend lit `formKey` côté serveur pour reconnaître le préfixe `user-question:` — exception documentée et bornée au principe d'opacité, jamais utilisée pour le rendu/la logique métier |
| D-USERQ-SCHEMA-CONCURRENCY (Phase 3) | Pas de concurrence optimiste sur `TaskQuestionSchema` — aligné sur `DossierType`, pas sur `Dossier.fields()` |
