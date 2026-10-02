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

## Revue adversariale (phase 10) — ajouts
- `PollingTriggerConnector` : remise de l'événement avant acquittement (D-INTEG-POLL-ACK) ; le runtime l'utilise pour les deux connecteurs.
- IMAP : retrait message par message, message empoisonné ou trop imbriqué écarté (`unreadable`), `getMessages(1, n)` borné, `Authentication-Results` de notre serveur (D-INTEG-MAIL-SENDER), hôte re-vérifié à chaque interrogation, nom de fichier sans caractères bidirectionnels.
- Service Bus : messages verrouillés rendus si la lecture échoue.
- Planificateur : créneau conservé après échec. SMTP sortant : `Auto-Submitted: auto-generated`.

## Ajout D3c-bis — Microsoft Graph (revue D-INTEG-MAIL-PROTOCOL, 2026-10-02)

- **Connecteur** `graph-mail-inbound` (`GraphInboundConnector`) : même SPI `PollingTriggerConnector` que l'IMAP. Les règles de routage (champs, validation, corrélation par référence, attestation de
  l'expéditeur, refus des réponses automatiques, mapping, remise avant rangement) sont extraites dans `InboundMailRouting`, partagé avec `ImapInboundConnector` ; chaque connecteur ne
  garde que l'accès à la boîte (`ImapMailbox` / `GraphMailbox`, mêmes types `Mail`, `Limits`, `Outcome`, `Handler`).
- **Accès** `HttpGraphMailbox` (`java.net.http`) : dossier lu (`inbox` ou premier niveau par nom), dossiers « traités » / « refusés » créés au besoin, liste des messages (corps texte demandé par `Prefer`),
  pièces jointes *fichier* non incrustées lues à la demande, déplacement du message. Sans curseur (D-INTEG-GRAPH-STATE) ; catégorie `FlowForge-delivered` posée avant le déplacement (idempotence).
- **Sécurité** : OAuth2 `client_credentials` seul (D3a) ; `graphBaseUrl` limité aux hôtes Graph et re-vérifié à chaque interrogation ; redirections jamais suivies ; réponses bornées ; corps d'erreur jamais
  relayé ; 401/403/408/429/5xx/3xx abandonnent l'interrogation sans rien ranger (réessai au prochain tour), 404/400 sur un message l'écartent sans bloquer la boîte.
