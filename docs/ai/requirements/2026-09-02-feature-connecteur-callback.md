---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
---

# Requirements & Problem Understanding

## Problem Statement
**What problem are we solving?**

- Un process BPMN qui déclenche un appel asynchrone vers un système externe (via un connecteur
  SERVICE, ex. `http-rest`) n'a aujourd'hui **aucun moyen de reprendre son exécution** quand ce
  système externe répond plus tard par un callback HTTP entrant. Vérifié contre le code réel
  (`docs/specs/21-correlation-callback-async.md`) : ni le mode SERVICE de `http-rest`
  (`httpClient.send(...)` bloquant, timeout fixe 5 s), ni le mode TRIGGER (`WebhookController` →
  `ConnectorTriggerRuntimeImpl.onIncomingEvent`, dont toute la sémantique est « créer ou attacher
  un dossier », jamais « réveiller un point d'attente précis d'une instance en cours »), ni
  `flowable-signal` (broadcast interne au moteur, non déclenchable par HTTP externe) ne répondent
  à ce besoin.
- Affecté : tout concepteur de workflow BPMN ayant besoin de modéliser un appel externe
  fire-and-forget suivi d'une attente de réponse différée (ex. validation manuelle côté système
  tiers, traitement long asynchrone, callback de paiement).
- Situation actuelle : aucun contournement possible dans le modèle BPMN existant sans ce
  mécanisme — le besoin a été soulevé par une question utilisateur (2026-09-01), analysé
  (TODO §51) mais laissé « en réserve, non priorisé » jusqu'à cette feature.

## Goals & Objectives
**What do we want to achieve?**

- **Primaire** : permettre à un process BPMN en attente sur un `intermediateCatchEvent`/
  `receiveTask` à message d'être réveillé par un callback HTTP externe, corrélé par une valeur de
  clé métier portée dans le payload entrant (pas par le `businessKey` du process, qui identifie un
  dossier entier et ne distingue pas plusieurs appels asynchrones parallèles d'un même process).
- **Primaire** : exposer ce mécanisme sous forme d'un nouveau type de connecteur générique et
  indépendant (nom provisoire `CallbackConnector`), réutilisable par n'importe quel connecteur
  SERVICE ayant émis l'appel sortant initial — pas couplé à `http-rest` spécifiquement.
- **Secondaire** : réutiliser au maximum l'infrastructure existante — authentification HMAC par
  instance (`HmacWebhookAuthenticator`), extraction JSONPath du payload (`SimpleJsonPathMapper`),
  mécanisme d'idempotence déjà livré pour `http-rest` TRIGGER (`webhook_idempotency` /
  `JpaWebhookIdempotencyStoreAdapter`).
- **Non-goals (hors scope V1)** :
  - File d'attente persistée avec relance différée des callbacks arrivés en avance sur le process
    (D-CALLBACK-RACE, option écartée) — un callback qui arrive avant que l'instance atteigne le
    point d'attente est **rejeté** (le système externe doit retenter), pas mis en attente.
  - `messageName` dynamique par appel (D-CALLBACK-MESSAGE-NAME, option écartée) — le
    `messageName` est fixe par instance de connecteur, comme `signalName` pour
    `flowable-signal`.
  - `correlateAll()` / corrélation broadcast — plusieurs instances en attente avec la même valeur
    de corrélation est une erreur de modélisation BPMN à faire remonter explicitement, jamais
    masquée silencieusement.
  - Nouvelle table de file d'attente de callbacks non corrélés.

## User Stories & Use Cases
**How will users interact with the solution?**

- En tant que **concepteur de workflow BPMN**, je veux modéliser un `serviceTask` d'appel
  asynchrone suivi d'un `intermediateCatchEvent` à message, afin que le process attende un
  callback externe corrélé sans bloquer le moteur ni consommer un thread pendant l'attente.
- En tant que **concepteur de workflow BPMN**, je veux choisir une valeur de corrélation (ex.
  référence dossier ou identifiant métier généré) transmise au système externe lors de l'appel
  sortant, afin que le système externe me la retourne dans son callback et que le moteur puisse
  cibler la bonne instance en attente.
- En tant qu'**administrateur tenant**, je veux configurer une instance de connecteur callback
  (URL d'entrée dédiée, secret HMAC, `messageName` cible, mapping JSONPath de la clé/valeur de
  corrélation) de la même façon que les connecteurs TRIGGER existants (`http-rest`, `scheduler`).
- En tant que **système externe**, si je retente un callback déjà traité (5xx transitoire,
  timeout réseau), je ne dois pas déclencher une seconde corrélation (idempotence).
- **Edge cases à couvrir** :
  - Callback reçu alors qu'aucune instance n'est en attente sur la valeur de corrélation (pas
    encore atteinte, déjà passée, ou jamais démarrée) → rejet HTTP explicite (404/409), pas
    d'exception non gérée qui fuiterait en 500.
  - Plusieurs instances en attente avec la même valeur de corrélation → erreur de modélisation
    remontée clairement (log + réponse HTTP en erreur), jamais un `correlateAll()` silencieux.
  - Callback rejoué (idempotence) → traité comme un no-op réussi (même contrat que
    `http-rest` TRIGGER existant), pas d'erreur côté système externe qui retenterait indéfiniment.
  - Déploiement BPMN d'un process contenant un `receiveTask`/`intermediateCatchEvent` à message
    → **[résolu en Phase 3]** vérifié sans changement du chemin de déploiement existant.

## Success Criteria
**How will we know when we're done?**

