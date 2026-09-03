---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
---

# Requirements & Problem Understanding — `formulaires-dynamiques-taches`

> Origine : question utilisateur (2026-09-01), analyse écrite
> [22-formulaires-dynamiques-taches.md](../../../analysis%20doc/22-formulaires-dynamiques-taches.md),
> TODO §52. Choisie comme prochaine feature après épuisement de `FILE-ATTENTE.md` (file vide, seul
> candidat éligible — le provisioning tenant est explicitement hors file produit). Arbitrages
> produit tranchés par l'utilisateur au lancement du cycle (2026-09-03) : voir § « Décisions
> tranchées ».

## Problem Statement
**What problem are we solving?**

- Le tuyau backend de complétion de tâche accepte déjà des variables
  (`POST /api/workflow-tasks/{taskId}/complete` → `{ variables: {...} }`, `CompleteTaskUseCaseImpl`),
  mais rien ne s'en sert : `MobileTasksView.vue`/`TaskList.vue` complètent les tâches à l'aveugle
  (`store.complete(taskId)` sans variable), sans jamais construire de formulaire.
- Aucun concept de `formKey` n'existe aujourd'hui sur `MyWorkflowTask`/`WorkflowTaskView`, ni dans
  l'éditeur BPMN visuel (`flowablePropertiesProvider.ts` ne gère aucun groupe pour `bpmn:UserTask`).
- Affecté : les utilisateurs métier (rôles `DOSSIER_MANAGER` et assimilés, porteurs de tâches
  BPMN) qui doivent aujourd'hui sortir du process (aller sur la fiche dossier pour éditer des
  champs) pour accomplir une action que le BPMN pourrait porter directement à l'étape `userTask`.
- Situation actuelle / contournement : édition de champs dossier via `DossierFieldsEditor.vue`
  (accessible à tout moment depuis la fiche dossier, indépendamment du BPMN) ; génération de
  documents entièrement automatique via le `serviceTask` `${documentGenerationTask}`, qui lit des
  variables déjà présentes à l'exécution — jamais saisies par un utilisateur en cours de process.
- Vérifié contre le code réel (pas supposé) : `flowable-form` (module de formulaires natifs
  Flowable) n'est pas sur le classpath (`pom.xml`) — seul `flowable-spring-boot-starter` est
  présent. Toute solution est donc une convention FlowForge au-dessus de `flowable:formKey`
  (attribut BPMN standard du moteur cœur), pas un mécanisme Flowable prêt à l'emploi. Confirmé par
  recherche externe : `Task.getFormKey()`/`FormService` font partie du core `flowable-engine`
  (voir tests `org.flowable.examples.taskforms.TaskFormsTest` dans le module core), indépendants du
  module optionnel `flowable-form-engine`.

## Goals & Objectives
**What do we want to achieve?**

- **Primaire** : permettre à un `userTask` BPMN de déclarer, via `flowable:formKey`, qu'il attend
  une saisie utilisateur avant complétion, et que FlowForge affiche le formulaire correspondant
  côté frontend (`MobileTasksView.vue`/`TaskList.vue`) avant d'autoriser le bouton « Compléter ».
- **Primaire** : couvrir les 2 cas confirmés par l'utilisateur — compléter des champs dossier
  (`dossier-fields`) et fournir les paramètres d'une génération de document en aval
  (`document-generation`) — sans court-circuiter les mécanismes existants
  (`PATCH /api/dossiers/{id}/fields`, `serviceTask ${documentGenerationTask}`).
- **Primaire** : exposer `flowable:formKey` dans l'éditeur BPMN visuel
  (`flowablePropertiesProvider.ts`) pour un `bpmn:UserTask`, au même patron que les groupes
  `serviceTask`/`signal` déjà présents.
- **Secondaire** : rendre le mécanisme de rendu de formulaire **extensible côté frontend** sans
  toucher au backend/plomberie Flowable pour ajouter un futur 3ᵉ kind (décision utilisateur
  D-FORM-KIND-SCOPE, voir § « Décisions tranchées »).
