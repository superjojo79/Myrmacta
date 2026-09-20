---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
feature: connecteurs-cible
lot: B1
---

# Requirements & Problem Understanding — `connecteurs-cible`

> Origine : lot **B1** ([FILE-ATTENTE.md](../../../FILE-ATTENTE.md) Vague B, 2026-09-08 —
> « `connecteurs-cible` (Kafka + import batch + ERP), après A, un seul cycle »),
> [DEFERRED-FEATURES.md](../../../DEFERRED-FEATURES.md) §6. Vague A (A1/A2/A3) **intégralement
> livrée** ([PR #119](https://github.com/superjojo79/FlowForge/pull/119),
> [PR #117](https://github.com/superjojo79/FlowForge/pull/117),
> [PR #118](https://github.com/superjojo79/FlowForge/pull/118)) — plus aucune contrainte de
> surface exclusive à respecter pour B1.
>
> **Constat de code (Phase 1, pas supposé) :** aucun document d'analyse (`analysis doc/NN-*.md`)
> ne définissait « Kafka », « import batch » ou « ERP » comme connecteurs, contrairement à toutes
> les features précédentes de ce dépôt. `analysis doc/06-connecteurs.md` catalogue exactement 5
> types de connecteurs cibles historiques (`http-rest`, `flowable-signal`, `scheduler`, `smtp`,
> `s3-minio`→`document-storage`) — **tous les 5 sont déjà livrés**
> ([20-connecteurs-hors-v1.md](../../../analysis%20doc/20-connecteurs-hors-v1.md) §1, PR #99). Le
> périmètre concret de ce lot vient donc de [ROADMAP.md](../../../ROADMAP.md) §2.1/§2.2
> (rédigé par l'utilisateur, 2026-09-12) : **`BatchImportConnector`** (SFTP/CSV/Excel) et
> **`KafkaConnector`** (événementiel) — l'« ERP » de FILE-ATTENTE y est le **cas d'usage** que ces
> deux connecteurs débloquent (ingestion massive ERP, notification/commande depuis un système
> core), pas un 3ᵉ connecteur (déjà couvert par `http-rest` pour les API REST d'ERP).
>
> Mode workspace : **no-worktree**, développement direct sur la branche de session
> `claude/blissful-brahmagupta-vo50p3` — le harness de cette session restreint le push à cette
> branche, confirmé par l'utilisateur (cohérent avec toutes les features précédentes de ce dépôt).
> Traçage `ai-devkit task` non tenté explicitement dans cette session (voir
> `dev-lifecycle` startup) ; le suivi de phase se fait dans ce fichier et dans les résumés adressés
> à l'utilisateur.

## Problem Statement

FlowForge sait déjà créer des dossiers depuis un événement entrant unique (`http-rest` webhook,
`flowable-signal`) ou une planification cron sans payload (`scheduler`). Deux familles de flux
d'intégration entreprise très demandées en secteur public/assurance ne sont couvertes par aucun
connecteur existant :

1. **Ingestion de masse par fichier** — un ERP, un export bancaire ou un système legacy dépose
   régulièrement un ou plusieurs fichiers CSV/Excel sur un serveur SFTP ; FlowForge doit les
   récupérer, les parser, et créer un dossier par ligne, sans intervention manuelle.
2. **Intégration événementielle** — un système core (assurance, ERP, microservice) publie des
   événements métier sur un topic Kafka ; FlowForge doit les consommer au fil de l'eau pour créer
   ou faire avancer des dossiers, en alternative au polling HTTP/batch pour les flux quasi
   temps réel.

Sans ces deux connecteurs, FlowForge ne peut pas s'intégrer aux systèmes legacy par flux fichiers
ni aux architectures événementielles modernes — seul le webhook HTTP (`http-rest`, poussé par
l'appelant) ou le cron sans payload (`scheduler`) sont disponibles aujourd'hui.

**Constat de code déterminant (Phase 1) :** `TriggerConnector.onEvent(...)` retourne déjà
`List<WorkflowTriggerEvent>` et `HttpRestConnector` l'exploite via un mapping `itemsPath`
(`SimpleJsonPathMapper`) — la création de **N dossiers depuis un seul événement entrant** est donc
un mécanisme générique déjà livré, pas quelque chose à inventer. Ce qui manque réellement, ce sont
deux **nouveaux transports** pour amener les données jusqu'à ce mécanisme : lecture de fichier
SFTP (au lieu d'un push HTTP) et consommation Kafka (au lieu d'un push HTTP/signal). Aucun des deux
ne nécessite de modifier `ConnectorCatalog`, `ConnectorTriggerRuntimeImpl` ni
`ExecuteConnectorRequest` — seulement d'ajouter deux nouveaux `@Component` implémentant
`ConnectorDefinition`/`TriggerConnector`, exactement comme `SchedulerConnector`/`SmtpConnector`/
`DocumentStorageConnector` l'ont fait pour `connecteurs-hors-v1` (PR #99).

## Goals & Objectives

**Objectifs principaux**

- Livrer **2 nouveaux connecteurs TRIGGER** dans **un seul cycle** (pas 2 PR) :
  - `batch-import` — SFTP, fichiers CSV **et** Excel (.xlsx), déclenché par cron (comme
    `scheduler`), un dossier par ligne, archivage du fichier après import réussi.
  - `kafka` — consumer Kafka (topic + consumer group), un dossier ou avancement de tâche par
    message JSON, offset `latest` à la première souscription.
- Réutiliser tel quel le pipeline existant : `ConnectorCatalog` (auto-registration Spring),
  `ConnectorTriggerRuntime.onIncomingEvent`, `WorkflowTriggerEvent`, `CreateDossierUseCase` —
  **zéro** modification de ces classes partagées.
- Formulaire admin **déjà généré depuis `configSchema`** par le lot A2
  (`admin-connector-form-schema`, PR #117) pour configurer une instance des deux connecteurs.
  **Amendé en Phase 3 (design)** : une clé privée SSH (PEM multi-lignes) ne tient pas dans le
  rendu actuel de `ConfigFieldType.PASSWORD` (`<input type="password">` mono-ligne) — 1 seul cas
  additif dans le composant partagé `ConnectorConfigForm.vue` (nouveau `ConfigFieldType.
  SECRET_TEXTAREA`), aucun nouvel écran/vue (voir D-CONFIG-FIELD-SECRET-TEXTAREA, doc design).

**Objectifs secondaires**

- `docker-compose.yml` : ajouter un service Kafka (mode KRaft, sans Zookeeper) pour permettre une
  démo/dev locale réelle du connecteur `kafka`.
- `GUIDE-CONCEPTION-BPMN.md` / `06-connecteurs.md` : documenter les deux nouveaux types au
  catalogue (colonne « V1 livré »), comme fait pour `connecteurs-hors-v1`.

**Non-goals**

- Un 3ᵉ connecteur « ERP » dédié — cas d'usage déjà couvert par `http-rest` (API REST) et par les
  deux connecteurs de ce lot (fichier / événementiel) — voir constat Phase 1 ci-dessus.
- `kafka` en rôle SERVICE (producer) — hors scope, [ROADMAP.md](../../../ROADMAP.md) §2.2 ne décrit
  qu'un TRIGGER ; à réévaluer si un besoin réel de publication apparaît.
- `batch-import` par FTP/SMB — SFTP uniquement pour ce cycle.
- Nouveau concept domaine « dossier en erreur » / statut d'erreur tracé en base — l'isolation des
  lignes invalides reste **interne au connecteur** (voir D-BATCH-ERROR-HANDLING), pas de
  changement de `ConnectorTriggerRuntimeImpl` ni de `Dossier`.
- Granularité « dossier parent + éléments enfants » pour un import — un dossier par ligne
  uniquement (voir D-BATCH-GRANULARITY).
- Preuve contre un vrai broker Kafka ou un vrai serveur SFTP en test d'intégration — tests
  mockés uniquement pour les deux connecteurs (voir D-KAFKA-TEST / D-SFTP-TEST) ; risque assumé et
  documenté, pas contourné.
- Toute décision d'image Docker / `context-path` / store partagé (hors lot, propriété A1).
- `SecurityConfig`, ACL, `CreateDossierBatchUseCase` (aucun de ces deux connecteurs n'appelle un
  chemin batch dédié — chaque ligne passe par `CreateDossierUseCase` comme `http-rest`).

## User Stories & Use Cases

1. En tant qu'administrateur tenant, je configure une instance `batch-import` (hôte/port SFTP,
   identifiants — mot de passe ou clé privée, répertoire distant + motif de nom de fichier, cron,
   `processKey`/`dossierTypeCode`, mapping colonnes → champs) via le formulaire admin généré
   (A2), sans toucher à un `<textarea>` JSON.
2. À l'échéance du cron, FlowForge se connecte en SFTP, liste les fichiers du répertoire
   matchant le motif, télécharge et parse chacun (CSV ou Excel selon l'extension), crée un
   dossier par ligne valide, puis déplace/renomme le fichier traité pour ne pas le réimporter au
   prochain poll.
3. Une ligne du fichier est malformée (champ requis manquant, type invalide) : le connecteur
   l'exclut et continue les autres lignes du même fichier — le fichier est quand même archivé une
   fois le traitement terminé, la ligne invalide est journalisée (log + compteur), pas de dossier
   créé pour elle.
4. En tant qu'administrateur tenant, je configure une instance `kafka` (bootstrap servers, topic,
   consumer group, protocole de sécurité `PLAINTEXT` ou `SASL_SSL` + identifiants si `SASL_SSL`,
   `processKey`/`dossierTypeCode`, mapping JSON → champs) via le même formulaire admin généré.
5. Un message métier est publié sur le topic configuré : FlowForge le consomme, le mappe en
   `WorkflowTriggerEvent` (JSON → champs, comme `http-rest`), crée ou fait avancer un dossier. Les
   messages publiés **avant** l'activation de l'instance (offset antérieur au démarrage) ne sont
   pas rejoués (`latest`).
6. En local/dev, un développeur démarre `docker-compose up` (incluant le nouveau service Kafka)
   et peut tester manuellement le connecteur `kafka` sans dépendre d'un broker externe.

## Success Criteria

- `ConnectorCatalog.listAll()` expose exactement les connecteurs existants **plus** `batch-import`
  et `kafka` — zéro modification de `ConnectorRegistry`, `ConnectorRuntime`,
  `ExecuteConnectorRequest` (confirmé par relecture des callers en Phase 7).
- `batch-import` : capability `TRIGGER` ; CSV **et** .xlsx tous les deux parsés vers le même
  mapping colonnes → champs ; une ligne invalide n'interrompt pas les suivantes ; le fichier traité
  est archivé côté SFTP une fois le poll terminé (succès ou échecs partiels) ; un fichier dépassant
  la taille ou le nombre de lignes configuré est rejeté proprement (log, pas de parsing, pas
  d'archivage) plutôt que de risquer un OOM (D-BATCH-SIZE-LIMIT).
- `kafka` : capability `TRIGGER` ; consumer group configurable ; `securityProtocol` accepte
  `PLAINTEXT` et `SASL_SSL` ; offset `latest` à la première souscription d'une instance ; un
  message malformé ne bloque pas la consommation des messages suivants.
- Les deux connecteurs sont configurables de bout en bout depuis l'écran Admin → Connecteurs (A2)
  sans code frontend nouveau — `mot de passe`/`clé privée SFTP` et `mot de passe SASL` masqués en
  lecture et chiffrés au repos via `ConnectorCatalog.isPasswordField` (schéma déclaré), comme les
  champs `PASSWORD` existants.
- `docker-compose.yml` démarre un broker Kafka utilisable localement (`docker compose config`
  valide au minimum si `docker compose up` n'est pas exécutable dans ce bac à sable — même limite
  déjà documentée pour A1/Docker Hub).
- `./mvnw verify` vert (Jacoco LINE ≥ 85 % / BRANCH ≥ 75 % tenus). **Amendé en Phase 3** : la
  feature n'est plus 100 % backend-only — `ConnectorConfigForm.vue` gagne le cas `SECRET_TEXTAREA`
  (D-CONFIG-FIELD-SECRET-TEXTAREA). Vitest couvre ce nouveau cas ; Playwright reste **exigé** en
  régression (composant partagé par tous les connecteurs déjà configurables en E2E) **plus** un
  nouveau scénario bout-en-bout (configurer une instance `batch-import` avec `sftpAuthType=
  PRIVATE_KEY` via l'écran Admin → Connecteurs).
- `06-connecteurs.md` / `GUIDE-CONCEPTION-BPMN.md` mis à jour (catalogue, exemples de mapping).

## Constraints & Assumptions

| ID | Contrainte / hypothèse |
|---|---|
| C-SURFACE | Nouveaux fichiers : `infrastructure/connector/builtin/BatchImportConnector.java`, `KafkaConnector.java`, jobs/adaptateurs associés (SFTP client, Kafka consumer), tests. Aucune modification de `ConnectorCatalog`, `ConnectorTriggerRuntimeImpl`, `ExecuteConnectorRequest`, `ConnectorRegistry`. |
| C-DEPS | Aucune dépendance SFTP/CSV/Excel/Kafka dans `pom.xml` aujourd'hui — les 4 sont nouvelles (client SSH/SFTP, parseur CSV, Apache POI pour .xlsx, client Kafka Spring). |
| C-SCHEDULER-NOT-GENERIC | `SchedulerConnectorTriggerJob` est câblé en dur sur le type `"scheduler"` (pas générique) — `batch-import` a besoin de son propre mécanisme de déclenchement cron, à trancher en Phase 3 (nouveau job dédié vs généralisation ; recommandation par défaut : nouveau job dédié, moindre risque de régression sur `scheduler` déjà livré). |
| C-KAFKA-DYNAMIC | Les instances de connecteur sont créées dynamiquement via l'API admin (pas de bean statique par topic) — le mécanisme d'abonnement dynamique par instance Kafka reste à concevoir en Phase 3 (`MessageListenerContainer` créé/détruit par instance plutôt que `@KafkaListener` statique). |
| C-TENANT | Une instance Spring Boot = un tenant (contrainte déjà connue) — aucune partition de topic/segmentation par tenant à ajouter. |
| A-MAPPING | Le mapping colonnes CSV/Excel → champs et JSON Kafka → champs réutilise le même style de configuration que `http-rest.mapping` (`SimpleJsonPathMapper`/équivalent colonne), pas un nouveau langage de mapping. |
| A-COMPOSE-IMAGE | Image Kafka en mode KRaft (sans Zookeeper) pour le service Compose — choix par défaut, pas de contrainte utilisateur exprimée sur l'image exacte. |
| A-SFTP-LIB | Bibliothèque SFTP/SSH à choisir en Phase 3 (ex. SSHJ ou JSch) — aucune contrainte de licence connue à ce stade. |

## Décisions actées (Phases 1–2)

| ID | Question | Décision | Pourquoi |
|---|---|---|---|
| **D-ERP-SCOPE** | Un 3ᵉ connecteur ERP dédié ? | **A — Non**, 2 connecteurs seulement (confirmé utilisateur 2026-09-14) | ROADMAP.md §2.1/§2.2 ne décrit que `BatchImportConnector`/`KafkaConnector` ; ERP = cas d'usage, déjà couvert par `http-rest` pour les API REST. |
| **D-BATCH-PROTOCOL** | FTP/SMB en plus de SFTP ? | **SFTP uniquement** (confirmé utilisateur 2026-09-14) | Le plus courant pour des échanges sécurisés ERP/legacy ; FTP/SMB différés en follow-up si besoin réel. |
| **D-BATCH-FORMAT** | CSV seul ou aussi Excel ? | **CSV + Excel (.xlsx) dès ce cycle** (confirmé utilisateur 2026-09-14) | Couvre le besoin « fichiers Excel métier » du ROADMAP dès maintenant. |
| **D-KAFKA-ROLE** | `kafka` en BOTH (comme `http-rest`) ou TRIGGER seul ? | **TRIGGER uniquement** (confirmé utilisateur 2026-09-14) | Conforme à ROADMAP §2.2 ; SERVICE (producer) hors scope, à réévaluer plus tard. |
| **D-BATCH-ERROR-HANDLING** | Ligne invalide : comment l'isoler ? | **Isolation dans le connecteur** — `onEvent()` filtre les lignes invalides (log + compteur), pas de nouveau statut domaine (confirmé utilisateur 2026-09-14) | Pas de changement sur `ConnectorTriggerRuntimeImpl` partagé par tous les connecteurs TRIGGER — périmètre et risque de régression minimaux. |
| **D-BATCH-GRANULARITY** | Un dossier par ligne, ou parent + éléments enfants ? | **Un dossier par ligne** (confirmé utilisateur 2026-09-14) | Réutilise directement `WorkflowTriggerEvent` existant, cohérent avec `http-rest` `itemsPath` déjà livré. |
| **D-KAFKA-TEST** | Preuve contre un vrai broker (Testcontainers) ? | **Mocké uniquement** (confirmé utilisateur 2026-09-14) | Risque assumé et documenté (écart avec la culture « SmokeIT réel » du dépôt), pas de Testcontainers Kafka ce cycle. |
| **D-KAFKA-AUTH** | Quel(s) mode(s) d'auth dans `configSchema` ? | **PLAINTEXT + SASL_SSL** (confirmé utilisateur 2026-09-14) | Couverture minimale mais réaliste pour un premier livrable exploitable en prod (pas seulement dev/local). |
| **D-BATCH-DEDUP** | Éviter le double-import d'un même fichier entre deux polls ? | **Archivage côté SFTP après traitement** (déplacement/renommage) (confirmé utilisateur 2026-09-14) | Simple, aucun nouvel état persisté côté FlowForge ; suppose un accès en écriture sur le SFTP distant (documenté). |
| **D-BATCH-PATH** | Fichier unique ou répertoire + motif ? | **Répertoire + motif glob** (`remoteDirectory` + `filenamePattern`) (confirmé utilisateur 2026-09-14) | Plus réaliste pour un export nightly à nom variable/daté. |
| **D-KAFKA-OFFSET** | `earliest` ou `latest` à la première souscription ? | **`latest`** (confirmé utilisateur 2026-09-14) | Évite une création massive imprévue de dossiers historiques à la première activation. |
| **D-KAFKA-COMPOSE** | Ajouter un service Kafka à `docker-compose.yml` ? | **Oui** (confirmé utilisateur 2026-09-14) | Cohérent avec `GETTING-STARTED.md` (démarrage local déjà documenté via Compose) ; permet une démo/dev locale réelle. |
| **D-SFTP-AUTH** | Mot de passe seul ou aussi clé privée ? | **Mot de passe + clé privée** (confirmé utilisateur 2026-09-14) | Plus proche des usages ERP réels (auth SSH par clé), effort supplémentaire assumé. |
| **D-SFTP-TEST** | Preuve contre un vrai serveur SFTP (Testcontainers) ? | **Mocké uniquement** (confirmé utilisateur 2026-09-14) | Cohérence avec D-KAFKA-TEST — même niveau de preuve pour les deux connecteurs de ce lot. |
| **D-BATCH-SIZE-LIMIT** | Garde de taille/nombre de lignes sur le fichier téléchargé ? | **Oui — taille max + lignes max** (confirmé utilisateur 2026-09-14, trouvé en Phase 2) | `http-rest`/webhooks ont déjà une garde équivalente (`maxResponseBytes`, `WebhookBodySizeLimitFilter`) ; sans garde, un fichier anormalement volumineux téléchargé en mémoire pour parsing risque un OOM. Nouvelle propriété `ConnectorProperties` (ex. `batch-import.max-file-bytes`/`max-rows`), fichier trop gros → rejet propre, pas de crash. |
| **D-SFTP-HOSTKEY** | Comment vérifier la clé hôte du serveur SFTP distant (SSHJ exige un `HostKeyVerifier` explicite) ? | **Nouveau champ `sftpHostKeyFingerprint`, requis, vérifié à chaque connexion** (confirmé utilisateur 2026-09-14, trouvé en Phase 5 en écrivant l'adaptateur SSHJ réel) | Sans vérification explicite, la connexion SFTP sortante est exposée à un MITM — risque non couvert par aucune décision antérieure. `SSHClient.addHostKeyVerifier(String)` (vérifié dans l'API réelle de SSHJ 0.39.0, pas supposé) compare l'empreinte configurée à celle présentée par le serveur. |

## Questions & Open Items

Aucun point produit/scope matériel ouvert. Les 4 points architecturaux reportés en Phase 3 (design)
sont tranchés — voir
[docs/ai/design/2026-09-14-feature-connecteurs-cible.md](../design/2026-09-14-feature-connecteurs-cible.md) :
D-BATCH-CRON-JOB (nouveau job dédié), D-KAFKA-LIFECYCLE (reconciliateur par polling),
D-CONFIG-FIELD-SECRET-TEXTAREA (nouveau `ConfigFieldType`, découvert en design à partir de
D-SFTP-AUTH), D-SFTP-LIB (SSHJ). `configSchema` exact des deux connecteurs également documenté
dans le doc design.
