# §62 — Nouveaux connecteurs `batch-import` (SFTP) et `kafka` — lot B1 de la file d'attente

> **Statut :** clos · **Section :** [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) · ancien `TODO.md` §62 · [registre](README.md)

**Statut (2026-09-14) : clos.** Cycle `dev-lifecycle` Phases 1-9 complet, lot **B1** de la Vague B
([FILE-ATTENTE.md](../../FILE-ATTENTE.md)), décrit à l'origine comme « Kafka + import batch + ERP » — «
ERP » s'est avéré être le cas d'usage métier de `batch-import`
([ROADMAP.md](../../ROADMAP.md) §2.1/§2.2), pas un 3ème connecteur : périmètre réel confirmé = 2
connecteurs. **16 décisions tranchées en Phase 1 via `AskUserQuestion`**, dont plusieurs
non-recommandées choisies délibérément par l'utilisateur (thoroughness > défaut) : CSV **et**
Excel dès ce cycle (pas CSV seul) ; authentification SFTP mot de passe **et** clé privée (pas
mot de passe seul) ; tests Kafka/SFTP mockés uniquement (cohérent, pas de vrai broker/serveur SSH
dans ce cycle). **2 trouvailles réelles hors planification initiale, actées en cours
d'implémentation** : **D-BATCH-SIZE-LIMIT** (garde `maxFileBytes`/`maxRows` — absente du premier
jet de design) et **D-SFTP-HOSTKEY** (SSHJ exige un `HostKeyVerifier` explicite, pas d'acceptation
silencieuse par défaut contrairement à d'autres clients SSH — nouveau champ config requis
`sftpHostKeyFingerprint`, vérifié à chaque connexion, plutôt que d'accepter tout hôte et documenter
la limite).

**Fondation partagée (M0) :** nouveau `ConfigFieldType.SECRET_TEXTAREA` (clé privée PEM
multi-lignes) — `ConnectorCatalog.isPasswordField` élargi d'un seul `||`, propageant
automatiquement le masquage REST/MCP et le chiffrement au repos aux 4 call sites existants sans
dupliquer la logique (même source de vérité unique que TODO §56). **Bug réel trouvé et corrigé en
TDD** : un second constructeur ajouté à `ConnectorProperties` (record `@ConfigurationProperties`)
pour éviter de casser les call sites existants faisait échouer tout `ApplicationContext` au
démarrage (`NoSuchMethodException` — le binder Spring Boot exige **exactement un** constructeur
sur un tel record) ; corrigé en mettant à jour atomiquement les 8 sites d'appel plutôt que de
contourner.

**`batch-import` (M1-M2) :** nouveau port domaine `SftpClientPort` (`listFiles`/`download`/
`archive`), adaptateur réel SSHJ (API vérifiée par `javap` sur le jar réel avant écriture, pas
supposée — piège trouvé : `RemoteFile.RemoteFileInputStream` est une classe interne non statique).
Réutilise `SimpleJsonPathMapper` pour le mapping colonnes → champs (pas de nouvelle classe
`TabularRowMapper`, simplification trouvée en TDD). **Bug de conception réel trouvé en TDD** :
l'isolation de ligne invalide copiée de `HttpRestConnector` (rejet si `processKey` blanc) était
strictement inatteignable, `processKey` ayant toujours un repli d'instance — le vrai critère
(`hasBlankMappedField`, un champ **mappé** requis manquant) a été implémenté et re-testé. Job de
déclenchement cron dédié (`BatchImportConnectorTriggerJob`), isolation par instance ajoutée (écart
assumé vs `SchedulerConnectorTriggerJob`, non rétroporté — job dédié précisément pour ne pas y
toucher).

**`kafka` (M3-M4) :** `KafkaConsumerFactory`/`KafkaConsumerHandle` en seam d'infrastructure (pas un
port domaine, contrairement à `SftpClientPort` — `KafkaConsumerReconciler` est de la plomberie
infra de bout en bout). `KafkaConsumerReconciler` : nouveau pattern de cycle de vie par polling
(diff instances activées vs conteneurs en cours), API Spring Kafka vérifiée par `javap` avant
écriture (même discipline que SSHJ). Isolation par message et par instance au démarrage.