- **Secondaire** : filtrer le picker de templates du kind `document-generation` par type de
  dossier (décision utilisateur D-FORM-DOCGEN-TEMPLATE-SCOPE) — nécessite un nouveau lien
  `DossierType` ↔ `templateKey`, absent du modèle aujourd'hui (vérifié :
  `DocumentTemplate`/`DossierType` n'ont aucune référence croisée).
- **Non-goals** :
  - Pas de moteur de formulaire générique/schéma arbitraire (pas de 3ᵉ kind spéculatif, pas de JSON
    Schema custom) — seuls `dossier-fields` et `document-generation` sont implémentés maintenant ;
    l'extensibilité porte sur le **mécanisme d'enregistrement**, pas sur la couverture fonctionnelle.
  - Pas de réintroduction du module `flowable-form` ni d'une dépendance Maven supplémentaire.
  - Pas de nouvelle validation serveur pour `dossier-fields` : `PATCH .../fields` revalide déjà via
    `DossierFieldValidator` — aucune garde dupliquée côté formulaire de tâche.
  - Pas de migration des process BPMN existants : une tâche sans `formKey` garde le comportement
    actuel (bouton « Compléter » aveugle), sans action requise sur les diagrammes déjà déployés.

## Décisions tranchées (à l'ouverture du cycle, 2026-09-03)

