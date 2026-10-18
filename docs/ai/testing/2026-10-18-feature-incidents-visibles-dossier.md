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

- [ ] `listForLink` : un participant, un éditeur et un TA/WA voient les échecs. Un autre utilisateur reçoit 403.
- [ ] `listForLink` : message présent pour TA/WA, absent pour participant et éditeur.
- [ ] `retry` : TA/WA sur un `INCIDENT` de l'instance du lien → `port.retry(jobId, tenant, 3)`.
- [ ] `retry` : rôle non admin → 403, aucun appel au port.
- [ ] `retry` : job inconnu, ou d'une autre instance → `WorkflowIncidentNotFoundException`.
- [ ] `retry` : job `RETRYING` → `IncidentNotRetryableException`.
- [ ] `listForTenant` : non admin → 403. Admin : enrichi avec la référence du dossier, jobs sans lien ignorés,
  pagination et indicateur `truncated` au-delà de la borne.
- [ ] `listForDossierSystem` : toutes les exécutions du dossier, jamais de message.
- [ ] `WorkflowLinkVisualizationUseCaseImpl.getDiagram` : `failedIds` contient les activités des échecs, sans doublon.

## Adaptateur — `FlowableWorkflowIncidentAdapterIT` (moteur réel)

- [ ] Un `serviceTask` asynchrone qui lève une exception : `RETRYING` (tentatives restantes), puis `INCIDENT` en dead
  letter. On réduit les tentatives au déploiement de test (`flowable:failedJobRetryTimeCycle` ou exécution forcée des
  jobs) pour un test rapide.
- [ ] Message tronqué à 300 caractères ; activité et libellé corrects.
- [ ] `retry` : le job redevient exécutable. Une fois la cause levée, le process reprend et l'échec disparaît.
- [ ] Isolation : un job d'un autre tenant n'est jamais renvoyé.

## Web

- [ ] `WorkflowIncidentApiTest` (MockMvc) : `GET .../incidents` (droits, message selon le rôle),
  `POST .../retry` (204, 403, 404, 409), `GET /api/admin/incidents` (200 TA/WA, 403 sinon).
- [ ] Audit : `event=incident_retried` journalisé, sans message.
- [ ] `WorkflowExceptionHandlerTest` : 404 et 409.
- [ ] `OpenApiContractIT` : golden régénéré.

## MCP

- [ ] `GetDossierToolTest` : `incidents` présent, sans message.

## Frontend

- [ ] Vitest `IncidentBanner.spec.ts` : rien sans échec ; deux états libellés ; message affiché s'il est fourni ;
  « Rejouer » seulement pour `INCIDENT` et un admin ; rejeu → appel API puis événement `retried`.
- [ ] Vitest `IncidentsAdminView.spec.ts` : tableau, lien vers le dossier, pagination, « Rejouer », message
  « liste tronquée ».
- [ ] Vitest `DossierDetailView.spec.ts` : bandeau affiché et diagramme rechargé après le rejeu.
- [ ] Playwright : fiche avec un incident → bandeau, rejeu → requête POST, bandeau vidé ; écran `/admin/incidents`
  listé et accès refusé à un non-admin.

## Vérifications finales

- [ ] `./mvnw clean verify` (frontend compris) vert.
- [ ] `npm run test:all` vert.
- [ ] Recette sur l'instance locale : le cas R6 de l'exemple riverain affiche l'incident ; correction de l'adresse et
  rejeu → e-mail reçu dans Mailpit.
