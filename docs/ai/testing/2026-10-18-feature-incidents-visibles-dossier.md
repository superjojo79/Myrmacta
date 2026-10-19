---
phase: testing
title: Testing — incidents-visibles-dossier
status: draft
created: 2026-10-18
feature: incidents-visibles-dossier
---

# Testing — incidents-visibles-dossier

TDD : chaque scénario est écrit en rouge avant le code. Seuils Jacoco tenus.

## Domaine — `WorkflowIncidentsUseCaseImplTest` (port simulé)

- [x] `listForLink` : un participant, un éditeur et un TA/WA voient les échecs. Un autre utilisateur reçoit 403.
- [x] `listForLink` : message présent pour TA/WA, absent pour participant et éditeur.
- [x] `retry` : TA/WA sur un `INCIDENT` de l'instance du lien → `port.retry(jobId, tenant, 3)`.
- [x] `retry` : rôle non admin → 403, aucun appel au port.
- [x] `retry` : job inconnu, ou d'une autre instance → `WorkflowIncidentNotFoundException`.
- [x] `retry` : job `RETRYING` → `IncidentNotRetryableException`.
- [x] `listForTenant` : non admin → 403. Admin : enrichi avec la référence du dossier, jobs sans lien ignorés,
  pagination et indicateur `truncated` au-delà de la borne.
- [x] `listForDossierSystem` : toutes les exécutions du dossier, jamais de message.
- [x] `WorkflowLinkVisualizationUseCaseImpl.getDiagram` : `failedIds` contient les activités des échecs, sans doublon.

## Adaptateur — `FlowableWorkflowIncidentAdapterIT` (moteur réel)

- [x] Un `serviceTask` asynchrone qui lève une exception : `RETRYING` (tentatives restantes), puis `INCIDENT` en dead
  letter. On réduit les tentatives au déploiement de test (`flowable:failedJobRetryTimeCycle` ou exécution forcée des
  jobs) pour un test rapide.
- [x] Message tronqué à 300 caractères ; activité et libellé corrects.
- [x] `retry` : le job redevient exécutable. Une fois la cause levée, le process reprend et l'échec disparaît.
- [x] Isolation : un job d'un autre tenant n'est jamais renvoyé.

## Web

- [x] `WorkflowIncidentApiTest` (MockMvc) : `GET .../incidents` (droits, message selon le rôle),
  `POST .../retry` (204, 403, 404, 409), `GET /api/admin/incidents` (200 TA/WA, 403 sinon).
- [x] Audit : `event=incident_retried` journalisé, sans message.
- [x] `WorkflowExceptionHandlerTest` : 404 et 409.
- [x] `OpenApiContractIT` : golden régénéré.

## MCP

- [x] `GetDossierToolTest` : `incidents` présent, sans message.

## Frontend

- [x] Vitest `IncidentBanner.spec.ts` : rien sans échec ; deux états libellés ; message affiché s'il est fourni ;
  « Rejouer » seulement pour `INCIDENT` et un admin ; rejeu → appel API puis événement `retried`.
- [x] Vitest `IncidentsAdminView.spec.ts` : tableau, lien vers le dossier, pagination, « Rejouer », message
  « liste tronquée ».
- [x] Vitest `DossierDetailView.spec.ts` : bandeau affiché et diagramme rechargé après le rejeu.
- [x] Playwright : fiche avec un incident → bandeau, rejeu → requête POST, bandeau vidé ; écran `/admin/incidents`
  listé et accès refusé à un non-admin.

## Vérifications finales

- [x] `./mvnw clean verify` (frontend compris) vert.
- [x] `npm run test:all` vert.
- [x] Recette sur l'instance locale : le cas R6 de l'exemple riverain affiche l'incident ; correction de l'adresse et
  rejeu → e-mail reçu dans Mailpit.

## Résultats (2026-10-19, après correctifs de la revue adversariale)

- `./mvnw clean verify` (frontend compris) : **3460 tests, 0 échec**, couverture tenue, golden OpenAPI à jour.
- `npm run test:all` : Vitest **730**, Playwright **129**.
- Ajoutés par la revue : `FlowableWorkflowIncidentAdapterTest` (job sans instance, rejeu sans effet ou concurrent, tri
  stable), IT sous-process appelé et rejeu d'un autre tenant, lecteur du dossier = liste vide, `truncated` exact, MCP
  `workflowLinkId`, bandeau accessible, 409 traduit, retour de page, relectures après rejeu.
- Recette locale R6 : incident visible (bandeau, diagramme), adresse corrigée, rejeu, e-mail reçu dans Mailpit.
