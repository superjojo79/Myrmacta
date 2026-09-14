---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: in-progress
feature: connecteurs-cible
lot: B1
---

# Implementation Guide — `connecteurs-cible`

> Planning : [2026-09-14-feature-connecteurs-cible.md](../planning/2026-09-14-feature-connecteurs-cible.md).
> Suivi milestone par milestone — mis à jour au fil de la Phase 5, pas seulement à la clôture.

## M0 — Fondations partagées (clos, 2026-09-14)

**Fichiers touchés :**

- `domain/connector/model/ConfigFieldType.java` — ajout `SECRET_TEXTAREA` (T0.1).
- `pom.xml` — ajout `spring-kafka` (géré par le BOM Spring Boot, 4.1.1 résolu), `com.hierynomus:sshj`
  (0.39.0, propriété `sshj.version`), `org.apache.poi:poi-ooxml` (5.4.1, propriété `poi.version`)
  (T0.2). Résolution vérifiée (`dependency:tree`), aucun conflit.
- `infrastructure/config/ConnectorProperties.java` — nouveaux records `BatchImport(maxFileBytes,
  maxRows)` et `Kafka(consumerPollTimeout)`, même pattern que `Http`/`Webhook`/`DocumentStorage`
  (T0.3).
- `domain/connector/port/out/ConnectorCatalog.java` — `isPasswordField` élargi pour traiter
  `SECRET_TEXTAREA` comme sensible en plus de `PASSWORD` (T0.4/T0.5 fusionnées, voir ci-dessous).
- Nouveau `domain/connector/port/out/ConnectorCatalogTest.java` (aucun test dédié n'existait pour
  cette méthode par défaut jusqu'ici, exercée seulement indirectement via `thenCallRealMethod()`
  ailleurs).
- `infrastructure/config/ConnectorPropertiesTest.java` étendu (+8 tests).

**Trouvaille en Phase 5 (T0.4/T0.5 fusionnées) :** `ConnectorCatalog.isPasswordField` est déjà la
**source de vérité unique** consultée par 4 call sites indépendants — `ConnectorApiDtos` (masquage
REST), `ConnectorInstanceMcpView` (masquage MCP), `EnvMasterKeyCipher` (chiffrement au repos),
`ManageConnectorInstanceUseCaseImpl.mergeConfig` (préservation sur update). Élargir la seule
méthode par défaut de `ConnectorCatalog` (un `||` sur `SECRET_TEXTAREA`) couvre donc les 4
comportements simultanément — confirmé en lisant chaque appelant avant de coder, pas supposé.
T0.5 (vérifier l'adaptateur de chiffrement) n'a donc nécessité **aucun changement séparé** :
`EnvMasterKeyCipher` délègue déjà à `catalog.isPasswordField(...)`.

**Bug réel trouvé et corrigé en cours de TDD (pas dans le plan initial) :** la première tentative
d'ajout de `BatchImport`/`Kafka` à `ConnectorProperties` (record `@ConfigurationProperties`)
ajoutait un **second constructeur** (4-arg, délégant au canonique 6-arg) pour éviter de casser les
8 sites d'appel positionnels existants dans le dépôt. Rouge inattendu en testant la suite de
régression complète (pas seulement les nouveaux tests) : `ApplicationContext` échouait au
démarrage avec `NoSuchMethodException: ConnectorProperties.<init>()` — le binder
`@ConfigurationProperties` de Spring Boot pour les records exige **exactement un** constructeur
pour identifier sans ambiguïté celui à utiliser pour le binding ; en présence de deux, il retombe
sur une stratégie de réflexion par constructeur sans argument, qui échoue. Corrigé en retirant le
second constructeur et en mettant à jour les 8 sites d'appel existants (7 classes de test) pour
passer explicitement `null, null` sur les 2 nouveaux paramètres — la vraie voie "breaking change,
update all callers atomically" du contrat de phase, pas le raccourci tenté d'abord. Leçon
retenue : ne **jamais** ajouter un constructeur non canonique à un record annoté
`@ConfigurationProperties`.

**Preuve fraîche :** `./mvnw test` sur `domain.connector.**`, `infrastructure.connector.**`,
`infrastructure.config.**`, `infrastructure.web.connector.**`, `infrastructure.mcp.tool.**`,
`infrastructure.persistence.connector.**` → **465 tests, 0 échec** (inclut la régression complète
des connecteurs déjà livrés — aucun `ApplicationContext` cassé par le changement de signature).
JDK 25 installé dans ce conteneur (JDK 21 seul présent par défaut, même limite déjà rencontrée et
documentée lors de `connecteur-callback` TODO §53).

## Development Setup

- JDK 25 (`java.version` du `pom.xml`) — `apt-get install openjdk-25-jdk` si seul JDK 21 est
  présent par défaut sur le conteneur, puis `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64`.
- `./mvnw test -Dskip.frontend=true` pour itérer sans le build frontend (plus rapide en TDD
  backend-only).

## Code Structure

- Pattern connecteur : `infrastructure/connector/builtin/<Type>Connector.java` implémentant
  `ConnectorDefinition` (+ `TriggerConnector`/`ServiceConnector`), auto-enregistré par
  `ConnectorRegistry` (liste Spring). Zéro modification de `ConnectorCatalog`/
  `ConnectorTriggerRuntimeImpl`/`ExecuteConnectorRequest`.
- Déclenchement cron dédié : `infrastructure/connector/<Type>ConnectorTriggerJob.java`, copie du
  pattern `SchedulerConnectorTriggerJob` (poll `@Scheduled`, filtre sur le type, réutilise
  `ConnectorScheduleStateRepository`).

## Implementation Notes

_Mis à jour au fil des milestones M1–M8._

## Integration Points

_Mis à jour au fil des milestones._

## Error Handling

- Isolation par ligne/message au niveau du connecteur, pas du pipeline partagé
  (`ConnectorTriggerRuntimeImpl` inchangé) — voir D-BATCH-ERROR-HANDLING, doc requirements.

## Performance Considerations

- `ConnectorProperties.BatchImport(maxFileBytes, maxRows)` — garde mémoire sur le parsing de
  fichier, même pattern que `Http.maxResponseBytes`.

## Security Notes

- `SECRET_TEXTAREA` traité comme sensible par `ConnectorCatalog.isPasswordField` — masquage
  lecture (REST + MCP) et chiffrement au repos couverts par le même point d'extension que
  `PASSWORD`, sans dupliquer la logique (leçon TODO §56 réappliquée).