- Un process BPMN de test peut : démarrer, exécuter un `serviceTask` `http-rest` transmettant une
  clé de corrélation, atteindre un `intermediateCatchEvent` à message, et reprendre correctement
  avec les variables du payload lorsqu'un POST externe authentifié (HMAC) contenant la bonne
  valeur de corrélation est reçu sur l'endpoint du connecteur callback.
- Un callback avec une valeur de corrélation ne correspondant à aucune instance en attente reçoit
  une réponse HTTP d'erreur explicite (404/409), sans exception non gérée.
- Un callback rejoué avec le même identifiant d'idempotence ne corrèle pas deux fois (vérifié par
  test d'intégration avec le vrai moteur Flowable, pas mocké).
- Zéro modification de `ConnectorTriggerRuntimeImpl`/`ConnectorCatalog`/`ConnectorRegistry` pour
  les connecteurs existants (le nouveau connecteur callback est additif, même patron que
  `connecteurs-hors-v1`, TODO §50).
- `./mvnw verify` vert (ArchUnit + couverture Jacoco LINE ≥85 % / BRANCH ≥75 % tenus) ; feature
  **backend-only confirmée en Phase 2** (écran Admin → Connecteurs déjà générique, zéro fichier
  `frontend/**` à modifier) — Vitest/Playwright hors scope pour cette raison, pas par omission.

## Constraints & Assumptions
**What limitations do we need to work within?**

- **Techniques** :
  - Architecture hexagonale non négociable : nouvelle méthode sur `WorkflowEnginePort`
    (`correlateMessage(...)`), implémentation dans `FlowableWorkflowEngineAdapter` — aucune
    dépendance Flowable dans le domaine.
  - Nouveau connecteur dans `domain/connector/model`, interface dédiée distincte de
    `TriggerConnector` (sémantique différente : reprendre une instance existante, jamais en créer
    une nouvelle).
  - Endpoint HTTP entrant : même authentification HMAC que `WebhookController`
    (`HmacWebhookAuthenticator` réutilisable tel quel), routage dédié (pas de branchement sur
    `ConnectorTriggerRuntimeImpl.onIncomingEvent`).
  - **[Corrigé en Phase 3]** Mécanisme moteur réel : `RuntimeService.createMessageCorrelation(...)`
    proposé par l'analyse d'origine **n'existe pas** en Flowable 8.0.0 Community (vérifié contre
    les sources réelles, pas supposé). Mécanisme correct : `ExecutionQuery
    .messageEventSubscriptionName(...).processVariableValueEquals(...)` pour trouver les exécutions
    en attente, puis `RuntimeService.messageEventReceived(messageName, executionId, variables)` sur
    l'unique résultat. 0 ou plusieurs résultats détectés explicitement par notre propre code sur la
    liste (pas d'exception Flowable interne à interpréter) — comportement voulu identique, pas de
    fallback. Détail : cf. design doc.
- **Assumptions actées via clarification (2026-09-02)** :
  - Race condition (callback en avance) → rejet HTTP, retry côté externe. Pas de file différée.
  - Idempotence → réutilisation telle quelle du mécanisme `webhook_idempotency` existant.
  - `messageName` fixe par instance de connecteur, pas dynamique par appel.
  - Connecteur callback générique et indépendant, pas couplé à `http-rest`.
  - Capability : nouvelle valeur `ConnectorCapability.CALLBACK` (tranché en Phase 3).
- **[Trouvé et corrigé en Phase 7]** Sensibilité au type de la valeur de corrélation : Flowable
  compare les variables de process par type ET valeur (`String "123"` ≠ `Integer 123`, vérifié
  contre le vrai moteur). Le connecteur normalise désormais `correlationValue` en `String` — la
  variable de process posée par le `serviceTask` sortant **doit** elle aussi être une `String`
  (documenté sur le champ de config `correlationVariableName`).
- **[Résolu en Phase 3]** Déploiement BPMN : `DeployBpmnProcessUseCaseImpl`/
  `FlowableWorkflowDeploymentAdapter` déploient de façon générique (`createDeployment().addBytes(...)`,
  aucune liste blanche d'éléments) — un `receiveTask`/`intermediateCatchEvent` à message se
  déploierait sans changement de code, vérifié contre le code réel.

## Questions & Open Items
**What do we still need to clarify?**

- **[Résolu — Phase 2, 2026-09-02] Écran admin** — vérifié contre le code réel
  (`frontend/src/views/admin/ConnectorsAdminView.vue`) : l'écran Admin → Connecteurs est déjà
  totalement générique (liste des types via `listConnectorDefinitions()`, config saisie en JSON
  brut dans un `<textarea>`, aucun rendu dynamique de `getConfigSchema()`). **Zéro changement
  frontend nécessaire** — un nouveau `connectorType` callback apparaît automatiquement dans le
  dropdown existant. Feature confirmée **backend-only**.
- **[Résolu — Phase 3, 2026-09-02] Nom du connecteur/de la capability** — `ConnectorCapability.CALLBACK`
  (nouvelle valeur d'enum) tranché via `AskUserQuestion`, alternative (sous-type de `TRIGGER`)
  écartée pour éviter le couplage que la portée V1 exclut.
- **[Résolu — Phase 4, 2026-09-02] Modélisation BPMN de référence** — pas de process d'exemple
  livré dans `src/main/resources/processes/` en V1 : aucun `WorkflowTypeConfig` existant n'en a
  besoin, et `DeployBpmnProcessUseCaseImpl.deploy(...)` exige un `WorkflowTypeConfig` pour la clé
  déployée (en créer un fictif serait hors scope produit). Le process synthétique nécessaire pour
  prouver le comportement moteur reste un fixture de test (`src/test/resources/**`).
