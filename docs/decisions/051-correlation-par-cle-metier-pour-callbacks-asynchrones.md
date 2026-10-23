# §51 — Corrélation par clé métier pour callbacks asynchrones — connecteur `flowable-callback`

> **Statut :** clos · **Section :** [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) · ancien `TODO.md` §51 · [registre](README.md)

**Statut (2026-09-02) : clos.** Cycle `dev-lifecycle` Phases 1–9 complet, **mergée sur `main`**
([PR #102](https://github.com/superjojo79/FlowForge/pull/102), sans attendre la CI hébergée, sur
demande explicite utilisateur). `README.md` § « Système de connecteurs » et duplication interne
triviale trouvées et corrigées en Phase 9. `CHANGELOG.md` mis à jour avec le numéro de PR réel.
Implémentation complète (M1–M7) en TDD : `WorkflowEnginePort.correlateMessage`
(`ExecutionQuery`/`messageEventReceived`), `CallbackConnectorRuntime`,
`FlowableMessageCallbackConnector` (capability `CALLBACK`), `CallbackWebhookController`
(`POST /api/callbacks/{instanceId}`). `./mvnw verify` : 1533 tests, 0 échec. Zéro fichier
`frontend/**` touché. **3 écarts réels trouvés et corrigés** : Phase 5 (NPE sur variable de mapping
optionnelle non résolue, `Map.copyOf`) ; **Phase 7** (D-CALLBACK-TYPE — Flowable compare les
variables de process par type ET valeur, un `correlationValue` numérique dans le payload externe
ne matchait jamais une variable de process `String`, mode de défaillance systématique pas un cas
limite ; corrigé par normalisation en `String` dans `FlowableMessageCallbackConnector`, prouvé
contre le vrai moteur) ; **Phase 7** aussi (design promettait un 404 sur la race
`list()`/`messageEventReceived(...)`, le code livré en Phase 5 laissait fuir un
`FlowableException` brut — corrigé). Docs :
`docs/ai/{requirements,design,planning,implementation,testing}/2026-09-02-feature-connecteur-callback.md`.
Rappel Phase 3 : le mécanisme initialement proposé (`RuntimeService.createMessageCorrelation(...)`)
n'existe pas en Flowable 8.0.0 Community (vérifié contre les sources réelles téléchargées, pas
supposé) — remplacé par `ExecutionQuery`/`messageEventReceived` ci-dessus.

**Constat :** ni le rôle SERVICE du connecteur `http-rest` (`HttpRestConnector.execute`, appel
`httpClient.send(...)` bloquant, timeout fixe) ni son rôle TRIGGER (`WebhookController` →
`ConnectorTriggerRuntimeImpl.onIncomingEvent`, qui crée/attache un dossier mais ne réveille jamais
une instance de process en attente précise) ne permettent de recevoir un callback asynchrone
corrélé à un appel sortant précis. Origine : question utilisateur (2026-09-01), vérifiée contre le
code réel avant de répondre.

**Mécanisme retenu (tranché, corrigé en Phase 3) :**
```java
List<Execution> matches = runtimeService.createExecutionQuery()
        .messageEventSubscriptionName(messageName)
        .processVariableValueEquals(correlationKey, correlationValue)
        .list();
// 0 -> erreur (404), >1 -> erreur (409), 1 -> :
runtimeService.messageEventReceived(messageName, matches.get(0).getId(), payload);
```
Déjà disponible sur le `RuntimeService`/`ExecutionQuery` injectés, **jamais utilisé** dans le dépôt
aujourd'hui (confirmé par grep). Résout l'exécution en attente sur un
`intermediateCatchEvent`/`receiveTask` dont une variable de **process** correspond à une clé de
corrélation choisie par le concepteur BPMN — pas le `businessKey` du process (`link.id()`, déjà
utilisé par `WorkflowEnginePort.start`), qui ne distingue pas plusieurs callbacks parallèles pour
un même dossier.

**Points ouverts — tous tranchés (2026-09-02) :**

1. **D-CALLBACK-RACE** — rejet HTTP 404/409, retry côté système externe. Pas de file d'attente
   persistée.
2. **D-CALLBACK-IDEMPOTENCE** — réutilisation telle quelle de `webhook_idempotency` /
   `JpaWebhookIdempotencyStoreAdapter`.
3. **D-CALLBACK-MULTI-MATCH** — erreur remontée (409) sur `matches.size() > 1`, jamais de
   traitement silencieux du premier résultat.
4. **D-CALLBACK-MESSAGE-NAME** — `messageName` fixe par instance de connecteur, pas dynamique par
   appel.
5. **Nom du connecteur/capability** — nouvelle valeur `ConnectorCapability.CALLBACK`, tranché en
   Phase 3 via `AskUserQuestion`.
6. **Vérification déploiement BPMN** — faite en Phase 3 : `DeployBpmnProcessUseCaseImpl`/
   `FlowableWorkflowDeploymentAdapter` déploient de façon générique, aucune restriction sur les
   éléments BPMN.

**À produire (Phase 5, implémentation) :** `WorkflowEnginePort.correlateMessage(...)` +
implémentation Flowable ci-dessus, nouveau connecteur callback + endpoint HTTP authentifié (HMAC,
patron `WebhookController`), doc catalogue à jour.

**Impacte :** `WorkflowEnginePort`, `FlowableWorkflowEngineAdapter`, nouveaux fichiers
`domain/connector/model/CallbackConnector.java` (nom provisoire) + adaptateur infra dédié,
`06-connecteurs.md`, `README.md`.

**Détail :** [21-correlation-callback-async.md](../specs/21-correlation-callback-async.md).
