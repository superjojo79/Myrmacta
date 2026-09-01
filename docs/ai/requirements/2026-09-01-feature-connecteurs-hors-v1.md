---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
---

# Requirements & Problem Understanding

## Problem Statement
**What problem are we solving?**

- `06-connecteurs.md` catalogue 5 types de connecteurs cibles ; seuls `http-rest` et
  `flowable-signal` sont enregistrés dans `ConnectorRegistry` aujourd'hui. Les 3 restants
  (`scheduler`, `smtp`, `s3-minio`) étaient hors V1 à chaque cycle précédent — pas par manque de
  valeur, mais parce que 2 des 3 chevauchent un sous-système déjà livré (email via les
  notifications, stockage via `DocumentStoragePort`) et nécessitaient de délimiter précisément le
  périmètre pour ne pas dupliquer l'existant.
- Affecté : tout concepteur de workflow BPMN qui a besoin (a) de démarrer un process à intervalle
  planifié sans dépendre d'un événement externe (`scheduler`), (b) d'envoyer un email ad hoc depuis
  un `serviceTask` avec un contenu calculé dans le process, sans passer par le modèle de
  notification structuré/templé (`smtp`), ou (c) de lire/écrire un contenu depuis un `serviceTask`
  qui n'est pas une pièce jointe de dossier (`document-storage`).
- Choisi comme prochaine feature le 2026-09-01 : TODO.md (49/49), la revue sécurité (§40–46) et le
  sweep de dette technique (§49) sont tous clos ; retenu par l'utilisateur parmi deux pistes
  proposées (outillage provisioning tenant reste en réserve). Périmètre précisé par l'utilisateur :
  les 3 connecteurs candidats, avec `s3-minio` **généralisé** en `document-storage`.

Référence : [analysis doc/20-connecteurs-hors-v1.md](../../../analysis%20doc/20-connecteurs-hors-v1.md).

## Goals & Objectives
**What do we want to achieve?**

Primaire :

- **`scheduler` (TRIGGER)** — nouveau connecteur qui démarre un workflow à intervalle planifié
  (cron). Réutilise le pipeline trigger générique déjà en place
  (`ConnectorTriggerRuntimeImpl.onIncomingEvent`, déjà découplé de HTTP) — le connecteur produit un
  `WorkflowTriggerEvent` à partir de sa config, un nouveau job `@Scheduled` décide quand l'appeler.
  - **D-SCHEDULER-LASTFIRED = table dédiée** (décidé 2026-09-01, option recommandée retenue) :
    nouvelle table `connector_schedule_state` (`connector_instance_id`, `last_triggered_at`) + port
    dédié, séparée de `ConnectorInstanceData` (record partagé par tous les connecteurs — ne pas lui
    ajouter un champ qui ne concerne que `scheduler`).
  - **D-SCHEDULER-DOSSIER = un dossier par tir, comportement existant** (décidé 2026-09-01, option
    recommandée retenue) : `scheduler` exige `dossierTypeCode` en config comme les autres
    connecteurs TRIGGER — zéro changement du pipeline trigger partagé (risque zéro sur
    `http-rest`/`flowable-signal`). Mode « sans dossier » explicitement hors périmètre V1.
  - **D-SCHEDULER-POLL = 1 minute par défaut, configurable** (décidé 2026-09-01, option
    recommandée retenue) — `app.connector.scheduler.poll-delay`, même patron que les autres jobs
    `@Scheduled` du dépôt (`PurgeOutboxJob`, `EvaluateDossierRetentionJob`).
- **`smtp` (SERVICE)** — envoi email ad hoc depuis un `serviceTask`, réutilise `EmailPort`/
  `SmtpEmailAdapter` déjà livré (une seule config SMTP par instance, cohérent avec le modèle « une
  instance = un tenant »). Distinct du pipeline notification (structuré/templé/outbox/webhook) —
  pas une duplication, un envoi direct avec contenu calculé dans le process BPMN.
  - **D-SMTP-HTML = texte brut seul** (décidé 2026-09-01, option recommandée retenue) — garde
    `EmailPort.send(to, subject, body)` tel quel, aucun besoin HTML identifié.
  - **D-SMTP-VALIDATION = garde basique non-blank + présence d'un « @ »** (décidé 2026-09-01,
    option recommandée retenue) — `IllegalArgumentException` avant tout appel réseau, même patron
    que les gardes d'entrée existantes (`HttpRestConnector`). Pas une validation RFC complète.
