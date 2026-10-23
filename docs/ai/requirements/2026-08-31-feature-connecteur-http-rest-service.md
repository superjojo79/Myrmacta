---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
---

# Requirements & Problem Understanding

## Problem Statement
**What problem are we solving?**

- Le connecteur `http-rest` ne peut aujourd'hui être utilisé qu'en rôle **TRIGGER** (réception de
  webhook entrant). Un `serviceTask` BPMN, ou un appel API direct (BFF/M2M), ne peut pas appeler un
  service REST externe via le système de connecteurs — alors que le mécanisme générique
  d'exécution SERVICE (`ConnectorServiceTask`, `ConnectorExecuteController`,
  `M2mConnectorExecuteController`, port `ExecuteConnectorUseCase`) est déjà en production et
  documenté (`GUIDE-CONCEPTION-BPMN.md` §4/§7), exercé aujourd'hui uniquement par `flowable-signal`.
- Un skeleton existe déjà : `HttpRestServiceConnector`
  (`infrastructure/connector/builtin/HttpRestServiceConnector.java`, livré avec
  `durcissement-donnees-sensibles`, PR #62) implémente `ServiceConnector`, réutilise
  `OutboundUrlGuard` (hardening SSRF déjà livré), et protège déjà contre la fuite de
  `instance.config()` dans le résultat — mais il n'implémente **pas** `ConnectorDefinition` et
  n'est enregistré nulle part. `ExecuteConnectorUseCaseImpl` rejette explicitement toute demande
  SERVICE sur le type `http-rest` (`UnsupportedOperationException`).
- Affecté : tout concepteur de workflow BPMN qui a besoin d'appeler un service REST externe depuis
  un `serviceTask` (contournement actuel : écrire un connecteur custom au classpath, ou détourner
  `flowable-signal`, aucun des deux n'étant conçu pour ce cas d'usage) et tout appelant M2M/BFF
  souhaitant invoquer un appel HTTP sortant via `/api/connectors/{instanceId}/execute`.
- **Contrainte de modélisation identifiée en analyse** (pas un simple oubli d'enregistrement) :
  `ConnectorCatalog` indexe par `ConnectorDefinition.getType()` — un seul bean peut porter le type
  `"http-rest"`. Le skeleton, en tant que classe séparée, ne peut donc pas être « juste enregistré »
  à côté de `HttpRestTriggerConnector` (`@Component`, capability `TRIGGER`) sans conflit de type.

Référence : [docs/specs/18-connecteur-http-rest-service.md](../../specs/18-connecteur-http-rest-service.md).

## Goals & Objectives
**What do we want to achieve?**

Primaire :

- **D-CAPABILITY-SCOPE = fusion au niveau du type** (décidé 2026-08-31) : `HttpRestTriggerConnector`
  implémente désormais aussi `ServiceConnector` ; `getCapability()` passe de `TRIGGER` à `BOTH`. Une
  seule classe, un seul bean, cohérent avec le seul autre connecteur `BOTH` du dépôt
  (`FlowableSignalConnector`). Toute instance `http-rest` existante ou future devient utilisable dans
  les deux rôles.
- **D-HTTP-METHODS = GET + POST/PUT avec corps JSON** (décidé 2026-08-31) : la méthode HTTP, l'URL et
  le corps (le cas échéant) sont fournis dynamiquement via `connectorInput` à chaque appel — même
  principe que l'URL aujourd'hui dans le skeleton, pas de nouveau champ de config pour ça.
- **D-HTTP-CONFIG = en-tête d'authentification statique en config** (décidé 2026-08-31) : nouveau
  champ optionnel `authHeaderValue` (`ConfigFieldType.PASSWORD`, chiffré au repos comme
  `webhookSecret`) dans `getConfigSchema()`. S'il est renseigné, envoyé automatiquement comme
  en-tête `Authorization` sur chaque appel SERVICE de l'instance — évite de faire circuler un
  secret en clair dans les variables de process BPMN à chaque appel.
- **D-HTTP-NAMING = renommage** (décidé 2026-08-31) : `HttpRestTriggerConnector` →
  `HttpRestConnector` (le nom « Trigger » devient trompeur une fois `BOTH`). Classe interne, aucune
  API publique concernée.
- **D-HTTP-TESTS = fusion des tests** (décidé 2026-08-31) : `HttpRestServiceConnectorTest.java`
  fusionné dans `HttpRestConnectorTest.java` (renommé en cohérence avec D-HTTP-NAMING). Les deux
  tests qui verrouillent explicitement l'état « non activé »
  (`connector_doesNotImplementConnectorDefinition`, `connector_isNotASpringBean`) sont inversés en
  assertions positives (implémente `ConnectorDefinition`, `getCapability() == BOTH`, `@Component`
  présent). Le reste des tests (SSRF, anti-fuite `instance.config()`) migre à l'identique.

Secondaire :

- Mettre à jour la documentation qui affirme aujourd'hui explicitement « TRIGGER seulement » :
  tableau catalogue de [06-connecteurs.md](../../specs/06-connecteurs.md) (colonne « V1
  livré »), [README.md](../../../README.md) (§ Système de connecteurs) — convention
  `flowforge-conventions` §6.

Non-goals (explicitement hors périmètre V1, cf. analyse §2) :

- Connecteurs `scheduler`, `smtp`, `s3-minio`, Kafka, import batch, ERP — toujours hors V1, non
  concernés par cette feature.
- Authentification sortante avancée (flux OAuth2 client credentials vers le service tiers appelé) —
  un en-tête statique (D-HTTP-CONFIG) est le maximum de cette V1.
- Politique de retry/backoff spécifique à ce connecteur — le comportement d'erreur actuel
  (`IllegalStateException` sur échec réseau) remonte tel quel à l'appelant, cohérent avec l'absence
  de retry générique aujourd'hui sur `ExecuteConnectorUseCase`.
- Parsing structuré de la réponse (JSON typé, XML…) — le corps de réponse reste une chaîne opaque
  (`response.body()`) dans `connectorResult`.
- Capacité par instance (D-CAPABILITY-SCOPE option non retenue) — pas de champ permettant de
  limiter une instance `http-rest` donnée à un seul rôle (TRIGGER seul ou SERVICE seul) ; toute
  instance devient utilisable dans les deux rôles dès cette feature livrée.

## User Stories & Use Cases
**How will users interact with the solution?**

- En tant que concepteur de workflow BPMN, je veux appeler un service REST externe depuis un
  `serviceTask` (`${connectorServiceTask}`) en configurant une instance `http-rest`, sans écrire de
  connecteur custom.
- En tant que `TENANT_ADMIN`, je veux créer une instance `http-rest` utilisable indifféremment en
  TRIGGER (webhook entrant) et/ou en SERVICE (appel sortant), depuis le même écran d'admin
  connecteurs déjà générique (aucun changement d'écran attendu — formulaire piloté par
  `getConfigSchema()`).
- En tant que `TENANT_ADMIN`, je veux pouvoir renseigner un token d'authentification statique pour
  une instance `http-rest`, chiffré au repos, envoyé automatiquement en en-tête `Authorization` sur
  chaque appel SERVICE — sans avoir à le faire transiter en clair dans les variables du process.
- En tant qu'appelant M2M (`API_SERVICE`), je veux invoquer `POST
  /api/connectors/{instanceId}/execute` sur une instance `http-rest` et recevoir le statut + corps
  de la réponse HTTP distante.
- En tant que concepteur de workflow, je veux que l'appel échoue explicitement (pas d'appel HTTP
  effectué) si l'URL cible pointe vers un réseau privé/loopback/lien local — garantie déjà offerte
  par `OutboundUrlGuard`, à préserver telle quelle.

Cas limites :

- `connectorInput` sans `url` → rejet immédiat (`IllegalArgumentException`), pas d'appel HTTP
  tenté — comportement déjà couvert par le skeleton, à préserver.
- URL résolvant vers une adresse bloquée (loopback, réseau privé, lien local, `.local`) → rejet
  avant tout appel réseau, comportement `OutboundUrlGuard` déjà couvert par les tests existants
  (`execute_pinsResolvedIpWithoutSecondLookup`, etc.), à préserver à l'identique.
- Instance `http-rest` sans `authHeaderValue` renseigné → aucun en-tête `Authorization` ajouté,
  comportement rétrocompatible avec les instances TRIGGER-seules déjà existantes.
- Résultat de l'appel ne doit **jamais** contenir `instance.config()` (donc jamais
  `authHeaderValue` en clair) — contrat déjà prouvé par test
  (`execute_neverEchoesInstanceConfigInResult`), à préserver pour toute méthode HTTP ajoutée.
- Une instance `http-rest` existante, créée avant cette feature (capability `TRIGGER` au moment de
  la création), devient automatiquement utilisable en SERVICE dès le déploiement — aucune migration
  de données nécessaire (`ConnectorInstanceData` ne stocke pas la capability, elle vient du type).

## Success Criteria
**How will we know when we're done?**

- Un `serviceTask` `${connectorServiceTask}` référençant une instance `http-rest` exécute un appel
  HTTP réel (GET/POST/PUT) et expose le résultat (`status`, `body`) en variable de process
  `connectorResult`.
- `POST /api/connectors/{instanceId}/execute` (BFF et M2M) fonctionne sur une instance `http-rest`
  sans lever `UnsupportedOperationException`.
- Le hardening SSRF (`OutboundUrlGuard`, pin IP D-44) s'applique sans exception à tout appel
  `http-rest` SERVICE, méthode et corps compris.
- `instance.config()` (dont `authHeaderValue`) n'apparaît jamais dans le résultat retourné, quel
  que soit le chemin d'appel.
- `HttpRestConnectorTest.java` : tous les tests hérités du skeleton passent inchangés côté
  comportement (SSRF, anti-fuite), plus de nouveaux tests couvrant `getCapability() == BOTH`,
  `implements ConnectorDefinition`, POST/PUT avec corps, en-tête `Authorization` conditionnel.
- `06-connecteurs.md` et `README.md` mis à jour (plus de mention « TRIGGER seulement » pour
  `http-rest`).
- `./mvnw verify` vert. Feature **confirmée backend seule** (vérifié en Phase 2 contre le code réel,
  voir Constraints & Assumptions) : `npm run test`/`test:e2e` restent verts en régression complète,
  sans nouveau scénario E2E dédié — zéro fichier `frontend/**` modifié.

## Constraints & Assumptions
**What limitations do we need to work within?**

- `ConnectorCatalog`/`ConnectorRegistry` indexent par `getType()` — contrainte structurelle qui a
  guidé D-CAPABILITY-SCOPE, non négociable sans refonte plus large du registre (non envisagée ici).
- `OutboundUrlGuard` est un bean Spring déjà exposé (`NotificationConfig`), réutilisé tel quel — pas
  de nouvelle instance ni de configuration SSRF parallèle.
- Convention déjà établie : un champ de config sensible est de type `ConfigFieldType.PASSWORD`,
  chiffré au repos par `ConnectorSecretCipher` comme tout autre secret de connecteur (même
  mécanisme que `webhookSecret`) — `authHeaderValue` suit cette convention sans nouveau mécanisme
  de chiffrement.
- **Correction Phase 2 (vérifié contre le code, pas seulement contre la doc) :** l'écran d'admin
  connecteurs (`ConnectorsAdminView.vue`) n'est **pas** piloté par `ConnectorDefinition.getConfigSchema()`
  malgré ce que suggérait `GUIDE-CONCEPTION-BPMN.md` §4 « Étape 1 » (qui décrit le contrat déclaratif
  côté backend, pas l'implémentation réelle de l'écran) — `GET /api/connector-definitions` n'expose
  au frontend que `type`/`label` (`ConnectorDefinitionItem`, `frontend/src/api/admin.ts`). La saisie
  de `config` est un simple `<textarea>` de JSON brut (`form.configJson`, parsé côté client puis
  envoyé tel quel), agnostique de tout schéma. C'est déjà ainsi que `webhookSecret` est saisi
  aujourd'hui pour une instance `http-rest` TRIGGER — `authHeaderValue` suivra exactement le même
  chemin (aucun composant Vue à toucher), sans changement de comportement pour l'admin ni
  dégradation par rapport à l'existant.
- Renommage `HttpRestTriggerConnector` → `HttpRestConnector` : classe interne au package
  `infrastructure.connector.builtin`, aucun contrat REST/OpenAPI ni configuration externe (le champ
  `connectorType` stocké en base reste la chaîne `"http-rest"`, indépendante du nom de classe Java)
  — renommage sans impact de compatibilité.
- Pas de nouveau rôle ni de changement d'autorisation : les endpoints `ConnectorExecuteController`/
  `M2mConnectorExecuteController` existent déjà avec leurs gardes actuelles.

## Questions & Open Items
**What do we still need to clarify?**

Décisions actées 2026-08-31 (`AskUserQuestion`, TODO §48) :

- **D-CAPABILITY-SCOPE = fusion au niveau du type** (option recommandée retenue).
- **D-HTTP-METHODS = GET + POST/PUT avec corps JSON** (option recommandée retenue).
- **D-HTTP-CONFIG = en-tête d'authentification statique en config** (option recommandée retenue).
- **D-HTTP-NAMING = renommage en `HttpRestConnector`** (option recommandée retenue).
- **D-HTTP-TESTS = fusion dans `HttpRestConnectorTest`** (option recommandée retenue).

Résolu en revue Phase 2 (2026-08-31) :

- **Confirmation « backend-only »** — **confirmé backend seul**, vérifié contre le code réel de
  `ConnectorsAdminView.vue` (pas seulement contre la doc conceptuelle) : l'écran est un formulaire
  JSON brut agnostique de tout schéma, `authHeaderValue` sera saisissable dès l'ajout du champ côté
  backend, sans aucun changement frontend. Voir Constraints & Assumptions pour le détail — cet écart
  entre `GUIDE-CONCEPTION-BPMN.md` et le code réel est noté mais hors périmètre de correction pour
  cette feature (pas une régression introduite ici).

Restant à trancher en phase design (pas bloquant pour clore les requirements) :

- **Nom exact du champ de config** — `authHeaderValue` est un nom de travail (valeur brute de
  l'en-tête `Authorization`, ex. `"Bearer xxx"`, laissée à la charge de l'admin plutôt que de
  distinguer un schéma `Bearer`/`Basic` dédié). À confirmer en design : champ unique libre vs.
  paire structurée (type d'auth + valeur).
- **Comportement en cas de `Content-Type` non précisé pour POST/PUT** — le skeleton actuel n'envoie
  aucun en-tête. À trancher en design : `application/json` par défaut si un corps est présent, ou
  en-tête fourni explicitement par `connectorInput` (cohérent avec le principe « tout dynamique par
  appel » de D-HTTP-METHODS) ?
- **Nommage définitif du lot** dans `FILE-ATTENTE.md`/`DEFERRED-FEATURES.md` — proposition actuelle
  `connecteur-http-rest-service`, à confirmer sans urgence (n'affecte pas l'implémentation).
