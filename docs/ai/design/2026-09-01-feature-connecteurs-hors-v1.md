---
phase: design
title: System Design & Architecture
description: Define the technical architecture, components, and data models
---

# System Design & Architecture

> Phase 3 (`dev-design`) close — schéma de `connector_schedule_state`, noms de classes et
> mécanique d'upsert résolus par exploration de code (précédents `bpmn_draft`,
> `listByTenantId`), voir Design Decisions.

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

- Nouvelle table `connector_schedule_state` (migration Liquibase `018-connector-schedule-state.
  yaml`, suite de `017-mcp-audit-authors.yaml`) — **clé naturelle, pas de surrogate UUID**, même
  patron que `bpmn_draft` (013, clé composite `tenant_id`+`process_definition_key`) plutôt qu'un
  `id` séparé :
  ```yaml
  connector_instance_id: uuid, PK, FK → connector_instance(id)
  last_triggered_at: timestamp with time zone, nullable (absent avant le premier tir)
  ```
  Pas de colonne `tenant_id` sur cette table : l'isolation tenant est déjà portée par
  `connector_instance` (1:1 via la FK), la dupliquer ici serait redondant.
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
  du tenant via `ConnectorInstanceRepository.listByTenantId` (**déjà** filtré
  `deletedAtIsNull` — confirmé en lisant `JpaConnectorInstanceRepositoryAdapter`, aucun filtre
  supplémentaire à écrire pour exclure les instances supprimées), évalue `CronExpression` contre
  `last_triggered_at`, appelle `ConnectorTriggerRuntime.onIncomingEvent` si dû, puis **upsert**
  `connector_schedule_state` (créé au premier tir, mis à jour ensuite — pas de création lors de
  la création de l'instance connecteur, `ManageConnectorInstanceUseCaseImpl` reste totalement
  inchangé, aucun couplage nouveau).
- `ConnectorScheduleStateRepository` (port `port.out`, méthodes `findByConnectorInstanceId`,
  `upsert(connectorInstanceId, triggeredAt)`) + adaptateur JPA
  (`JpaConnectorScheduleStateRepositoryAdapter`).
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
- **Schéma `connector_schedule_state` — clé naturelle `connector_instance_id`, pas de surrogate
  `id`** (résolu en Phase 3) : suit le précédent `bpmn_draft` (013) plutôt que le patron
  `connector_instance` (UUID généré côté application) — ici la relation est strictement 1:1 avec
  `connector_instance`, une clé naturelle évite une colonne et un index inutiles.
- **Upsert au premier tir plutôt que création à la création de l'instance** (résolu en Phase 3) :
  garde `ManageConnectorInstanceUseCaseImpl`/`ConnectorInstanceRepository.save()` totalement
  inchangés — le concept de planification reste isolé dans le job/port dédiés, cohérent avec la
  décision D-SCHEDULER-LASTFIRED de ne pas coupler `ConnectorInstanceData`.
- **Noms de classes confirmés** (résolu en Phase 3, pas de véritable alternative en jeu) :
  `SchedulerConnector`, `SmtpConnector`, `DocumentStorageConnector`,
  `SchedulerConnectorTriggerJob`, `ConnectorScheduleStateRepository`/
  `JpaConnectorScheduleStateRepositoryAdapter` — cohérents avec les conventions de nommage
  `*Connector`/`*Job`/`*Repository`/`Jpa*RepositoryAdapter` déjà établies dans le dépôt.
- **Nommage du lot confirmé** : `connecteurs-hors-v1` (déjà utilisé pour les docs/analysis doc,
  aucune alternative proposée).

## Requirements Coverage (Phase 3 cross-check)

| Requirement (doc requirements) | Couverture design |
|---|---|
| D-SCHEDULER-LASTFIRED (table dédiée) | §Data Models, §Component Breakdown — schéma résolu |
| D-SCHEDULER-DOSSIER (un dossier par tir) | §Component Breakdown — `dossierTypeCode` requis en config |
| D-SCHEDULER-POLL (1 min configurable) | À câbler via `ConnectorProperties`/`application.yml`, cf. patron `PurgeOutboxJob` — trivial, pas de section dédiée nécessaire |
| D-SMTP-HTML / D-SMTP-VALIDATION | §Component Breakdown — `SmtpConnector` |
| D-STORAGE-SIZE-LIMIT | §Component Breakdown — `ConnectorProperties.DocumentStorage` |
| D-STORAGE-REF-LIFECYCLE | §Design Decisions — choix documenté, pas de nouveau composant |
| Backend seul, aucun fichier `frontend/**` | Confirmé Phase 2 — aucun composant frontend dans Component Breakdown |
| Aucune modification `ConnectorTriggerRuntimeImpl`/`ConnectorCatalog`/`ConnectorRegistry` | §Architecture Overview |

Aucun goal/user story/contrainte des requirements sans couverture design identifiée.

## Non-Functional Requirements
**How should the system perform?**

- `SchedulerConnectorTriggerJob` : impact mémoire/CPU négligeable (liste d'instances par tenant,
  généralement petite, évaluation `CronExpression` peu coûteuse).
- Sécurité : `document-storage` — la limite de taille configurable (D-STORAGE-SIZE-LIMIT) est la
  seule protection nouvelle à valider ; pas de nouveau secret par instance pour `smtp`/
  `document-storage`.
- Fiabilité : `connector_schedule_state` doit survivre à un redémarrage sans perdre le dernier
  déclenchement (persisté en DB, pas en mémoire).