| Point ouvert (TODO §52) | Décision | Conséquence modèle/design |
|---|---|---|
| D-FORM-KIND-SCOPE | **Mécanisme extensible** (pas seulement 2 kinds figés en dur) | Le backend reste agnostique : `formKey` est une chaîne opaque de bout en bout (`Task.getFormKey()` → `MyWorkflowTask.formKey`/`WorkflowTaskView.formKey` → réponse REST), aucun `enum`/`switch` de kind côté serveur. Le frontend interprète `formKey` par convention `<kind>` (ex. `dossier-fields`, `document-generation`) via un **registre** `Record<string, Component>` : ajouter un 3ᵉ kind plus tard = ajouter une entrée au registre + un composant, sans toucher `WorkflowTaskController`/`FlowableWorkflowTaskInboxAdapter`/`ListMyTasksUseCaseImpl`. Un `formKey` dont le kind n'est pas dans le registre retombe sur le comportement actuel (bouton « Compléter » aveugle) plutôt que d'échouer — **à confirmer en Phase 2/3**, voir § Questions.
| D-FORM-DOSSIER-FIELDS-PARTIAL | **Schéma complet du `DossierType`** (pas de sous-ensemble par étape) | `DynamicFields.vue` réutilisé sans modification ; aucun filtre à encoder dans le `formKey`. |
| D-FORM-DOCGEN-TEMPLATE-SCOPE | **Filtré par type de dossier** | Nouveau champ `documentTemplateKeys: List<String>` sur `DossierType`, édité depuis une nouvelle section de l'écran Admin → Types existant (patron `FieldDefinitionDefsEditor.vue`). **Liste vide = aucun template disponible** (association explicite obligatoire, décision utilisateur 2026-09-03) — `document-generation` reste indisponible sur un `DossierType` tant que son admin n'a pas configuré au moins un `templateKey`. |
| Kind de `formKey` inconnu du registre frontend | **Dégrade vers le comportement actuel** (bouton « Compléter » aveugle, pas d'erreur bloquante) | Cohérent avec D-FORM-BACKWARD-COMPAT ; aucune tâche ne peut rester bloquée par une faute de configuration `formKey`. |
| D-FORM-VALIDATION (déjà non ouvert par l'analyse) | Validation client uniquement, revalidation serveur existante suffisante | Confirmé, aucun changement. |
| D-FORM-BACKWARD-COMPAT (déjà non ouvert par l'analyse) | Tâche sans `formKey` = comportement actuel inchangé | Confirmé, à documenter explicitement dans le design. |
| Vérification technique `Task.getFormKey()` sans `flowable-form` | **Confirmé** (recherche externe, tests core `flowable-engine`) | Aucun changement de dépendance Maven nécessaire ; à reconfirmer par un test d'intégration réel en Phase 5/7 (pas de jar Flowable disponible dans ce bac à sable pour l'inspecter directement). |

## User Stories & Use Cases
**How will users interact with the solution?**

- En tant qu'**administrateur workflow** (`WORKFLOW_ADMIN`), je veux poser `flowable:formKey` sur un
  `userTask` dans l'éditeur BPMN visuel, afin de déclarer qu'une saisie est requise avant complétion.
- En tant qu'**administrateur workflow**, je veux associer un `DossierType` à un sous-ensemble de
  `templateKey` actifs, afin que le picker `document-generation` ne propose que des templates
  pertinents pour ce type de dossier.
- En tant que **porteur de tâche** (`DOSSIER_MANAGER`), sur une tâche avec `formKey=dossier-fields`,
  je veux voir le formulaire des champs du dossier lié directement dans `MobileTasksView.vue`/
  `TaskList.vue`, le remplir, puis compléter la tâche — sans devoir naviguer vers la fiche dossier.
- En tant que **porteur de tâche**, sur une tâche avec `formKey=document-generation`, je veux
  choisir un `templateKey`/`variantKey`/`locale` parmi les templates associés au type de dossier,
  puis compléter la tâche avec ces 3 variables — la génération réelle reste déclenchée par le
  `serviceTask ${documentGenerationTask}` suivant, inchangé.
- En tant que **porteur de tâche**, sur une tâche **sans** `formKey`, je continue de voir le bouton
  « Compléter » actuel, sans régression.
- **Edge cases à couvrir** :
  - `formKey` présent mais dossier introuvable / type de dossier introuvable / aucun `templateKey`
    associé au type de dossier (`documentTemplateKeys` vide) : picker affiché vide, pas d'erreur
    bloquante — le porteur de tâche voit qu'aucun template n'est disponible et doit escalader vers
    un admin plutôt que de bloquer silencieusement le process.
  - `formKey` avec un kind inconnu du registre frontend : dégrade vers le bouton « Compléter »
    actuel (décision tranchée, voir tableau ci-dessus).
  - Concurrence : le dossier a été modifié entre l'affichage du formulaire de tâche et la
    soumission (`PATCH .../fields` gère déjà `expectedVersion` — le formulaire de tâche doit
    propager cette version, pas la contourner).
  - Permission : le porteur de tâche n'a pas le droit d'éditer les champs du dossier
    (`accessPolicy`/`DossierAccessGuard`) — `PATCH .../fields` rejette déjà, le formulaire doit
    afficher l'erreur plutôt que masquer le cas.

## Success Criteria
**How will we know when we're done?**

- Un `userTask` avec `flowable:formKey=dossier-fields` affiche `DynamicFields.vue` contre le schéma
  complet du `DossierType` du dossier lié, avant le bouton « Compléter » ; la soumission appelle
  `PATCH /api/dossiers/{id}/fields` (existant, inchangé) puis complète la tâche.
- Un `userTask` avec `flowable:formKey=document-generation` affiche un sélecteur
  `templateKey`/`variantKey`/`locale` limité aux templates associés au `DossierType` du dossier lié
  ; la soumission complète la tâche avec ces 3 variables, sans appel direct à
  `GenerateDocumentUseCase` depuis le frontend.
- Une tâche sans `formKey` garde exactement le comportement actuel (zéro régression e2e).
- L'éditeur BPMN visuel permet de poser/modifier/effacer `flowable:formKey` sur un `bpmn:UserTask`,
  round-trip fidèle sur un diagramme important puis réexporté (comme les attributs `serviceTask`
  existants).
- `./mvnw verify` vert (Jacoco LINE ≥85 %/BRANCH ≥75 % tenus) ; Vitest et Playwright tous deux
  obligatoires (feature touchant le SPA) — `npm run test` et `npm run test:e2e` verts, mocks e2e mis
  à jour si les shapes API changent.
- `GUIDE-UTILISATEUR.md`/`GUIDE-CONCEPTION-BPMN.md` mis à jour si un nouvel écran admin (association
  `DossierType` ↔ templates) ou un nouveau champ visible en admin est livré.

## Constraints & Assumptions
**What limitations do we need to work within?**

- **Techniques** : architecture hexagonale non négociable — le domaine (`com.flowforge.domain`)
  reste pur, `flowable:formKey` est lu par un adaptateur infrastructure
  (`FlowableWorkflowTaskInboxAdapter`) et transporté comme `String` opaque à travers les ports.
  Pas de dépendance Flowable dans le domaine.
- **Techniques** : `flowable-form` reste hors classpath ; ne pas l'ajouter.
- **Décision (2026-09-03)** — forme du lien `DossierType` ↔ template : nouveau champ
  `documentTemplateKeys: List<String>` sur `DossierType`, édité depuis une nouvelle section de
  l'écran Admin → Types existant (patron `FieldDefinitionDefsEditor.vue`, pas de nouvel écran).
  **Liste vide = aucun template disponible** : un `DossierType` non configuré rend
  `document-generation` indisponible (picker vide) tant qu'un admin n'a pas explicitement associé
  au moins un `templateKey` — pas de fallback « tous les templates actifs », configuration
  explicite requise. Impact rétrocompatibilité : tous les `DossierType` existants démarrent avec
  une liste vide, donc `document-generation` est indisponible partout tant qu'aucune migration de
  configuration n'est faite — à documenter clairement dans `GUIDE-UTILISATEUR.md` (pas une
  régression du bouton « Compléter » aveugle existant, qui reste inchangé pour toute tâche sans
  `formKey`).
- **Décision (2026-09-03)** — un `formKey` de kind inconnu par le registre frontend dégrade vers le
  comportement actuel (bouton « Compléter » aveugle) plutôt que de bloquer la complétion, cohérent
  avec D-FORM-BACKWARD-COMPAT.
- **Business** : rôles V1 inchangés (`TENANT_ADMIN`, `WORKFLOW_ADMIN`, `DOSSIER_MANAGER`,
  `DOSSIER_VIEWER`, `API_SERVICE`) — aucun nouveau rôle nécessaire, les gardes d'accès existantes
  (`DossierAccessGuard`) suffisent.
- **Scope** : feature touchant à la fois `frontend/**` (SPA + éditeur BPMN) et le backend — Vitest
  **et** Playwright obligatoires (pas de dérogation « backend-only » possible ici, contrairement à
  plusieurs features récentes).

## Questions & Open Items
**What do we still need to clarify?**

Tous les points produit matériels ont été tranchés par l'utilisateur au lancement du cycle
(2026-09-03) — voir § « Décisions tranchées ». Reste un point technique à vérifier, pas un point
produit :

1. Vérifier en Phase 5 (implémentation), via un test d'intégration réel contre le vrai moteur
   Flowable (pas supposé), que `Task.getFormKey()` est bien lisible sur les tâches actives sans le
   module `flowable-form` — la confirmation actuelle s'appuie sur une recherche externe (tests
   `org.flowable.examples.taskforms.TaskFormsTest` du module core `flowable-engine`), pas sur une
   inspection directe du jar (dépôt Maven local non peuplé dans ce bac à sable).
2. Détail de composition de l'écran Admin → Types (emplacement exact de la nouvelle section
   `documentTemplateKeys`, sélection multiple parmi quels templates — actifs seulement, tous
   statuts ?) — micro-décision UI à trancher en Phase 3 (design), pas bloquante pour les
   requirements.