- **`document-storage` (SERVICE, généralisé depuis `s3-minio`)** — lecture/écriture de contenu
  depuis un `serviceTask`, réutilise `DocumentStoragePort` (`store`/`retrieve`/`exists`/`delete`,
  déjà agnostique S3/filesystem) pour du contenu **non lié** à un `Dossier`/`DossierElement`.
  - **D-STORAGE-SIZE-LIMIT = limite configurable** (décidé 2026-09-01, option recommandée
    retenue) : nouveau champ `ConnectorProperties.DocumentStorage.maxContentSize` (même patron que
    `Webhook.maxBodyBytes` existant), rejette avant appel à `DocumentStoragePort.store` si dépassé.
  - **D-STORAGE-REF-LIFECYCLE = choix assumé, documenté explicitement** (décidé 2026-09-01, option
    recommandée retenue) : aucune purge automatique du `storageRef` créé — le process BPMN est
    responsable de nettoyer (opération `DELETE`) s'il ne doit pas persister indéfiniment. Documenté
    dans le Javadoc et `06-connecteurs.md`, pas un oubli.

Secondaire :

- Mettre à jour `06-connecteurs.md` (catalogue, colonne « V1 livré ») et `README.md` § Système de
  connecteurs — convention `flowforge-conventions` §6.

Non-goals (explicitement hors périmètre, cf. analyse §5) :

- Coordination multi-pod du scheduler — hors V1 (décision n° 13, une instance = un tenant).
- Corps HTML pour `smtp` (D-SMTP-HTML).
- Rattachement `document-storage` → `Dossier`/`DossierElement`, purge automatique par TTL
  (D-STORAGE-REF-LIFECYCLE).
- Mode « sans dossier » pour `scheduler` (D-SCHEDULER-DOSSIER).
- Nouveau client S3 direct — écarté par la généralisation demandée, `DocumentStoragePort` reste la
  seule abstraction de stockage du dépôt (`DEFERRED-FEATURES.md` §0).

## User Stories & Use Cases
**How will users interact with the solution?**

- En tant que `TENANT_ADMIN`, je veux créer une instance `scheduler` avec une expression cron et un
  `dossierTypeCode`, pour démarrer automatiquement un workflow à intervalle régulier (ex. une
  vérification récurrente) sans dépendre d'un événement externe.
- En tant que concepteur de workflow BPMN, je veux appeler un `serviceTask` référençant une
  instance `smtp` avec `to`/`subject`/`body` calculés dans le process, pour envoyer un email ad hoc
  sans passer par le modèle de notification structuré.
- En tant que concepteur de workflow BPMN, je veux appeler un `serviceTask` référençant une
  instance `document-storage` pour stocker ou relire un contenu binaire pendant l'exécution du
  process, indépendamment de tout dossier.
- En tant qu'opérateur, je veux qu'un tir `scheduler` manqué au redémarrage de l'instance (ou un
  double-tir) soit évité grâce à la mémorisation du dernier déclenchement.

Cas limites :

- Instance `scheduler` avec un `cronExpression` invalide → rejet à la création/mise à jour
  (`validate(config)`), pas seulement au premier tir raté.
- Instance `scheduler` désactivée (`enabled=false`) → jamais évaluée par le job de poll, comme les
  autres connecteurs désactivés.
- `smtp` avec `to` blank ou sans « @ » → `IllegalArgumentException`, aucun appel `EmailPort`
  tenté.
- `document-storage` `STORE` avec un contenu base64 dépassant `maxContentSize` →
  `IllegalArgumentException`, aucun appel `DocumentStoragePort.store` tenté.
- `document-storage` `RETRIEVE`/`EXISTS`/`DELETE` avec un `storageRef` inexistant → comportement
  hérité de `DocumentStoragePort` (`DocumentStorageException`/`false` selon l'opération, pas de
  nouveau comportement inventé par le connecteur).

## Success Criteria
**How will we know when we're done?**

