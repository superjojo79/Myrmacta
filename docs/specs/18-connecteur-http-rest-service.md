# Activation du connecteur `http-rest` en rôle SERVICE

> **Statut : première analyse fonctionnelle, aucune décision technique tranchée.** À affiner en
> phase `dev-lifecycle` (requirements → design) avant tout code — voir « Points ouverts » en fin de
> document. Choisi comme prochaine feature le 2026-08-31 : le backlog V1 (analyses 01–17 + tous les
> points `TODO.md`) est entièrement livré, `FILE-ATTENTE.md` était explicitement vide ; ce sujet a
> été retenu parmi trois pistes proposées à l'utilisateur (les deux autres — outillage provisioning,
> sweep tech-debt — restent en réserve, voir `DEFERRED-FEATURES.md` §1/§4).

---

## 1. Constat — l'écart actuel

[README.md](../../README.md) et [06-connecteurs.md](06-connecteurs.md) le documentent déjà en toutes
lettres : le catalogue V1 livré pour `http-rest` est **TRIGGER seulement** ; la capacité SERVICE est
un « skeleton non enregistré ».

Constat confirmé côté code :

- `HttpRestServiceConnector`
  (`src/main/java/com/flowforge/infrastructure/connector/builtin/HttpRestServiceConnector.java`)
  existe et fonctionne déjà en isolation : il réutilise `OutboundUrlGuard` (hardening SSRF — pin IP,
  blocage réseaux privés/loopback/link-local, déjà livré pour les webhooks sortants de notification)
  pour un appel HTTP GET, et ne renvoie jamais `instance.config()` dans son résultat (contrat
  anti-fuite de secrets, D-SECRETS-AUDIT). 7 tests dédiés le couvrent
  (`HttpRestServiceConnectorTest.java`), y compris deux qui **verrouillent explicitement l'état
  actuel** : `connector_doesNotImplementConnectorDefinition` et `connector_isNotASpringBean`.
- Mais il **n'implémente pas `ConnectorDefinition`** et n'est annoté ni `@Component` : `Connector­Registry`
  ne le charge jamais. Aucun appel ne peut l'atteindre — `ExecuteConnectorUseCaseImpl.execute(...)`
  résout le type depuis `ConnectorCatalog.get(instance.connectorType())`, qui ne connaît que le bean
  `HttpRestTriggerConnector` (capability `TRIGGER`), et rejette explicitement toute demande SERVICE
  sur ce type avec `UnsupportedOperationException`.
- **Tout le reste de la chaîne de consommation SERVICE est déjà en production**, générique, et ne
  nécessite aucune évolution : `ConnectorServiceTask` (JavaDelegate Flowable,
  `@Component("connectorServiceTask")`,
  `src/main/java/com/flowforge/infrastructure/flowable/ConnectorServiceTask.java`), le contrôleur
  BFF `ConnectorExecuteController` et le contrôleur M2M `M2mConnectorExecuteController`
  (`infrastructure/web/connector`) appellent tous le même port `ExecuteConnectorUseCase`, déjà
  exercé aujourd'hui par le connecteur `flowable-signal` (seul type actuellement `BOTH`). Le
  `serviceTask` `${connectorServiceTask}` est déjà documenté au catalogue §7 de
  [GUIDE-CONCEPTION-BPMN.md](../../GUIDE-CONCEPTION-BPMN.md), avec un exemple générique
  d'instanciation/appel (§4) qui ne mentionne aucun type en particulier.
- L'écran d'admin connecteurs est lui aussi générique : il construit son formulaire depuis
  `ConnectorDefinition.getConfigSchema()` (`ConfigField` déclaratif) — d'après
  [GUIDE-CONCEPTION-BPMN.md §4](../../GUIDE-CONCEPTION-BPMN.md) « Étape 1 », créer une instance d'un
  nouveau type ne demande *a priori* aucun changement d'écran.

Autrement dit, comme pour `schema-champs-dynamiques` (§17) : le gros du travail (hardening
SSRF, plomberie d'exécution, UI d'instanciation) est déjà livré. Ce qui manque est plus étroit
qu'il n'y paraît, mais touche une **contrainte de modélisation** qu'il faut clarifier avant de
coder — voir §3.

---

## 2. Périmètre proposé

### Inclus (à confirmer en requirements)

1. Le connecteur `http-rest` devient appelable en rôle SERVICE depuis un `serviceTask`
   (`${connectorServiceTask}`), l'API BFF (`ConnectorExecuteController`) et l'API M2M
   (`M2mConnectorExecuteController`) — les trois consommateurs génériques existants, sans
   modification de leur code.
2. Le hardening SSRF déjà livré (`OutboundUrlGuard`, pin IP D-44) s'applique à tout appel sortant
   `http-rest` SERVICE, sans exception ni contournement.
