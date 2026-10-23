---
phase: requirements
title: Requirements — incidents visibles et rejouables sur le dossier
status: reviewed
created: 2026-10-18
feature: incidents-visibles-dossier
todo_ref: TODO.md §94 · DEFERRED-FEATURES.md §7 · exemples/autorisation-riverain/DIFFICULTES.md §11
---

# Requirements — incidents-visibles-dossier

## Problème

La recette du 2026-10-18 (PR #175, cas R6) a montré qu'une brique **asynchrone** en échec est invisible. Exemple : un
e-mail « Notifier » dont l'adresse lue dans le dossier est mal formée. Flowable retente le job trois fois, puis le
range en *dead letter*. Pendant ce temps :
- la fiche dossier affiche toujours le workflow « en cours » ;
- le diagramme ne marque aucune étape. `FlowableWorkflowVisualizationAdapter.getDiagram` ne retient en `failedIds` que
  les activités terminées par une erreur BPMN, et l'étape en attente du job n'apparaît pas non plus en `activeIds` ;
- rien dans l'interface ne permet de rejouer le job après avoir corrigé la donnée.

Seul le compteur global de *dead letters*, sur l'écran Rapports, bouge. Or le guide de conception recommande
justement l'asynchrone pour transformer un échec en incident plutôt que de bloquer l'agent.

## Objectifs

- **G1 — Deux états d'échec**, calculés à partir des jobs Flowable d'une instance de process :
  - `RETRYING` : job exécutable ou minuteur en échec (exception) avec des tentatives restantes ;
  - `INCIDENT` : job en *dead letter*, plus aucune tentative automatique.

  Chaque échec porte : l'activité (identifiant, libellé du BPMN), l'état, la date du dernier échec, le nombre de
  tentatives restantes et l'identifiant du job.
- **G2 — Diagramme.** Les activités qui ont un job en échec (l'un ou l'autre état) sont ajoutées à `failedIds`, donc
  marquées en erreur par le viewer existant.
- **G3 — Fiche dossier.** Un bandeau « Incident » apparaît au-dessus du diagramme pour chaque échec de l'exécution
  courante. Il donne l'étape, l'état (« nouvelle tentative prévue » ou « incident ») et la date. Il est visible de
  quiconque peut voir les tâches de l'exécution (participants, éditeurs du dossier, TENANT_ADMIN, WORKFLOW_ADMIN).
- **G4 — Message technique.** Le message de l'exception n'est renvoyé et affiché qu'à **TENANT_ADMIN** et
  **WORKFLOW_ADMIN**, tronqué à 300 caractères. Les autres voient l'état sans message.
- **G5 — Rejouer.** TENANT_ADMIN et WORKFLOW_ADMIN peuvent rejouer un **incident** (*dead letter*), depuis la fiche
  dossier ou depuis l'écran de supervision. Le job redevient exécutable, avec le nombre de tentatives par défaut de
  Flowable (3). Le rejeu est journalisé : qui, quel job, quelle exécution, quand. Un job `RETRYING` n'a pas d'action.
  Le moteur le retente déjà.
- **G6 — Écran de supervision** `/admin/incidents` (TENANT_ADMIN, WORKFLOW_ADMIN) : liste paginée de tous les échecs du
  tenant, les plus récents d'abord. Colonnes : dossier (référence, lien vers la fiche), workflow, étape, état, date,
  tentatives restantes, message tronqué. Action « Rejouer » sur les incidents. Lien dans le menu Admin.
- **G7 — MCP (lecture seule).** `get_dossier` renvoie les échecs de ses exécutions : activité, état, date, tentatives
  restantes, **sans message**. Aucun outil de rejeu.

## Hors périmètre

- Notification (navigateur ou e-mail) à l'entrée en *dead letter* : backlog.
- Rejeu par un agent MCP.
- Modifier le nombre de tentatives ou le délai entre tentatives (configuration du moteur inchangée).
- Supprimer un job ou forcer le passage de l'étape (« ignorer l'incident »).
- Incidents de processus sans lien dossier (process démarrés hors FlowForge) : non listés.

## Récits utilisateur

- En tant que **gestionnaire**, j'ouvre un dossier dont l'e-mail n'est pas parti : un bandeau me dit que l'étape
  « E-mail : autorisation refusée » est en incident, et le diagramme la marque en rouge. Je corrige l'adresse.
- En tant qu'**administrateur**, je vois le message (« … is not a single e-mail address »), puis je clique sur
  « Rejouer » une fois l'adresse corrigée : l'e-mail part et le workflow reprend.
- En tant qu'**administrateur**, j'ouvre « Incidents » dans le menu Admin pour voir tous les échecs en cours du tenant
  et y accéder dossier par dossier.
- En tant qu'**agent MCP**, `get_dossier` me dit qu'une étape est en incident, sans exposer le message.

## Critères d'acceptation

- Un job en échec avec des tentatives restantes produit un échec `RETRYING` ; un *dead letter* produit `INCIDENT`.
  Les deux apparaissent dans `failedIds`.
- Un lecteur non administrateur reçoit les échecs sans champ `message` (absent du JSON). Un administrateur le reçoit,
  tronqué à 300 caractères.
- `POST .../incidents/{jobId}/retry` :
  - TA/WA sur un *dead letter* de cette exécution : 204, le job redevient exécutable ;
  - autre rôle : 403 ;
  - job inconnu ou d'une autre exécution : 404 ;
  - job `RETRYING` : 409 ;
  - le rejeu est journalisé.
- `GET /api/admin/incidents` (TA/WA) pagine les échecs du tenant avec la référence du dossier. Autre rôle : 403.
- Bout en bout (moteur réel) : un service asynchrone qui échoue part en *dead letter*, apparaît en `INCIDENT` dans le
  diagramme et la liste. Une fois la cause levée, le rejeu le fait aboutir et l'échec disparaît.
- Interface : bandeau sur la fiche, écran `/admin/incidents`, bouton « Rejouer » (Vitest et Playwright).
- `get_dossier` (MCP) expose les échecs sans message.
- `./mvnw verify`, `npm run test:all` verts. Le golden OpenAPI est régénéré pour les nouveaux endpoints.
- Documentation : `GUIDE-UTILISATEUR.md` (bandeau, écran Incidents, rôle), `GUIDE-CONCEPTION-BPMN.md` (§6c :
  l'incident est désormais visible et rejouable), `docs/specs/15-serveur-mcp.md`, `TODO.md` §94, `CHANGELOG.md`.

## Contraintes et hypothèses

- Architecture hexagonale : lecture et rejeu des jobs derrière un port du domaine (`WorkflowIncidentPort`).
  L'adaptateur Flowable utilise `ManagementService` (jobs, minuteurs, *dead letters*), filtré par tenant
  (`jobTenantId`).
- Le lien job → dossier passe par l'instance de process → `DossierWorkflowLink`. Un job dont l'instance n'a pas de lien
  est ignoré.
- **Hypothèse.** Nombre de tentatives au rejeu : 3, le défaut de Flowable. C'est une constante nommée, pas une
  nouvelle variable d'environnement.
- **Hypothèse.** Journalisation du rejeu via le logger d'audit existant (`SECURITY_AUDIT`, `event=incident_retried`),
  sans nouvelle table.
- Le message d'exception peut contenir des données selon la brique. Il n'est donc jamais renvoyé aux non-admins, ni
  par MCP, ni journalisé en clair par ce lot.

## Questions ouvertes

Aucune. Décisions prises le 2026-10-18 :
- périmètre visible, rejouable et supervision ;
- message réservé aux admins ;
- deux états distingués ;
- MCP en lecture seule.