- Une instance `scheduler` activée démarre effectivement un workflow au moment attendu (test
  d'intégration avec horloge contrôlée, pas un vrai sleep), sans double-tir ni tir manqué au
  redémarrage simulé.
- Un `serviceTask` `${connectorServiceTask}` référençant une instance `smtp` envoie un email réel
  via `EmailPort` avec le `to`/`subject`/`body` du `connectorInput`.
- Un `serviceTask` référençant une instance `document-storage` exécute `STORE`/`RETRIEVE`/
  `EXISTS`/`DELETE` via `DocumentStoragePort`, résultat exposé en `connectorResult`.
- `06-connecteurs.md` et `README.md` mis à jour (plus de `Non` pour `scheduler`/`smtp` ; `s3-minio`
  remplacé par `document-storage` avec la description généralisée).
- `./mvnw verify` vert. Feature **confirmée backend seule** (vérifié en Phase 2 contre le code réel,
  voir Constraints & Assumptions) : `npm run test`/`test:e2e` restent verts en régression complète,
  sans nouveau scénario E2E dédié — zéro fichier `frontend/**` modifié.

## Constraints & Assumptions
**What limitations do we need to work within?**

- `ConnectorCatalog`/`ConnectorRegistry` indexent par `getType()`, un seul bean par type — chacun
  des 3 nouveaux connecteurs est une classe/bean distincte (pas de conflit de type entre eux, ni
  avec `http-rest`/`flowable-signal`).
- Modèle « une instance Spring Boot = un tenant » (décision n° 13) — pas de coordination multi-pod
  à gérer pour le job de poll `scheduler` ; une seule config SMTP par instance pour `smtp`.
- `ConnectorInstanceData` (record domaine partagé par tous les connecteurs) reste inchangé — l'état
  de planification du `scheduler` vit dans une table/port séparés (D-SCHEDULER-LASTFIRED), pas dans
  ce record.
- Convention déjà établie : un champ de config sensible est de type `ConfigFieldType.PASSWORD`,
  chiffré au repos — aucun des 3 nouveaux connecteurs n'introduit de nouveau secret par instance à
  ce stade (SMTP/stockage réutilisent une config applicative unique, pas par instance).
- Écran d'admin connecteurs (`ConnectorsAdminView.vue`) déjà générique — formulaire JSON brut, pas
  piloté par `getConfigSchema()` (écart doc/code déjà documenté lors de
  `connecteur-http-rest-service`) — aucun changement d'écran attendu pour ces 3 connecteurs.

## Questions & Open Items
**What do we still need to clarify?**

Décisions actées 2026-09-01 (`AskUserQuestion`, TODO §50) :

- **D-SCHEDULER-LASTFIRED = table dédiée** (option recommandée retenue).
- **D-SCHEDULER-DOSSIER = un dossier par tir, comportement existant** (option recommandée
  retenue).
- **D-SCHEDULER-POLL = 1 minute par défaut** (option recommandée retenue).
- **D-SMTP-HTML = texte brut seul** (option recommandée retenue).
- **D-SMTP-VALIDATION = garde basique non-blank + « @ »** (option recommandée retenue).
- **D-STORAGE-SIZE-LIMIT = limite configurable** (option recommandée retenue).
- **D-STORAGE-REF-LIFECYCLE = choix assumé, documenté** (option recommandée retenue).

Résolu en revue Phase 2 (2026-09-01) :

- **Confirmation « backend-only »** — **confirmé backend seul**, vérifié contre le code réel de
  `ConnectorsAdminView.vue` (pas seulement contre l'analyse) : le `<select v-if="definitions.
  length">` type est déjà alimenté dynamiquement par `GET /api/connector-definitions` (n'importe
  quel nouveau type enregistré apparaît automatiquement, sans changement frontend) et le
  `<textarea>` JSON brut couvre déjà la saisie de config pour tout type. Aucun endpoint HTTP dédié
  au déclenchement n'est nécessaire pour `scheduler` — pattern déjà établi avec 2 précédents
  (`WebhookController` pour `http-rest`, `FlowableSignalEventListener` pour `flowable-signal`,
  tous deux appelant `ConnectorTriggerRuntime.onIncomingEvent` depuis l'infra) ; le nouveau job
  `@Scheduled` en sera un 3e appelant du même type, pas un mécanisme inédit.

Restant à trancher en phase design (pas bloquant pour clore les requirements) :

- **Forme exacte de la table `connector_schedule_state`** — schéma de colonnes précis, index,
  contrainte d'unicité sur `connector_instance_id`.
- **Nom exact de la classe/bean pour chaque connecteur** — `SchedulerConnector`, `SmtpConnector`,
  `DocumentStorageConnector` proposés dans l'analyse, à confirmer en design.
- **Nommage définitif du lot** dans `FILE-ATTENTE.md`/`DEFERRED-FEATURES.md` — proposition actuelle
  `connecteurs-hors-v1`, à confirmer sans urgence.