3. Mise à jour de la documentation qui affirme aujourd'hui explicitement « TRIGGER seulement » :
   tableau catalogue de [06-connecteurs.md](06-connecteurs.md), [README.md](../../README.md) (§ Système
   de connecteurs), et le point `TODO.md`/`FILE-ATTENTE.md` qui référence ce skeleton comme
   « hors V1 ».
4. Sort des deux tests qui verrouillent volontairement l'état « non activé »
   (`connector_doesNotImplementConnectorDefinition`, `connector_isNotASpringBean`) — à réécrire pour
   refléter le nouvel état, pas à supprimer sans remplacement (les autres tests du fichier couvrent
   le SSRF/anti-fuite et doivent survivre à l'activation).

### Explicitement hors périmètre (V1)

| Sujet | Pourquoi hors périmètre |
|---|---|
| Connecteurs `scheduler`, `smtp`, `s3-minio`, Kafka, import batch, ERP | Toujours hors V1 (README.md) — non concernés par cette feature, qui ne porte que sur `http-rest`. |
| Authentification sortante avancée (OAuth2 client credentials vers le service tiers appelé) | Le skeleton actuel ne porte aucune authentification sortante ; un simple en-tête statique (`Authorization` fixe en config `PASSWORD`) est le maximum raisonnable pour cette V1 — un flux OAuth2 complet côté connecteur serait un lot séparé. |
| Connecteur `http-rest` comme **cible** de retry/backoff applicatif dédié | Le comportement d'erreur du skeleton (`IllegalStateException` sur échec réseau) remonte tel quel à l'appelant (`ConnectorServiceTask`/contrôleurs) — pas de politique de retry spécifique à ce connecteur en V1, cohérent avec l'absence de retry générique aujourd'hui sur `ExecuteConnectorUseCase`. |
| Réponses non-JSON / autres formats de corps | Le skeleton actuel traite le corps de réponse comme une chaîne opaque (`response.body()`) ; aucun parsing structuré n'est proposé — resterait à la charge du BPMN consommateur (`connectorResult`) si besoin. |

---

## 3. Le point qui n'est pas prêt : une contrainte de modélisation, pas un trou technique

C'est la découverte la plus significative de cette analyse, et elle **conditionne le design** :
`ConnectorCatalog`/`ConnectorRegistry` indexent les définitions par `ConnectorDefinition.getType()`
dans une `Map<String, ConnectorDefinition>` — **un seul bean peut revendiquer le type `"http-rest"`.**

Aujourd'hui ce bean est `HttpRestTriggerConnector`
(`src/main/java/com/flowforge/infrastructure/connector/builtin/HttpRestTriggerConnector.java`,
`@Component`), qui implémente `ConnectorDefinition` + `TriggerConnector` et déclare
`getCapability() = ConnectorCapability.TRIGGER`. `ExecuteConnectorUseCaseImpl` vérifie à la fois
`definition.getCapability().includesService()` **et** `definition instanceof ServiceConnector` avant
d'exécuter — les deux doivent être vrais sur le **même objet**.

Conséquence directe : le skeleton `HttpRestServiceConnector`, en tant que **classe séparée**, ne
peut structurellement pas être « juste enregistré » tel quel à côté de `HttpRestTriggerConnector` —
cela produirait deux beans `ConnectorDefinition` pour le même type, un conflit à l'initialisation du
registre. Il faut soit :

- **(a)** faire porter la capacité `ServiceConnector` par `HttpRestTriggerConnector` lui-même
  (implémente aussi `ServiceConnector`, capability devient `BOTH`, la logique du skeleton est
  fusionnée dedans — renommage de la classe envisageable, ex. `HttpRestConnector`) ;
- **(b)** introduire un mécanisme de capacité **par instance** plutôt que par type (un champ de
  config limitant une instance donnée à TRIGGER seul, SERVICE seul, ou les deux) — changement de
  modèle plus large que ce que documente aujourd'hui `ConnectorCapability` (propriété du *type*,
  pas de l'*instance*), à ne pas sous-estimer.

L'option (a) est la plus proche du skeleton existant et du modèle actuel (capability = propriété du
type, cohérent avec `flowable-signal` qui est déjà `BOTH` sur une seule classe). L'option (b) répond
à un besoin réel non couvert aujourd'hui — un admin qui veut un `http-rest` **TRIGGER seul** (webhook
entrant) sans exposer la capacité SERVICE sur cette même instance — mais n'a aucun précédent dans le
code actuel et élargirait significativement le scope. Voir D-CAPABILITY-SCOPE en points ouverts.

---

## 4. Modèle domaine — ce qui existe déjà et ce qui manque

```java
// domain/connector/model — déjà existant, inchangé quelle que soit l'option retenue
public interface ServiceConnector {
    Map<String, Object> execute(ConnectorInstanceData instance, Map<String, Object> inputParams);
}

public enum ConnectorCapability {
    TRIGGER, SERVICE, BOTH;
    public boolean includesService() { return this == SERVICE || this == BOTH; }
}
```

