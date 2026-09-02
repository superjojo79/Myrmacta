# Corrélation par clé métier pour callbacks asynchrones (`http-rest`)

> **Statut : première analyse fonctionnelle, aucune décision technique tranchée.** À affiner en
> phase `dev-lifecycle` (requirements → design) avant tout code — voir « Points ouverts » en fin de
> document. Origine : question utilisateur (2026-09-01) — le connecteur `http-rest` (rôle SERVICE)
> peut-il recevoir un callback pour un appel asynchrone ? Réponse vérifiée contre le code réel :
> non, ni en SERVICE (appel `httpClient.send(...)` bloquant) ni en TRIGGER (`WebhookController`,
> qui crée/attache un dossier mais ne réveille pas une instance de process en attente précise).
> Analyse demandée pour évaluer la faisabilité d'une corrélation par clé métier. Aucune feature
> ouverte à ce stade — cette analyse ne préjuge pas de la priorité dans la file d'attente.

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

### Le bon outil — `RuntimeService.createMessageCorrelation(...)`

Flowable expose nativement, sur le `RuntimeService` déjà injecté dans
`FlowableWorkflowEngineAdapter` :

```java
runtimeService.createMessageCorrelation(messageName)
        .processInstanceVariableEquals(correlationKey, correlationValue)
        .setVariables(payload)
        .correlate();
```

Ça résout **l'unique** instance de process en attente sur un `intermediateCatchEvent`/`receiveTask`
dont une variable de process nommée `correlationKey` vaut `correlationValue`, et injecte `payload`
comme variables au réveil. C'est le patron BPMN standard pour ce cas — rien à réinventer côté
moteur.

Vérifié : `FlowableBpmnParserAdapter.extractProcessDefinitionKey` ne fait que
`convertToBpmnModel` + extraire l'id du process, aucune restriction sur les éléments BPMN — un
`receiveTask`/message event standard se déploierait et s'exécuterait sans changement du chemin de
déploiement existant (`DeployBpmnProcessUseCaseImpl`/`FlowableWorkflowDeploymentAdapter`, à
vérifier explicitement en design, pas supposé).

---

## 3. Composants à ajouter

### `WorkflowEnginePort` — nouvelle méthode

```java
void correlateMessage(String messageName, String correlationKey, Object correlationValue, Map<String, Object> variables);
```

Implémentée dans `FlowableWorkflowEngineAdapter` via l'appel `RuntimeService` ci-dessus.
`correlate()` (singulier, pas `correlateAll()`) : lève si 0 ou plus d'une instance en attente
correspond — comportement voulu par défaut, une valeur de corrélation cible une reprise précise, pas
un broadcast.

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

## 5. Points ouverts (à trancher en phase requirements/design)

1. **D-CALLBACK-RACE** — le callback peut arriver avant que l'instance atteigne le
   `receiveTask`/`intermediateCatchEvent` (le système externe répond plus vite que le process
   n'avance). `correlate()` lève dans ce cas (`FlowableObjectNotFoundException` ou équivalent).
   Options : (a) rejeter 404/409, le système externe doit retry (simple, cohérent avec le
   comportement HTTP habituel d'un webhook) ; (b) file d'attente persistée de callbacks non
   corrélés avec relance différée (scope nettement plus large, table dédiée + job, à ne considérer
   que si un besoin réel est confirmé).
2. **D-CALLBACK-IDEMPOTENCE** — un système externe qui retente un callback (à cause d'un 5xx
   transitoire, timeout réseau) ne doit pas corréler deux fois. Réutilisation directe envisagée du
   mécanisme d'idempotence déjà livré pour `http-rest` TRIGGER (`webhook_idempotency`,
   `JpaWebhookIdempotencyStoreAdapter`) — même patron, pas de nouvelle table a priori.
3. **D-CALLBACK-MULTI-MATCH** — plusieurs instances en attente avec la même valeur de corrélation :
   `correlate()` lève (erreur de modélisation BPMN à remonter clairement, pas un cas à masquer
   silencieusement en tombant sur `correlateAll()`).
4. **D-CALLBACK-MESSAGE-NAME** — `messageName` fixe par instance de connecteur (cohérent avec
   `signalName` de `flowable-signal`, un connecteur = un canal de callback) vs dynamique par appel
   (plus flexible, plus complexe à valider/sécuriser).
5. **Nom du connecteur/de la capability** — `CallbackConnector` est un nom provisoire, à confirmer
   en design ; impact sur `ConnectorCapability` (nouvelle valeur d'enum ou réutilisation de
   `TRIGGER` avec un sous-type ?) à trancher explicitement, pas par défaut.
6. **Vérification à faire en design, pas supposée ici** : confirmer que
   `DeployBpmnProcessUseCaseImpl`/`FlowableWorkflowDeploymentAdapter` déploient sans changement un
   process contenant un `receiveTask`/`intermediateCatchEvent` à message (aucune raison de penser
   le contraire vu le parsing générique, mais pas vérifié en pratique — pas de process de ce type
   dans `src/main/resources/processes/` aujourd'hui).