**Frontend (M6) :** `ConnectorConfigForm.vue` gagne un `<textarea>` pour `SECRET_TEXTAREA`, aucun
autre cas existant modifié. `docker-compose.yml` : service Kafka optionnel (`profiles: ["kafka"]`,
image KRaft sans Zookeeper).

**Phase 7-8 (vérification globale) :** drift golden OpenAPI trouvé et corrigé (`SECRET_TEXTAREA`
absent de l'enum `ConfigField.type` documenté, régénéré via `-Dopenapi.update=true`). Trou de
preuve comblé : aucun test n'exerçait `ConnectorRegistry` avec le contexte Spring réel pour
prouver l'absence de collision de type au démarrage — nouveau test confirmant **8 types**
enregistrés (6 déjà livrés + `batch-import` + `kafka`), premier jet écrit à tort avec un compte de
7 (oubli de `flowable-callback`, TODO §51). `./mvnw verify` : **1948 tests, 0 échec**, Jacoco
LINE/BRANCH tenus, `ArchitectureRulesTest` conforme (zéro import SSHJ/Spring Kafka/POI/Commons CSV
dans `domain`). `*IT.java` du périmètre (ce dépôt n'a pas de plugin Failsafe, même limite déjà
documentée §53) exécutées séparément : 10/10. Vitest 352/352, Playwright 72/72, typecheck/lint
propres. **1 flake préexistant hors périmètre trouvé** (`NotificationPersistenceIT.
oldestPendingAndWebhookDeadCount`, dépendant de l'ordre d'exécution des méthodes de la classe —
confirmé reproductible à l'identique sur `origin/main` nu, aucun fichier notification touché par
ce cycle) — **corrigé sur demande explicite ultérieure** (2026-09-20, hors cycle
`connecteurs-cible`) : `oldestPendingCreatedAt()` est une lecture globale, pas par tenant
(`findOldestCreatedAtByStatus` sans clause tenant), polluée par une ligne PENDING jamais nettoyée
dans `outbox_savePendingBatch_persistsAllRows` ; les deux tests nettoient désormais leurs propres
lignes et le test incriminé repart d'un état propre. Suite complète (`Test*` + `*IT.java`)
confirmée verte à 2 reprises : 2040 tests, 0 échec. Au passage (même demande), 2 autres bugs de
test sans rapport trouvés et corrigés : `EvaluateDossierRetentionUseCaseImplTest` (le use case
lisait `Instant.now()` en interne au lieu de le recevoir en paramètre, contrairement au pattern
déjà établi par `ProcessPurgeOutboxUseCase` — le test pariait sur un petit dérapage d'horloge qui
a fini par dépasser la fenêtre de rétention de 30 jours avec le temps réel qui avance) et
`DossierTest.toString_neverPrintsFieldValuesOrKeys` (un `UUID.randomUUID()` contenant
occasionnellement par hasard la séquence de chiffres testée, remplacé par un UUID fixe).

**Impacte :** `domain/connector/model/{ConfigFieldType,SftpConnectionConfig,
SftpConnectionException}.java`, `domain/connector/port/out/{ConnectorCatalog,SftpClientPort}.java`,
`infrastructure/connector/builtin/{BatchImportConnector,KafkaConnector}.java`,
`infrastructure/connector/{BatchImportConnectorTriggerJob,sftp/**,kafka/**}`,
`infrastructure/config/ConnectorProperties.java`, `docker-compose.yml`, `06-connecteurs.md`,
`docs/guides/GETTING-STARTED.md`, `openapi/openapi.yaml`, frontend `ConnectorConfigForm.vue`/`admin.ts`.
Documents lifecycle :
`docs/ai/{requirements,design,planning,implementation,testing,deployment,monitoring}/2026-09-14-feature-connecteurs-cible.md`.
**Mergée sur `main`** ([PR #129](https://github.com/superjojo79/FlowForge/pull/129), CI verte,
2026-09-20, sur demande explicite utilisateur — revue adversariale post-cycle et 3 bugs de test
préexistants sans rapport corrigés avant fusion, voir doc implementation).