Ce qui **manque réellement** n'est pas le port domaine (déjà écrit, déjà exercé par
`flowable-signal`), mais la déclaration de configuration pour l'usage SERVICE. Le schéma actuel de
`HttpRestTriggerConnector.getConfigSchema()` ne porte que des champs **TRIGGER**
(`webhookAuthType`, `webhookSecret`, `processKey`, `dossierTypeCode`) — rien pour SERVICE. Le
skeleton, lui, ne lit **aucun** champ de config à l'exécution : l'URL vient entièrement de
`inputParams.get("url")` au moment de l'appel (variable `connectorInput` du `serviceTask`), et seule
la méthode `GET` est supportée, sans en-têtes ni corps :

```java
// infrastructure/connector/builtin/HttpRestServiceConnector.java — état actuel, isolé
public Map<String, Object> execute(ConnectorInstanceData instance, Map<String, Object> inputParams) {
    String urlString = (String) inputParams.get("url");             // dynamique par appel
    OutboundUrlGuard.PinnedEndpoint pinned = outboundUrlGuard.resolveSafe(urlString);
    HttpRequest request = HttpRequest.newBuilder(URI.create(pinned.requestUrl())).GET().build();
    // ... GET seul, pas de headers, pas de body
}
```

Si l'écran d'admin doit exposer des réglages SERVICE par instance (URL de base fixe, en-têtes par
défaut, authentification sortante — voir D-HTTP-CONFIG), `getConfigSchema()` doit grandir en
conséquence ; si au contraire tout reste piloté dynamiquement par `connectorInput` à chaque appel
(comme aujourd'hui), aucune évolution de schéma n'est nécessaire. C'est un point ouvert, pas une
évidence — voir §5.

---

## 5. Points ouverts

À trancher en phase requirements/design (`dev-lifecycle`), pas dans ce document :

1. **D-CAPABILITY-SCOPE** — option (a) capacité au niveau du *type* (`HttpRestTriggerConnector`
   devient `BOTH`, toute instance `http-rest` peut servir aux deux rôles) vs option (b) capacité au
   niveau de l'*instance* (nouveau champ de config limitant le rôle par instance) — voir §3. (a) est
   nettement plus proche du skeleton et du modèle existant (`flowable-signal`) ; (b) répond à un
   besoin produit réel mais élargit le scope et n'a aucun précédent dans le code.
2. **D-HTTP-METHODS** — GET seul (statu quo du skeleton) ou élargir à POST/PUT/DELETE avec corps
   JSON ? Un `serviceTask` qui *pousse* des données vers un système tiers (cas d'usage cité en
   exemple dans [06-connecteurs.md](06-connecteurs.md) pour un connecteur custom D365) est
   probablement le cas d'usage le plus attendu, mais n'est pas couvert par le skeleton actuel.
3. **D-HTTP-CONFIG** — le connecteur a-t-il besoin de champs de configuration par instance (URL de
   base, en-têtes par défaut, type d'authentification sortante — token statique en `PASSWORD`
   chiffré, sur le modèle de `webhookSecret`) ou reste-t-il entièrement piloté par `connectorInput`
   à chaque appel comme aujourd'hui ? Conditionne l'évolution de `getConfigSchema()`/`validate()`.
4. **D-HTTP-NAMING** — fusionner dans `HttpRestTriggerConnector` en le renommant (ex.
   `HttpRestConnector`, plus neutre si `BOTH`) ou garder le nom actuel et lui ajouter l'interface
   `ServiceConnector` ? Classe interne, aucune API publique concernée par le renommage.
5. **D-HTTP-TESTS** — sort de `HttpRestServiceConnectorTest.connector_doesNotImplementConnectorDefinition`
   et `.connector_isNotASpringBean`, qui vérifient explicitement l'état « non activé » : à réécrire
   en assertions positives (`ConnectorRegistry` résout bien `"http-rest"` en SERVICE) plutôt qu'à
   supprimer sans remplacement — le reste du fichier (SSRF, anti-fuite de secrets) doit continuer de
   protéger le code fusionné.
6. **Documentation à mettre à jour une fois livré** (rappel de convention, skill
   `flowforge-conventions` § « Workflow de feature » point 6) : tableau catalogue de
   [06-connecteurs.md](06-connecteurs.md) (colonne « V1 livré »), [README.md](../../README.md) (§
   Système de connecteurs), et tout renvoi à ce skeleton comme « hors V1 » dans `TODO.md` /
   `DEFERRED-FEATURES.md` / `FILE-ATTENTE.md`.
7. **Nommage du lot** — aucun nom n'est encore réservé dans `FILE-ATTENTE.md` /
   `DEFERRED-FEATURES.md` pour ce sujet ; proposition : `connecteur-http-rest-service` (à confirmer
   en séquençant avec le reste de la file au moment de la Phase 1 `dev-requirements`).
