---
phase: design
title: Design — D3c + D3d
status: reviewed
created: 2026-10-01
feature: d3cd-email-entrant-bus-azure
---

# Design — D3c + D3d

- **Domaine** : `WorkflowTriggerEvent` gagne `attachments` et `createWhenReferenceUnknown` (constructeurs historiques conservés) ; `AttachInboundFilesUseCase(Impl)` (mêmes contrôles que l'upload interne et le portail, fichier refusé = écarté, jamais d'exception) ; `ConnectorTriggerRuntimeImpl` rattache les pièces après la création du dossier, repli sur la création si la référence est inconnue (opt-in), variable `inboundAttachmentsRejected`.
- **IMAP** : `ImapInboundConnector` (SPI `TriggerConnector`) + port `ImapMailbox` + `AngusImapMailbox` (Jakarta Mail, TLS vérifié, STARTTLS obligatoire, XOAUTH2 via `OAuth2TokenProvider`, messages rangés après traitement, bornes de taille/nombre, texte brut, nom de fichier assaini, réponses automatiques détectées par en-têtes). Expression de corrélation bornée par un délai (`DeadlineCharSequence`).
- **Service Bus** : `AzureServiceBusConnector` + port `ServiceBusPort` + `HttpServiceBusClient` (REST, peek-lock, redirections refusées, corps borné, adresse de verrou vérifiée sur l'hôte, SAS signée HMAC-SHA256 ou Bearer).
- **Cadence** : `InboundPollingConnectorTriggerJob` (une tâche pour les deux types, créneau pris par comparaison-échange, instances isolées).
- Configuration : `InboundConnectorProperties` (`app.inbound.*`) ; champs OAuth2 partagés (`OAuth2ConfigSupport`).
- Décisions : D-INTEG-MAIL-PROTOCOL, -CORRELATION, -REPLAY, -ATTACH, D-INTEG-AZURE, D-INTEG-POLL.
