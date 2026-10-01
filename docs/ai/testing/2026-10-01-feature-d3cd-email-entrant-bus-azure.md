---
phase: testing
title: Testing — D3c + D3d
status: reviewed
created: 2026-10-01
feature: d3cd-email-entrant-bus-azure
---

# Testing — D3c + D3d

| Niveau | Fichier | Couvre |
|---|---|---|
| Domaine | `AttachInboundFilesUseCaseTest`, `ConnectorTriggerRuntimeImplTest` | contrôles des pièces jointes, rejets visibles du processus, runtime sans support refuse, référence inconnue (opt-in) |
| Connecteur | `ImapInboundConnectorTest` | validation (hôte, sécurité, OAuth2, dossiers, motif), mapping, corrélation, messages automatiques, champ vide, motif catastrophique borné |
| Connecteur | `AzureServiceBusConnectorTest` | hôte limité aux domaines Service Bus, entité, SAS, mapping, message illisible rendu, échec de « terminé », signature SAS vérifiée indépendamment |
| Infra | `AngusImapMailboxTest` (GreenMail) | MIME texte/HTML, pièces jointes bornées, rangement, messages automatiques, trop gros, limite de messages, mot de passe erroné |
| Infra | `HttpServiceBusClientTest` | peek-lock, terminé/abandon, bornes, verrou sur un autre hôte refusé, gros message rendu, erreurs sans fuite, redirection refusée, bout en bout |
| Planificateur | `InboundPollingConnectorTriggerJobTest` | cadence par instance avec plancher, créneau pris, instances isolées |
| API | `ConnectorAdminApiTest` | le catalogue compte 10 types |
