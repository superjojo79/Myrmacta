---
phase: implementation
title: Implementation — D3c + D3d
status: reviewed
created: 2026-10-01
feature: d3cd-email-entrant-bus-azure
---

# Implementation — D3c + D3d

- `domain/connector/{model/InboundAttachment, model/WorkflowTriggerEvent, port/in/AttachInboundFilesUseCase, usecase/AttachInboundFilesUseCaseImpl, usecase/ConnectorTriggerRuntimeImpl}`.
- `infrastructure/connector/builtin/{ImapInboundConnector, ImapMailbox, AngusImapMailbox, AzureServiceBusConnector, ServiceBusPort, HttpServiceBusClient, OAuth2ConfigSupport}`, `infrastructure/connector/InboundPollingConnectorTriggerJob`, `infrastructure/config/{InboundConnectorProperties, ConnectorConfig}`.
- Dépendance de **test** : GreenMail 2.1.14 (serveur IMAP/SMTP en mémoire). Aucune dépendance de production ajoutée.
- Pièges : une valeur nulle dans les variables d'un événement lève dans `Map.copyOf` (retirées) ; la liste des types du catalogue est comptée par `ConnectorAdminApiTest` (10).
