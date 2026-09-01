---
phase: design
title: System Design & Architecture
description: Define the technical architecture, components, and data models
---

# System Design & Architecture

> Première passe (Phase 1), à affiner en Phase 3 (`dev-design`) — notamment le schéma exact de
> `connector_schedule_state` et la confirmation "backend-only".

## Architecture Overview
**What is the high-level system structure?**

```mermaid
graph TD
    subgraph Infrastructure
        SchedJob[SchedulerConnectorTriggerJob<br/>@Scheduled poll-delay]
        SchedConn[SchedulerConnector<br/>TRIGGER]
        SmtpConn[SmtpConnector<br/>SERVICE]
        StorageConn[DocumentStorageConnector<br/>SERVICE]
        StateRepo[ConnectorScheduleStateRepository<br/>connector_schedule_state]
    end
    subgraph Domaine existant réutilisé
        Runtime[ConnectorTriggerRuntime]
        EmailPort
        DocStoragePort[DocumentStoragePort]
    end
    SchedJob -->|"instances scheduler dues"| StateRepo
    SchedJob -->|"onIncomingEvent(instance, Map.of())"| Runtime
    SchedConn -->|"onEvent → WorkflowTriggerEvent"| Runtime
    SmtpConn -->|"execute → EmailPort.send"| EmailPort
    StorageConn -->|"execute → store/retrieve/exists/delete"| DocStoragePort
```

- 3 nouveaux connecteurs (`@Component implements ConnectorDefinition` + capacité), tous découverts
  automatiquement par `ConnectorRegistry` comme les connecteurs existants.
- Aucune modification de `ConnectorTriggerRuntimeImpl`/`ConnectorCatalog`/`ConnectorRegistry` —
  réutilisation pure des mécanismes génériques déjà en place.

## Data Models
**What data do we need to manage?**

- Nouvelle table `connector_schedule_state` (migration Liquibase) : `connector_instance_id` (FK,
  unique), `last_triggered_at` (Instant, nullable au premier tir).
- Aucun changement de schéma pour `connector_instance`/`ConnectorInstanceData`.

## API Design
**How do components communicate?**

- Aucun endpoint REST/MCP nouveau ou modifié — les 3 connecteurs passent par les mécanismes
  d'exécution génériques déjà exposés (`ConnectorExecuteController`/`M2mConnectorExecuteController`
  pour SERVICE, pipeline trigger existant pour TRIGGER).
- Nouveau port domaine `ConnectorScheduleStateRepository` (`port.out`), implémenté en infra JPA.

## Component Breakdown
**What are the major building blocks?**

- `SchedulerConnector` (TRIGGER) — config schema `cronExpression` (STRING, requis) +
  `dossierTypeCode` (STRING, requis, cohérent avec D-SCHEDULER-DOSSIER).
- `SchedulerConnectorTriggerJob` (`@Scheduled`, infra) — liste les instances `scheduler` activées
  du tenant, évalue `CronExpression` contre `last_triggered_at`, appelle
  `ConnectorTriggerRuntime.onIncomingEvent` si dû, met à jour `connector_schedule_state`.
- `ConnectorScheduleStateRepository` (port) + adaptateur JPA.
- `SmtpConnector` (SERVICE) — pas de config schema requis, injecte `EmailPort`.
- `DocumentStorageConnector` (SERVICE) — pas de config schema requis, injecte
  `DocumentStoragePort` + `ConnectorProperties.DocumentStorage.maxContentSize`.
- `ConnectorProperties.DocumentStorage` — nouveau sous-record, même patron que `Webhook`/`Http`.
- `06-connecteurs.md`/`README.md` — mise à jour du catalogue.

## Design Decisions
**Why did we choose this approach?**

- **D-SCHEDULER-LASTFIRED** : table dédiée plutôt qu'un champ sur `ConnectorInstanceData` — évite
  de polluer un record partagé par tous les types de connecteurs avec un besoin propre à un seul.
- **D-SCHEDULER-DOSSIER** : réutilisation stricte du pipeline trigger existant, zéro changement
  partagé — priorise le risque zéro sur `http-rest`/`flowable-signal` plutôt que la flexibilité
  immédiate d'un mode « sans dossier ».
- **D-SMTP-HTML/D-SMTP-VALIDATION** : réutilisation à l'identique d'`EmailPort`, garde d'entrée
  minimale cohérente avec le style `HttpRestConnector`.
- **D-STORAGE-SIZE-LIMIT** : même patron que `ConnectorProperties.Webhook.maxBodyBytes` — cohérence
  de style plutôt qu'un nouveau mécanisme de limite.
- **D-STORAGE-REF-LIFECYCLE** : documenté comme choix assumé plutôt qu'implémenté — pas de nouveau
  mécanisme de purge sans besoin identifié (cohérent avec le principe du dépôt de ne pas construire
  au-delà du besoin confirmé).

## Non-Functional Requirements
**How should the system perform?**

- `SchedulerConnectorTriggerJob` : impact mémoire/CPU négligeable (liste d'instances par tenant,
  généralement petite, évaluation `CronExpression` peu coûteuse).
- Sécurité : `document-storage` — la limite de taille configurable (D-STORAGE-SIZE-LIMIT) est la
  seule protection nouvelle à valider ; pas de nouveau secret par instance pour `smtp`/
  `document-storage`.
- Fiabilité : `connector_schedule_state` doit survivre à un redémarrage sans perdre le dernier
  déclenchement (persisté en DB, pas en mémoire).
