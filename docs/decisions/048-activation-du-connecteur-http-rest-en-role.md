# §48 — Activation du connecteur `http-rest` en rôle SERVICE

> **Statut :** clos · **Section :** [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) · ancien `TODO.md` §48 · [registre](README.md)

**Constat :** [README.md](../../README.md) et [06-connecteurs.md](../specs/06-connecteurs.md)
documentent déjà la limite : le catalogue V1 livré pour `http-rest` est **TRIGGER seulement** ; un
skeleton `HttpRestServiceConnector`
(`src/main/java/com/flowforge/infrastructure/connector/builtin/HttpRestServiceConnector.java`)
existe depuis `durcissement-donnees-sensibles` (PR #62, réutilise `OutboundUrlGuard`, anti-fuite de
secrets prouvée, 7 tests) mais n'implémente pas `ConnectorDefinition` et n'est enregistré nulle
part — `ExecuteConnectorUseCaseImpl` rejette toute demande SERVICE sur ce type
(`UnsupportedOperationException`).

**Découverte notable :** toute la chaîne de consommation SERVICE (`ConnectorServiceTask`,
`ConnectorExecuteController`, `M2mConnectorExecuteController`, doc `${connectorServiceTask}` déjà
au catalogue §7 de `docs/guides/GUIDE-CONCEPTION-BPMN.md`) est déjà générique et en production via
`flowable-signal` — rien à construire côté consommation. Le vrai obstacle est une **contrainte de
modélisation** : `ConnectorCatalog` indexe par `ConnectorDefinition.getType()`, un seul bean peut
porter `"http-rest"` — le skeleton (classe séparée) ne peut pas être enregistré tel quel à côté de
`HttpRestTriggerConnector` (`@Component`, capability `TRIGGER`) sans conflit.

**Points ouverts (à trancher en phase requirements/design, pas silencieusement) :**

1. **D-CAPABILITY-SCOPE** — fusionner la capacité SERVICE dans `HttpRestTriggerConnector`
   (capability → `BOTH`, toute instance sert aux deux rôles, cohérent avec `flowable-signal`) ou
   introduire une capacité par instance (nouveau champ de config limitant le rôle, sans précédent
   dans le code, scope plus large) ?
2. **D-HTTP-METHODS** — GET seul (statu quo skeleton) ou élargir à POST/PUT/DELETE avec corps ?
3. **D-HTTP-CONFIG** — champs de config par instance (URL de base, en-têtes par défaut, auth
   sortante) ou tout piloté dynamiquement par `connectorInput` à chaque appel comme aujourd'hui ?
4. **D-HTTP-NAMING** — renommer `HttpRestTriggerConnector` (ex. `HttpRestConnector`) ou garder le
   nom et ajouter l'interface `ServiceConnector` ?
5. **D-HTTP-TESTS** — sort des deux tests qui verrouillent explicitement l'état « non activé »
   (`connector_doesNotImplementConnectorDefinition`, `connector_isNotASpringBean`).

**À produire :** décisions ci-dessus, puis activation (fusion capacité + config schema le cas
échéant), avec mise à jour de `06-connecteurs.md` (tableau catalogue), `README.md` (§ Système de
connecteurs) et `DEFERRED-FEATURES.md`/`FILE-ATTENTE.md` une fois livrée.

**Impacte :** `HttpRestTriggerConnector`, `HttpRestServiceConnector`,
`HttpRestServiceConnectorTest`, `06-connecteurs.md`, `README.md`.

**Implémenté (2026-08-31, feature `connecteur-http-rest-service`, cycle `dev-lifecycle` complet) :**
les 5 décisions ci-dessus tranchées via `AskUserQuestion` (une à la fois) : D-CAPABILITY-SCOPE=fusion
au niveau du type (capability `BOTH`) ; D-HTTP-METHODS=GET+POST/PUT avec corps dynamique ;
D-HTTP-CONFIG=en-tête `Authorization` statique en config (`authHeaderValue`, chiffré) ;
D-HTTP-NAMING=renommage `HttpRestTriggerConnector`→`HttpRestConnector` ; D-HTTP-TESTS=fusion dans
`HttpRestConnectorTest`. Complétées en design par D-HTTP-AUTH-SHAPE=valeur brute de l'en-tête et
D-HTTP-CONTENT-TYPE=fourni dynamiquement par `connectorInput.headers` (pas de défaut).

**2 écarts réels trouvés en implémentation (Phase 5), absents du design :** `ExecuteConnectorRequest`
(DTO REST BFF/M2M) était câblé en dur sur les champs `flowable-signal` — `url`/`method`/`body`/
`headers` n'existaient pas dans le DTO, rendant `http-rest` SERVICE inatteignable via HTTP malgré la
capacité activée côté connecteur ; corrigé, golden OpenAPI régénéré en conséquence. **1 durcissement
trouvé en Phase 7 :** bean `HttpClient` rendu explicitement `Redirect.NEVER` (défaut JDK déjà correct,
mais implicite — une redirection suivie ne serait jamais re-vérifiée contre `OutboundUrlGuard`).

`./mvnw verify` 1420 tests (0 échec), Jacoco bundle tenu (LINE ≥85 %/BRANCH ≥75 %, 3 lacunes réelles
comblées en Phase 8 sur le code touché) ; Vitest 247/247, Playwright 59/59 (feature backend-only,
zéro fichier `frontend/**` touché, régression complète). Doc à jour :
`06-connecteurs.md`, `README.md`, `CHANGELOG.md`.

**Détail :** [18-connecteur-http-rest-service.md](../specs/18-connecteur-http-rest-service.md).
