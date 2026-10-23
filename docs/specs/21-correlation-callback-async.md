# Corrélation par clé métier pour callbacks asynchrones (`http-rest`)

> **Statut (2026-09-02) : cycle `dev-lifecycle` en cours sur la feature `connecteur-callback`
> (TODO §51), Phases 1–3 closes.** Origine : question utilisateur (2026-09-01) — le connecteur
> `http-rest` (rôle SERVICE) peut-il recevoir un callback pour un appel asynchrone ? Réponse
> vérifiée contre le code réel : non, ni en SERVICE (appel `httpClient.send(...)` bloquant) ni en
> TRIGGER (`WebhookController`, qui crée/attache un dossier mais ne réveille pas une instance de
> process en attente précise).
>
> **Correction factuelle importante (Phase 3, 2026-09-02)** : le mécanisme proposé en section 2
> ci-dessous (`RuntimeService.createMessageCorrelation(...)`) **n'existe pas** sur `RuntimeService`
> en Flowable 8.0.0 Community — vérifié en extrayant et lisant les sources réelles
> (`flowable-engine-8.0.0-sources.jar`), pas supposé, pas recopié de doc en ligne. Section 2
> corrigée en conséquence ; le mécanisme réellement retenu est équivalent en faisabilité mais
> utilise `ExecutionQuery.messageEventSubscriptionName(...).processVariableValueEquals(...)` +
> `RuntimeService.messageEventReceived(messageName, executionId, variables)`. Détail complet et
> décisions de design : [`docs/ai/design/2026-09-02-feature-connecteur-callback.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-09-02-feature-connecteur-callback.md).

---

## 1. Constat — deux mécanismes existants, aucun ne répond au besoin

Vérifié contre le code réel (pas supposé) :

| Mécanisme | Fichier | Comportement | Pourquoi ça ne corrèle pas un callback |
|---|---|---|---|
| `http-rest` SERVICE | `HttpRestConnector.execute` | `httpClient.send(...)` — appel HTTP **bloquant**, timeout fixe (`ConnectorProperties.Http.requestTimeout`, 5 s par défaut) | Aucune réception de réponse différée : le `serviceTask` attend dans le même appel, point |
| `http-rest` TRIGGER | `WebhookController` → `ConnectorTriggerRuntimeImpl.onIncomingEvent` | Reçoit un POST entrant, résout `WorkflowTriggerEvent` → `resolveOrCreateDossier` : soit crée un **nouveau** dossier (`dossierTypeCode`), soit no-op si `existingDossierReference` pointe un dossier dont le workflow est déjà actif (`OutboxRelayScheduler.reserveOrResume`, dédoublonnage) | Aucune injection de variables dans une tâche en attente précise du process en cours — pas de sémantique « reprendre ce point d'attente avec ce résultat » |
| `flowable-signal` | `FlowableSignalEventListener` (écoute `ACTIVITY_SIGNALED`) | Réveille des instances en attente d'un signal BPMN, mais c'est un **signal interne au moteur** (émis par un autre process du même moteur), broadcast par nom de signal, pas déclenchable par HTTP externe, pas corrélé par clé métier | Mauvais outil : ni externe, ni ciblé |

Confirmé par grep : `MessageCorrelation`/`createMessageCorrelation`/`IntermediateCatchEvent`/
`ReceiveTask` — **aucune occurrence** dans `src/main/java` ni dans les processes BPMN livrés
(`src/main/resources/processes/*.bpmn20.xml`). Le patron n'existe pas encore dans le dépôt.

---

## 2. Mécanisme proposé — corrélation par message Flowable, pas par businessKey

### Pourquoi pas le `businessKey` déjà utilisé

`WorkflowEnginePort.start(...)` démarre déjà chaque process avec un `businessKey` =
`link.id().toString()` (`DossierWorkflowLink`, cf. `OutboxRelayScheduler.processOneInternal`), et
`findProcessInstanceIdByBusinessKey` existe déjà. Mais le businessKey est corrélé à **un process par
dossier**, pas à un appel service précis — un même process peut faire plusieurs appels asynchrones
en parallèle (attendre 3 callbacks différents pour 3 systèmes externes), chacun avec sa propre clé
de corrélation choisie par le concepteur BPMN. Le businessKey ne peut pas distinguer ces cas.

### Le bon outil — `ExecutionQuery` + `RuntimeService.messageEventReceived(...)`

**Corrigé en Phase 3 (2026-09-02)** — la première version de cette section proposait
`runtimeService.createMessageCorrelation(messageName).processInstanceVariableEquals(...)
.setVariables(...).correlate()`. Cette méthode **n'existe pas** sur `RuntimeService` en
Flowable 8.0.0 Community : confirmé par `javap` sur `flowable-engine-8.0.0.jar` (absente de
l'interface) puis par lecture des sources réelles (`flowable-engine-8.0.0-sources.jar`,
`org/flowable/engine/RuntimeService.java` et `org/flowable/engine/runtime/ExecutionQuery.java`).

Le mécanisme réellement disponible dans cette version, tout aussi natif et sans nouvelle
dépendance :

```java
List<Execution> matches = runtimeService.createExecutionQuery()
        .messageEventSubscriptionName(messageName)
        .processVariableValueEquals(correlationKey, correlationValue)
        .list();
// 0 résultat -> pas d'exécution en attente (à traiter en erreur, cf. section 5)
// >1 résultat -> ambiguïté de modélisation BPMN (à traiter en erreur, cf. section 5)
// 1 résultat -> reprise :
runtimeService.messageEventReceived(messageName, matches.get(0).getId(), payload);
```

`messageEventSubscriptionName(String)` sélectionne les exécutions abonnées à ce message BPMN (nom
de l'attribut `name` de l'élément `message` dans le XML). **`processVariableValueEquals`** (pas
`variableValueEquals`, qui ne matche que les variables **locales** de l'exécution en attente) sur
la variable de process posée par le concepteur BPMN. `messageEventReceived(messageName,
executionId, variables)` réveille l'exécution ciblée avec les variables du callback. Contrairement
au mécanisme initialement envisagé, la distinction 0/1/plusieurs se fait explicitement sur la
liste retournée — pas en interprétant un type d'exception Flowable interne non documenté.

Vérifié : `FlowableBpmnParserAdapter.extractProcessDefinitionKey` ne fait que
`convertToBpmnModel` + extraire l'id du process, aucune restriction sur les éléments BPMN ;
`FlowableWorkflowDeploymentAdapter.deploy(...)` fait un `repositoryService.createDeployment()
.addBytes(...).deploy()` générique, aucune liste blanche d'éléments — un `receiveTask`/message
event standard se déploierait et s'exécuterait sans changement du chemin de déploiement existant
(`DeployBpmnProcessUseCaseImpl`), vérifié contre le code réel en Phase 3, pas supposé.

---

## 3. Composants à ajouter

### `WorkflowEnginePort` — nouvelle méthode

```java
void correlateMessage(String messageName, String correlationKey, Object correlationValue, Map<String, Object> variables);
```

Implémentée dans `FlowableWorkflowEngineAdapter` via `ExecutionQuery` + `messageEventReceived(...)`
ci-dessus (section 2). 0 ou plusieurs exécutions correspondantes → erreur explicite levée par notre
propre code sur la liste retournée — comportement voulu par défaut, une valeur de corrélation
cible une reprise précise, jamais un broadcast.

### Un nouveau type de connecteur, pas une extension du `TriggerConnector` existant

`TriggerConnector.onEvent()` produit un `WorkflowTriggerEvent` dont toute la sémantique est
« créer ou attacher un dossier » (`ConnectorTriggerRuntimeImpl.resolveOrCreateDossier`) — à côté du
besoin ici (reprendre une instance déjà en cours, jamais en créer une nouvelle). Proposition :
interface dédiée dans `domain/connector/model` (nom provisoire `CallbackConnector`), avec sa propre
capability et son propre point d'entrée infra qui appelle `correlateMessage` directement — **sans
passer par `ConnectorTriggerRuntimeImpl`**.

### Endpoint HTTP entrant

Même authentification que `WebhookController` (HMAC par instance, `HmacWebhookAuthenticator`
réutilisable tel quel), mais routage différent : `messageName` fixé par la config du connecteur,
`correlationKey`/valeur extraits du payload entrant (JSONPath, `SimpleJsonPathMapper` déjà utilisé
par `http-rest`).

### Remise de la clé de corrélation au système externe

Aucune plomberie nouvelle nécessaire : `serviceTask` avec `http-rest` passe déjà n'importe quelle
variable de process en entrée (`connectorInput`/`ExecuteConnectorRequest.toInputParams()`). Le
concepteur BPMN choisit une valeur (référence dossier, ID métier généré) et l'envoie dans l'appel
sortant initial ; le système externe la renvoie dans son callback.

---

## 4. Exemple de modélisation BPMN visée

```xml
<serviceTask id="appelAsync" flowable:delegateExpression="${connectorServiceTask}">
  <!-- appel http-rest SERVICE, envoie processInstanceBusinessKey ou une variable dédiée
       "correlationKey" dans le corps/en-tête de la requête sortante -->
</serviceTask>
<intermediateCatchEvent id="attenteCallback">
  <messageEventDefinition messageRef="callbackSystemeExterne" />
</intermediateCatchEvent>
```

Le connecteur callback, à réception d'un POST externe contenant la valeur de corrélation, appelle
`correlateMessage("callbackSystemeExterne", "correlationKey", valeurRecue, payloadVariables)` — le
process reprend exactement à `attenteCallback` avec les variables du callback.

---

## 5. Points ouverts — tous tranchés en Phases 1–3 du cycle `dev-lifecycle` (2026-09-02)

Détail complet et alternatives considérées : `docs/ai/requirements/` et `docs/ai/design/
2026-09-02-feature-connecteur-callback.md`.

1. **D-CALLBACK-RACE** — **tranché** : rejet HTTP 404/409 si le callback arrive avant que
   l'exécution atteigne le `receiveTask`/`intermediateCatchEvent` (`ExecutionQuery` vide), le
   système externe doit retenter. Pas de file d'attente persistée en V1.
2. **D-CALLBACK-IDEMPOTENCE** — **tranché** : réutilisation telle quelle du mécanisme déjà livré
   pour `http-rest` TRIGGER (`webhook_idempotency`, `JpaWebhookIdempotencyStoreAdapter`), aucune
   nouvelle table.
3. **D-CALLBACK-MULTI-MATCH** — **tranché** : plusieurs exécutions en attente avec la même valeur
   de corrélation → erreur de modélisation BPMN remontée explicitement (409), jamais un
   traitement silencieux du premier résultat.
4. **D-CALLBACK-MESSAGE-NAME** — **tranché** : `messageName` fixe par instance de connecteur
   (cohérent avec `signalName` de `flowable-signal`), pas dynamique par appel.
5. **Nom du connecteur/de la capability** — **tranché** : `ConnectorCapability.CALLBACK`
   (nouvelle valeur d'enum), pas un sous-type de `TRIGGER`.
6. **Vérification déploiement BPMN** — **faite** : `DeployBpmnProcessUseCaseImpl`/
   `FlowableWorkflowDeploymentAdapter` déploient de façon générique, aucune restriction sur les
   éléments BPMN — un `receiveTask`/`intermediateCatchEvent` à message se déploierait sans
   changement de code.
