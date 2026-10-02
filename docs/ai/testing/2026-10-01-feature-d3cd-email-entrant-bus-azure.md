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
| API | `ConnectorAdminApiTest` | le catalogue compte 10 types (11 avec Graph, voir ci-dessous) |

## Ajout D3c-bis — Microsoft Graph

| Niveau | Test | Couvre |
|---|---|---|
| Connecteur | `GraphInboundConnectorTest` | validation (boîte, hôtes Graph seuls et https, dossiers distincts, OAuth2 obligatoire, `trustedAuthservId`), jeton récupéré et jamais affiché, endpoint re-vérifié à l'interrogation, routage partagé (référence attestée, expéditeur non attesté, réponses automatiques) |
| Infra | `HttpGraphMailboxTest` (serveur local rejouant Graph) | remise puis déplacement, jeton porteur, premier `Authentication-Results`, réponse automatique rangée sans suppression, pièces jointes bornées / incrustées / non fichier, déjà remis non remis deux fois, message empoisonné écarté, déplacement 404 toléré, 401/403/429/503 abandonnent sans ranger ni fuite du jeton, redirection jamais suivie, réponse trop grosse refusée, dossier absent, guillemets du filtre |
| Planificateur | `InboundPollingConnectorTriggerJobTest` | `graph-mail-inbound` interrogé comme les autres types d'entrée |
| API | `ConnectorAdminApiTest` | le catalogue compte désormais 11 types |
| Non-régression | `ImapInboundConnectorTest`, `AngusImapMailboxTest` | extraction de `InboundMailRouting` sans changement de comportement |
