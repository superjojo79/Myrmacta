# Points à traiter — FlowForge

> Registre de suivi du projet. Il comporte deux parties :
>
> - **[Points ouverts](#points-ouverts)** — issus de la revue pré-implémentation de la documentation
>   d'architecture. Ils ne sont **pas** tranchés. Le contexte détaillé de chacun (constat, extraits
>   concernés, options envisageables, documents impactés) est dans
>   [REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md), sous le même numéro de point.
> - **[Décisions actées](#décisions-actées)** — décisions prises lors de la revue de conception
>   initiale, déjà répercutées dans `analysis doc/`. Elles font office de journal ; ne les rouvrir
>   qu'avec une raison explicite.
>
> **Reste à implémenter (features / lots différés) :** synthèse consolidée dans
> [DEFERRED-FEATURES.md](DEFERRED-FEATURES.md) — follow-ups des tranches livrées, features
> lifecycle non démarrées, et mapping vers les points ouverts ci-dessous.

---

# Points ouverts

> Pour le backlog d’implémentation reporté (hors décisions à trancher), voir
> [DEFERRED-FEATURES.md](DEFERRED-FEATURES.md).

## Bloquants — à trancher avant le premier commit applicatif

> Les trois points initialement classés bloquants sont désormais tranchés — propagation du tenant
> (décision n° 13), résolution du tenant pour les appels non authentifiés (décision n° 14), et
> sécurité de la session BFF (décision n° 15). Voir la section *Décisions actées* ci-dessous. Cette
> section n'a plus d'élément à trancher à ce niveau de priorité. Les points 17 (entités JPA) et 27
> (versions, build, packaging frontend) ont également été tranchés — voir *Décisions actées*.

## Contradictions internes à lever

> Les points 16 et 18 ont été tranchés — voir *Décisions actées*. Cette section n'a plus d'élément
> ouvert.

---

## Autorisation et conformité

Points identifiés par [12-autorisation-conformite.md](analysis%20doc/12-autorisation-conformite.md),
qui prévoyait leur fusion dans ce registre.

### [x] 19. Granularité des rôles RBAC

**Clos V1 (2026-08-03 — feature `autorisation-conformite`) :** les cinq rôles
`TENANT_ADMIN`, `WORKFLOW_ADMIN`, `DOSSIER_MANAGER`, `DOSSIER_VIEWER`,
`API_SERVICE` sont la liste définitive V1. Mapping `realm_access.roles` →
`ROLE_*` déjà livré (`authentification`). Restriction par type = ACL
(décision n° 20). Peut être rouvert si les premiers clients réels exigent
d’autres rôles.

**Impacte :** [12-autorisation-conformite.md](analysis%20doc/12-autorisation-conformite.md).

---

### [ ] 21. Politique de rétention par `DossierType`

**À décider :** durée de conservation configurable par tenant et par type, action à expiration
(archivage puis purge), et articulation avec les statuts `CLOTURE` / `ARCHIVE` existants.

**Impacte :** [04-dossiers.md](analysis%20doc/04-dossiers.md),
[12-autorisation-conformite.md](analysis%20doc/12-autorisation-conformite.md).

---

### [ ] 22. Purge cohérente PostgreSQL / MinIO

**À décider :** le mécanisme garantissant qu'une suppression (droit à l'effacement, expiration de
rétention, suppression d'un tenant par `DROP SCHEMA`) purge conjointement la ligne en base et l'objet
de stockage — transaction applicative, job de réconciliation, ou suppression logique suivie d'un
balayage.

**Impacte :** [07-generation-documents.md](analysis%20doc/07-generation-documents.md),
[12-autorisation-conformite.md](analysis%20doc/12-autorisation-conformite.md).

---

### [~] 23. Classification des champs dynamiques sensibles

**Partiel (2026-08-03 — feature `autorisation-conformite`) :** flag
`FieldDefinition.sensitive` livré (métadonnée JSON, défaut false, API types).
**Reste ouvert :** masquage à l'affichage, exclusion des exports / logs /
webhooks sortants.

**Impacte :** [04-dossiers.md](analysis%20doc/04-dossiers.md),
[12-autorisation-conformite.md](analysis%20doc/12-autorisation-conformite.md).

---

## Compléments de conception

Ces points ne bloquent pas le démarrage, mais ils doivent être planifiés explicitement : aucun n'est
couvert aujourd'hui, même partiellement.

### [~] 24. Contrat d'API — surface incomplète et format d'erreur non normalisé

**Partiel (2026-08-13 — feature `pagination-dossiers`) :** liste/recherche/pagination
dossiers + envelope page + Supervision FE livrés. **Partiel (2026-08-15 — feature
`patch-dossier-fields`) :** `PATCH /api/dossiers/{id}/fields` (fusion, verrou optimiste,
gate statut, audit `sensitive`) livré. **Partiel (2026-08-16 — feature `elements-dossier`) :**
`POST /api/dossiers/{id}/elements` (JSON, `INFORMATION`/`NOTE`/`LINK`) et
`POST /api/dossiers/{id}/elements/attachment` (multipart, `ATTACHMENT`) livrés, avec la
même gate de statut `CLOTURE`/`ARCHIVE` que le point précédent (scopée au chemin humain).
**Reste ouvert :** OpenAPI formel, pagination des autres ressources, surface admin
exhaustive.

**Énoncé :** aucun endpoint de création, recherche, liste ou mise à jour de dossier n'est documenté,
pas plus que l'administration des `DossierType`, `ConnectorInstance` et templates. Les listes
impliquent pagination, tri et filtres, non abordés. Les exceptions métier existent déjà avec des
sémantiques HTTP précises, mais sans format pivot.

**À produire :** la surface d'API complète avec schémas de requête et de réponse ; un format d'erreur
normalisé (`ProblemDetail` / RFC 7807 est le candidat naturel) et le `@RestControllerAdvice` associé ;
une décision sur la génération OpenAPI et son statut de contrat vis-à-vis du frontend.

---

### [ ] 25. Modèle physique de données consolidé

**Énoncé :** une dizaine de tables métier, deux index uniques partiels critiques, la table
`outbox_event`, `dossier_type_acl` (décision n° 20), le stockage des DEK chiffrées, `platform.tenant_migration_status` et les tables
`ACT_*` de Flowable coexistent sans vue consolidée ni règle de répartition entre le schéma du tenant
et le schéma `platform`. En particulier, la table des tenants reflétée par le `TenantRegistry` —
deuxième barrière de sécurité de la décision n° 1 — n'est décrite nulle part, non plus que la
politique de rafraîchissement de son cache.

**À produire :** schéma consolidé (ERD ou DDL), répartition tenant / `platform`, et changelogs
Liquibase correspondants avec leurs contextes `tenant-init` / `tenant-migration`.

---

### [ ] 26. Concurrence sur le dossier et unicité de la référence

**À décider :** verrouillage optimiste (`@Version`) sur `Dossier`, écrit concurremment par les
`ServiceTask` et les utilisateurs ; contrainte d'unicité et mécanisme de séquence pour
`Dossier.reference` en déploiement multi-pods (le `nextSequence(...)` n'est pas spécifié) ; fuseau
horaire de référence pour le millésime de la numérotation annuelle. L'extrait actuel de
`SequentialReferenceGenerator` échoue en outre à l'exécution — `Instant` ne supporte pas le champ
`YEAR` requis par la conversion `%tY`.

**Impacte :** [04-dossiers.md](analysis%20doc/04-dossiers.md).

---

### [ ] 28. Stratégie de test

**À produire :** l'outillage et l'exécution en CI des tests déjà nommés par la documentation (étanchéité
du `search_path` avec Testcontainers, dimensionnement des verrous Flowable en charge, migration
Liquibase multi-schéma avec échec partiel), complétés par ArchUnit pour rendre exécutables la règle de
dépendance et la convention `port/in` / `usecase`, les tests du relais outbox (rejeu, idempotence,
crash entre publication et marquage), la résolution en cascade des templates, et la règle « un seul
workflow actif » en parallèle réel. Décider également des seuils de couverture.

---

### [ ] 29. Observabilité

**À produire :** logs structurés avec `tenantId`, `dossierId` et `processInstanceId` en MDC ; métriques
Micrometer sur l'âge du plus ancien événement non traité de l'outbox — indicateur de santé principal
du déclenchement —, les jobs Flowable en échec, la durée des rendus PDF, les webhooks en dead-letter
et le nombre d'`SseEmitter` actifs ; et une décision sur la traçabilité des accès en lecture, laissée
ouverte par le document `12`.

---

### [~] 30. Durcissement des points d'entrée exposés

**Livré (audit sécurité, cette session) :** rate limiting + plafond de taille en mémoire sur
`/api/webhooks/**` (`WebhookRequestRateLimiter`, `WebhookBodySizeLimitFilter`) et sur
`POST /api/documents/generate` (`DocumentGenerationRateLimiter`,
`DocumentGenerationBodySizeLimitFilter`) ; protection SSRF sur le rendu PDF
(`RestrictedResourceUserAgent`) et les webhooks sortants (`OutboundUrlGuard`, résolution DNS
incluse) ; journal d'audit sécurité structuré sur les 403 (`SecurityAuditLog`).

**Reste ouvert / reporté :**
- **Rate limiter distribué (Redis ou équivalent).** Les deux limiteurs ci-dessus sont en mémoire
  locale (`ConcurrentHashMap` par JVM) — corrects pour le modèle retenu (décision n° 13, une
  instance par tenant), mais chaque réplique aurait son propre compteur si un tenant était un jour
  scalé horizontalement, multipliant de facto la limite effective. **Décision : ne pas ajouter Redis
  maintenant** — aucun déploiement multi-réplique n'est prévu à court terme, et cela introduirait une
  dépendance d'infrastructure hors stack figée sans besoin actuel. À réactiver si/quand un tenant est
  effectivement scalé horizontalement.
- Taille, types et analyse antivirale des pièces jointes / uploads génériques.
- Protection SSRF sur le connecteur `http-rest` en rôle SERVICE (V1 = TRIGGER uniquement, non
  concerné aujourd'hui).
- Prévention des fuites de secrets déchiffrés via les logs et les API d'administration.

**Impacte :** [06-connecteurs.md](analysis%20doc/06-connecteurs.md),
[09-notifications.md](analysis%20doc/09-notifications.md),
[12-autorisation-conformite.md](analysis%20doc/12-autorisation-conformite.md).

---

## Processus

### [ ] 31. Découpage en lots d'implémentation

**Énoncé :** la documentation décrit une cible, pas un chemin — il n'existe ni lots, ni séquencement,
ni critères d'acceptation.

**À produire :** le découpage en lots et son séquencement, en s'appuyant sur l'ordre recommandé en
section 9 de [REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md). Le support de ce plan reste
à choisir (`docs/ai/planning` n'est pas utilisé à ce stade).

---

# Décisions actées

> Décisions architecturales et de sécurité identifiées lors de la revue de conception initiale.
> Toutes sont actées et répercutées dans la documentation technique correspondante (`analysis doc/`).

## Sécurité

### [x] 1. Validation du `tenantId` avant injection dans le `search_path`

**Problème :** `SchemaMultiTenantConnectionProvider` concaténait directement le `tenantId` dans la requête SQL `SET search_path TO {tenantId}, public` sans sanitisation.

**Décision retenue :** Double barrière — (1) validation par regex (`^[a-z][a-z0-9_]{2,62}$`) à la création du tenant, alignée sur les contraintes d'identifiant PostgreSQL, et (2) vérification à chaque résolution contre un registre des tenants actifs mis en cache, plus échappement de l'identifiant via `Connection.enquoteIdentifier()` au lieu d'une concaténation brute (défense en profondeur).

> **Mise à jour** — ce mécanisme de bascule dynamique du schéma par requête est remplacé par le
> modèle d'instance dédiée par tenant de la décision n° 13. La validation par regex reste pertinente
> au provisioning (création du schéma) ; le registre des tenants actifs consulté à chaque requête et
> le routage dynamique du `search_path` n'ont plus lieu d'être. Répercuté dans
> [02-multi-tenant.md](analysis%20doc/02-multi-tenant.md).

**Documenté dans :** [02-multi-tenant.md](analysis%20doc/02-multi-tenant.md).

---

### [x] 2. Comportement si le claim `tenant_id` est absent du token OIDC

**Problème :** Le `TenantResolutionFilter` ne rejetait pas la requête si le claim `tenant_id` était absent, et le `TenantIdentifierResolver` tombait sur le fallback `"public"`, rendant le schéma public accessible.

**Décision retenue :** Le `TenantResolutionFilter` rejette la requête (403) si le claim est absent ou vide. Le `TenantIdentifierResolver` ne fait plus de fallback silencieux : il lève une exception si aucun tenant n'est présent dans le contexte. Le schéma `public` reste réservé à un contexte système explicite (jobs internes, migration), jamais atteint via une requête API.

> **Mise à jour** — avec la décision n° 13, le `TenantResolutionFilter` ne sélectionne plus de
> schéma : il vérifie que le claim `tenant_id` du token correspond au tenant configuré de l'instance,
> et rejette (403) sinon ou si le claim est absent. `TenantIdentifierResolver` disparaît avec le SPI
> multi-tenant Hibernate, devenu inutile. Répercuté dans
> [02-multi-tenant.md](analysis%20doc/02-multi-tenant.md).

**Documenté dans :** [02-multi-tenant.md](analysis%20doc/02-multi-tenant.md).

---

### [x] 3. Authentification des webhooks entrants

**Problème :** Le connecteur `http-rest` en mode TRIGGER recevait des webhooks externes sans mécanisme d'authentification documenté.

**Décision retenue :** Signature HMAC-SHA256 par défaut (modèle GitHub/Stripe), secret stocké comme champ `PASSWORD` de la `ConnectorInstance` (donc chiffré au repos comme les autres secrets). Un champ `webhookAuthType` (SELECT : `HMAC_SHA256`, `SHARED_SECRET_TOKEN`, `IP_ALLOWLIST`) permet d'adapter le mécanisme aux capacités du système appelant. Vérification effectuée dans l'adaptateur web (`WebhookController`), avant d'atteindre `ConnectorTriggerRuntime` — c'est un détail HTTP/infrastructure, pas une responsabilité du domaine.

**Documenté dans :** [06-connecteurs.md](analysis%20doc/06-connecteurs.md).

---

### [x] 4. Gestion des clés de chiffrement des secrets de connecteurs

**Problème :** Les champs `PASSWORD` des `ConnectorInstance` sont chiffrés AES-GCM avec une « clé par tenant », mais la gestion de ces clés n'était pas documentée.

**Décision retenue :** Chiffrement en enveloppe — une KEK (Key Encryption Key) unique gérée par un service externe (HashiCorp Vault Transit, Azure Key Vault ou AWS KMS), et une DEK (Data Encryption Key) par tenant, chiffrée par la KEK et stockée en base sous forme chiffrée. La rotation de la KEK est déléguée au vault (versionnage natif) ; la rotation d'une DEK est un job explicite (`RotateTenantEncryptionKeyUseCase`), jamais automatique. Une clé maître unique via variable d'environnement reste un compromis toléré uniquement en mono-tenant/développement, documenté comme limitation temporaire.

**Documenté dans :** [06-connecteurs.md](analysis%20doc/06-connecteurs.md).

---

### [x] 32. Retrait synchronisé des exclusions autoconfig temporaires (Security/OAuth2/Flowable)

**Problème (revue de code, phase architecture-hexagonale) :** `src/main/resources/application.yml`
désactivait Security, OAuth2 client et Flowable via des entrées séparées dans
`spring.autoconfigure.exclude` (3 lignes à elles seules pour la sécurité), documentées comme
temporaires « à retirer feature par feature ». Rien n'empêchait un retrait partiel de ces
exclusions lors de l'implémentation de la feature `authentification` — l'application aurait pu
démarrer sans filtre de sécurité actif sans qu'aucun test ne le détecte.

**Décision retenue :** la feature `authentification` (PR #12) a retiré les 3 lignes Security/OAuth2
d'un seul mouvement, en même temps qu'elle câblait `SecurityConfig` (chaîne BFF `oauth2Login` +
CSRF + `TenantResolutionFilter`). Vérifié en revue de code post-merge : plus aucune exclusion
Security/OAuth2 dans `application.yml` ; `BffSecurityTest` couvre le 401/403 anonyme et mismatch de
tenant, donc un retrait partiel futur (ex. lors du retrait des exclusions JPA/Flowable restantes)
serait immédiatement détecté par la suite de tests existante. Pas de garde dédiée supplémentaire
jugée nécessaire au-delà de cette couverture.

**Documenté dans :** [docs/ai/requirements/2026-07-31-feature-authentification.md](docs/ai/requirements/2026-07-31-feature-authentification.md),
`src/main/java/com/flowforge/infrastructure/security/SecurityConfig.java`.

---

## Architecture

### [x] 5. `ConnectorInstance` — entité JPA ou objet domaine ?

**Problème :** L'entité JPA `ConnectorInstance` était passée directement aux ports `TriggerConnector` et `ServiceConnector`, couplant le domaine à l'infrastructure.

**Décision retenue :** Introduction de `ConnectorInstanceData`, objet domaine pur (config déjà déchiffrée) consommé par les ports `TriggerConnector`/`ServiceConnector`. L'entité JPA est renommée `ConnectorInstanceJpaEntity` et reste confinée à `infrastructure.persistence.connector` ; l'adaptateur assure la conversion (et le déchiffrement) — conforme au pattern déjà appliqué à `WorkflowInstance`/`WorkflowJpaEntity`.

> La généralisation de cette règle aux autres entités du domaine n'a pas été faite — voir le point
> ouvert 17.

**Documenté dans :** [06-connecteurs.md](analysis%20doc/06-connecteurs.md), [01-architecture-hexagonale.md](analysis%20doc/01-architecture-hexagonale.md).

---

### [x] 6. Extensibilité de `WorkflowEventPublisher`

**Problème :** L'interface déclarait une méthode par type d'événement — chaque nouvel état du workflow imposait une modification de l'interface et de tous ses adaptateurs.

**Décision retenue :** Signature générique `void publish(WorkflowEvent event)` avec `WorkflowEvent` en interface scellée (`sealed interface`, Java 21). Un nouvel événement = un nouveau type implémentant l'interface scellée, sans modification de `WorkflowEventPublisher`. Les adaptateurs qui routent par type utilisent un `switch` exhaustif sur les types permis, ce qui conserve la vérification à la compilation malgré la signature générique.

**Documenté dans :** [05-workflow-engine.md](analysis%20doc/05-workflow-engine.md).

---

### [x] 7. Durabilité des événements de déclenchement workflow

**Problème :** Le bus `ApplicationEventPublisher` est synchrone et en mémoire — un crash entre la réception d'un trigger et le démarrage de l'instance Flowable entraînait une perte sans rejeu possible.

**Décision retenue :** Pattern outbox transactionnel — l'événement de trigger est écrit dans une table `outbox_event` (schéma du tenant) dans la même transaction que la création / résolution du `Dossier`, puis un relais (scheduler de polling, avec chemin d'évolution vers Debezium/CDC si le volume l'exige) démarre Flowable et marque l'événement traité. Solution retenue car elle ne nécessite pas de nouvelle brique d'infrastructure immédiate tout en garantissant la durabilité.

> Articulation avec `ConnectorTriggerRuntime` tranchée par la décision n° 16 (option B) : le runtime
> crée/résout le dossier et écrit l'outbox ; le relais réserve le lien, démarre Flowable et confirme.

**Documenté dans :** [05-workflow-engine.md](analysis%20doc/05-workflow-engine.md),
[06-connecteurs.md](analysis%20doc/06-connecteurs.md) (décision n° 16).

---

### [x] 33. Pattern outbox pour les notifications ; `WorkflowEventPublisher` = no-op en production

**Problème :** `09-notifications.md` décrivait un `NotificationDispatcher` synchrone (appel direct
email/webhook/SSE depuis le `ServiceTask` Flowable, retry via Spring Retry `@Retryable`).
L'implémentation livrée a divergé sur les deux points sans jamais repasser par une décision :
`SendNotificationUseCaseImpl` ne fait aucune I/O — il écrit dans `notification_outbox`, et un
relais (`NotificationOutboxRelay`) dispatche réellement avec retry/backoff maison. Par ailleurs
`WorkflowEventPublisher` (décision n° 6) n'a **aucune** implémentation de production câblée sur les
notifications : le bean réel est `NoOpWorkflowEventPublisher`, volontairement inerte.

**Décision retenue :**
- **Notifications = outbox transactionnel**, symétrique à la décision n° 7 (déclenchement workflow) :
  écriture dans `notification_outbox` dans la même transaction que l'action métier, puis relais de
  polling avec `attempts`/backoff. Retenu pour la même raison que la décision n° 7 — durabilité sans
  brique d'infra supplémentaire — et pour uniformiser les deux chemins outbox du projet. La
  dépendance `spring-retry` (jamais utilisée, `@Retryable` absent du code) est retirée du `pom.xml` ;
  le retry reste porté par le relais, pas par une annotation Spring.
- **`WorkflowEventPublisher` reste no-op en V1.** Les notifications déclenchées par un changement
  d'état workflow passent par un `ServiceTask` explicite du process BPMN
  (`notificationDispatchTask`), pas par un listener générique sur `WorkflowEvent`. Le point
  d'extension de la décision n° 6 (signature générique `publish(WorkflowEvent)`) reste disponible si
  un futur besoin (audit trail générique, intégration tierce) justifie un abonné réel — non requis
  en V1.

**Documenté dans :** [09-notifications.md](analysis%20doc/09-notifications.md) (mis à jour pour
refléter l'outbox au lieu du dispatcher synchrone d'origine).

---

### [x] 8. Validation des champs dynamiques des dossiers

**Problème :** `Dossier.fields` n'était pas validé contre le schéma `FieldDefinition` du `DossierType` associé.

**Décision retenue :** Validation aux deux niveaux — couche REST pour le retour d'erreurs par champ (400) à des fins d'UX, et use case (`CreateDossierUseCase`/`CreateDossierBatchUseCase`) comme garde-fou faisant autorité, car les triggers/imports batch contournent la couche REST. Un service domaine pur `DossierFieldValidator` vérifie les champs requis, la conformité de type, et les valeurs `SELECT` hors énumération. Les champs inconnus sont rejetés par défaut, avec un indicateur `allowUnknownFields` sur `DossierType` pour les cas de compatibilité ascendante volontaire.

**Documenté dans :** [04-dossiers.md](analysis%20doc/04-dossiers.md).

---

### [x] 9. Duplication `usecase/` et `port/in/` dans le domaine dossier

**Problème :** La structure de packages de `04-dossiers.md` listait les mêmes noms dans `domain/dossier/usecase/` et dans `domain/dossier/port/in/`, incohérent avec `01-architecture-hexagonale.md`.

**Décision retenue :** Convention uniformisée : `port/in/` contient uniquement les interfaces, `usecase/` contient les implémentations suffixées `Impl` (ex. `CreateDossierUseCaseImpl` implémente `CreateDossierUseCase`). Appliqué à tous les modules du domaine (`workflow`, `document`, `connector`, `notification`, `dossier`) et vérifié en revue de code.

**Documenté dans :** [04-dossiers.md](analysis%20doc/04-dossiers.md).

---

## Opérationnel

### [x] 10. Stratégie de migration Liquibase multi-tenant

**Problème :** Le choix de Liquibase était acté mais la stratégie opérationnelle (onboarding, migration de masse, erreurs partielles) n'était pas documentée.

**Décision retenue :** Deux contextes Liquibase — `tenant-init` (changelog complet exécuté une fois à la création d'un schéma) et `tenant-migration` (changesets incrémentaux appliqués à tous les schémas existants lors d'un déploiement). Exécution via un Job Kubernetes dédié (hook pre-upgrade), qui boucle sur les tenants actifs avant le démarrage des nouveaux pods applicatifs — évite toute migration concurrente entre plusieurs instances. Une table de contrôle `platform.tenant_migration_status` isole les échecs par tenant (statut `MIGRATING_ERROR`, requêtes API bloquées en 503 pour ce tenant uniquement) sans affecter les autres tenants ; le Job est idempotent et rejouable sur les seuls tenants en échec.

> **Mise à jour** — avec la décision n° 13, chaque instance ne migre que son propre schéma à son
> propre démarrage ; le Job Kubernetes orchestrant plusieurs schémas avant le démarrage de pods
> génériques n'est plus nécessaire par défaut. Le suivi par tenant (`platform.tenant_migration_status`)
> et les contextes `tenant-init`/`tenant-migration` restent des idées valables, en version simplifiée
> (une instance n'écrit plus que sa propre ligne, sans coordination inter-schéma). Répercuté dans
> [02-multi-tenant.md](analysis%20doc/02-multi-tenant.md).

**Documenté dans :** [02-multi-tenant.md](analysis%20doc/02-multi-tenant.md).

---

### [x] 11. Scaling horizontal et scheduler Flowable Community

**Problème :** Risque perçu de double exécution des jobs Flowable en déploiement multi-instances Kubernetes.

**Décision retenue :** Le job executor de Flowable Community gère nativement le verrouillage optimiste au niveau ligne (`lockOwner`/`lockExpirationTime`) et supporte le clustering sans contrainte d'instance unique. La configuration des délais de verrou (`lockTimeInMillis`, `defaultAsyncJobAcquireWaitTime`) est validée par un test de charge au niveau cible. Si la contention devient un problème à l'échelle visée, séparation opérationnelle entre pods API (`asyncExecutorActivate: false`) et pods worker (`asyncExecutorActivate: true`, eux-mêmes réplicables pour la haute disponibilité), plutôt qu'une limitation à une seule instance globale.

> **Mise à jour** — avec la décision n° 13, un tenant occupe par défaut une seule instance : aucune
> contention entre pods d'un même tenant n'est possible tant qu'il n'est pas scalé. Le repli
> « pods API / pods worker » ne redevient pertinent que dans le pool de répliques d'un tenant scalé,
> jamais entre tenants. Répercuté dans
> [05-workflow-engine.md](analysis%20doc/05-workflow-engine.md).

**Documenté dans :** [05-workflow-engine.md](analysis%20doc/05-workflow-engine.md).

---

### [x] 12. Fichier `architecture-technique v7.md` — référence ou archive ?

**Problème :** Le fichier `architecture-technique v7.md` coexistait avec les 11 fichiers thématiques numérotés qui en sont le découpage, sans qu'il soit clair lequel faisait référence.

**Décision retenue :** `00-index.md` couvre intégralement et sans écart les 11 fichiers thématiques (déjà la table des matières utilisée par le `README.md` du dépôt). `architecture-technique v7.md` est supprimé du suivi ; les fichiers numérotés de `analysis doc/` sont l'unique source de vérité.

**Documenté dans :** suppression effective du fichier ; voir [00-index.md](analysis%20doc/00-index.md).

---

### [x] 13. Modèle de déploiement multi-tenant — instance dédiée par tenant

**Problème :** `TenantContext` (bean `@RequestScope`) et le resolveur strict introduit par la décision n° 2 rendent inopérants tous les composants qui s'exécutent hors requête HTTP — relais outbox, `JavaDelegate` Flowable exécutés en asynchrone, listeners d'événements, job de migration. Le sujet était renvoyé à `ScopedValue`, resté en *preview* jusqu'à Java 25.

**Décision retenue :** Abandon du modèle « pods génériques + résolution dynamique du tenant par requête ». Chaque tenant est désormais servi par sa **propre instance Spring Boot**, démarrée séparément (port ou serveur distinct), configurée avec son identifiant de tenant en dur au démarrage — une propriété de configuration lue une fois, jamais une donnée résolue par requête. Toutes les instances partagent la même base PostgreSQL multi-schéma (l'isolation par schéma, décision n° 1, ne change pas), mais chaque instance n'ouvre jamais qu'une connexion vers son propre schéma. Le routage vers la bonne instance (sous-domaine, port ou chemin) est délégué à un reverse-proxy, pas à l'application. Le mode « instance mutualisée servant plusieurs tenants dynamiquement » est abandonné — aucun chemin alternatif n'est conservé. Le scaling horizontal d'un tenant donné (plusieurs répliques pour un seul tenant) reste un chemin d'évolution documenté, activé au cas par cas, et ne réintroduit alors les problématiques de session/SSE partagées et de verrouillage Flowable que dans le pool de ce tenant.

Cette décision rend obsolète le mécanisme dynamique décrit par les décisions n° 1, 2, 10 et 11 (voir notes ajoutées sous chacune).

**Documenté dans :** [REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md), section 3, et répercuté dans [02-multi-tenant.md](analysis%20doc/02-multi-tenant.md), [05-workflow-engine.md](analysis%20doc/05-workflow-engine.md), [06-connecteurs.md](analysis%20doc/06-connecteurs.md), [09-notifications.md](analysis%20doc/09-notifications.md), [04-dossiers.md](analysis%20doc/04-dossiers.md), [08-templates-documents.md](analysis%20doc/08-templates-documents.md) et le `README.md`.

---

### [x] 14. Résolution du tenant pour les appels non authentifiés — routage par chemin + `client_credentials` par tenant

**Problème :** le `WebhookController` est appelé par des systèmes tiers sans token OIDC — donc rejeté en 403 par le `TenantResolutionFilter` d'origine — et commençait par une requête JPA qui avait besoin du tenant pour lire la ligne qui contient justement le tenant : résolution circulaire. Le même trou valait pour l'appel machine-to-machine `/api/connectors/{id}/execute` associé au rôle `API_SERVICE`, sans flux `client_credentials` décrit.

**Décision retenue :** Routage par **chemin** — chaque tenant est adressé sous un préfixe d'URL qui lui est propre (`https://example.com/{tenantId}/...`), identique au `server.servlet.context-path` de son instance dédiée (décision n° 13). La configuration du reverse-proxy est **statique** (Apache `mod_proxy`/`mod_rewrite`, un bloc par tenant ajouté au provisioning), sans annuaire consulté dynamiquement à chaque requête. Ce mécanisme sert uniformément le trafic humain (SPA, redirection OAuth2), les webhooks et l'API machine-to-machine — la requête est déjà routée vers l'instance du bon tenant avant d'atteindre l'application, ce qui lève la circularité initiale.

Pour l'API machine-to-machine, un flux OAuth2 **`client_credentials`** est ajouté : un client Keycloak dédié par tenant, provisionné avec le reste de l'onboarding, dont un protocol mapper attache un claim `tenant_id` fixe à tout token émis. Une chaîne de sécurité Spring distincte (`securityMatcher` sur les endpoints machine-to-machine), en *resource server* JWT, applique la même vérification d'identité que le flux humain — le `tenant_id` du token doit correspondre au tenant configuré de l'instance (`TenantProperties`), sinon rejet.

Effet de bord : le comportement pour un tenant en `MIGRATING_ERROR` est résolu par construction — une instance dont la migration échoue au démarrage (décision n° 13) ne devient jamais disponible, donc n'est jamais atteinte par le routage, sans logique 503 dédiée à écrire côté application.

**Documenté dans :** [REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md), section 3, et répercuté dans [02-multi-tenant.md](analysis%20doc/02-multi-tenant.md) (routage), [03-authentification.md](analysis%20doc/03-authentification.md) (`client_credentials`), [06-connecteurs.md](analysis%20doc/06-connecteurs.md) (`WebhookController`, `ConnectorExecutionController`) et [12-autorisation-conformite.md](analysis%20doc/12-autorisation-conformite.md).

---

### [x] 15. Sécurité et cycle de vie de la session BFF — CSRF, déconnexion, session par tenant

**Problème :** le pattern BFF avec cookie de session était retenu et justifié, mais aucune de ses conséquences directes n'était traitée. Spring Security active la protection CSRF par défaut — sans configuration, toutes les requêtes mutantes de la SPA échoueraient en 403. Le `logout` documenté se limitait à `session.invalidate()`, laissant la session du fournisseur OIDC ouverte et permettant une reconnexion silencieuse de l'utilisateur.

**Décision retenue :** Jeton CSRF porté par un cookie lisible en JavaScript (`CookieCsrfTokenRepository`, pattern double-submit), avec `SameSite=Lax` — hypothèse confirmée par la décision n° 27 (frontend embarqué dans `static/`, même origine) ; un filtre force la résolution du token à chaque requête, contournant le rendu paresseux introduit par Spring Security 6 (comportement toujours présent sous Spring Boot 4 / Security 7). Webhooks et API machine-to-machine ne sont pas exemptés via `ignoringRequestMatchers` mais isolés dans leurs propres chaînes de sécurité (`securityMatcher`, sans session ni CSRF), au même titre que la chaîne `client_credentials` de la décision n° 14 — trois chaînes ordonnées (`@Order`), partitionnées, jamais traversées simultanément par une même requête.

Déconnexion via `OidcClientInitiatedLogoutSuccessHandler`, qui appelle l'`end_session_endpoint` du fournisseur avec l'`id_token_hint` pour fermer la session Keycloak en plus de la session locale — la reconnexion silencieuse disparaît. Effet de bord identifié en corrigeant ce point : le routage par chemin de la décision n° 14 fait varier l'URL de callback OAuth2 et de post-logout par tenant ; une entrée à caractère joker (`https://example.com/*`) côté client Keycloak partagé couvre tous les tenants sans provisioning supplémentaire par tenant.

La session partagée entre répliques reste hors périmètre de la V1 (déjà tranché par la décision n° 13) — non requise par défaut, à réexaminer seulement si un tenant précis est scalé sur plusieurs répliques.

**Documenté dans :** [REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md), section 3, et répercuté dans [03-authentification.md](analysis%20doc/03-authentification.md) (protection CSRF, déconnexion, chaînes de sécurité), [11-frontend.md](analysis%20doc/11-frontend.md) (renvoi vers la configuration Axios).

---

### [x] 17. Entités JPA dans le domaine — règle appliquée à toutes les entités

**Problème :** la décision n° 5 avait séparé `ConnectorInstanceData` de son entité de persistance, mais `Dossier`, `DossierType`, `DossierElement`, `DossierRelation`, `DossierWorkflowLink`, `WorkflowTypeConfig` et `DocumentTemplate` restaient annotés `@Entity` tout en étant déclarés dans le domaine et exposés dans les signatures des ports. `GetDossierWorkflowHistoryUseCase` allait jusqu'à renvoyer une entité de persistance comme corps de réponse d'API.

**Décision retenue :** La règle est appliquée strictement, sans exception, aux sept classes concernées. Le modèle domaine garde son nom métier naturel (`Dossier`, `DossierType`…) ; l'entité de persistance reçoit le suffixe `JpaEntity` (`DossierJpaEntity`…) — le principe déjà illustré par `WorkflowInstance`/`WorkflowJpaEntity`. `ConnectorInstanceData`/`ConnectorInstanceJpaEntity` reste une exception de nommage historique de la décision n° 5 (le nom lui-même n'est pas retouché), mais sa forme de déclaration est alignée sur la nouvelle convention.

Le modèle domaine est un `record` Java. Un `record` avait d'abord été écarté au profit de `@Value @Builder` (Lombok) au motif qu'il génère des accesseurs sans préfixe, incompatibles avec le style déjà utilisé dans des extraits de code existants (`type.isAllowUnknownFields()`, `type.getFieldDefinitions()`). **Cet argument a été corrigé** : le projet est en phase d'analyse, sans aucune implémentation — il n'existe pas de code réel dont les appelants seraient figés, et les extraits illustratifs de `analysis doc/` s'éditent au même titre que le reste de cette documentation. Une fois ce coût artificiel écarté, `record` est le choix le mieux justifié (fonctionnalité native du JDK, immuabilité garantie par le langage, cohérent avec l'usage déjà fait de Java 21 moderne — `sealed interface` de la décision n° 6 — et déjà utilisé ailleurs dans ces mêmes documents, `WorkflowDiagramView`). `@Builder` (Lombok) reste utilisé au-dessus du `record` pour la construction des objets à de nombreux champs, sans renoncer aux accesseurs natifs ni à l'immuabilité ; une transition d'état (`Dossier.withStatus(...)`) s'implémente comme une méthode d'instance retournant une nouvelle instance via le constructeur canonique. `ConnectorInstanceData` a été alignée sur cette même convention pour ne pas laisser sa forme de déclaration seule diverger.

Corollaires actés dans le même mouvement : le risque concret de fuite d'un proxy Hibernate en réponse REST (`GetDossierWorkflowHistoryUseCase`) disparaît par construction ; le découplage plus général du contrat d'API reste du ressort du point 24. La règle de dépendance du domaine (et la convention `usecase`/`Impl` de la décision n° 9) est désormais vérifiée par un test ArchUnit en CI plutôt que confiée à la seule revue de code.

**Documenté dans :** [REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md), section 3, et répercuté dans [01-architecture-hexagonale.md](analysis%20doc/01-architecture-hexagonale.md) (règle généralisée, convention de nommage, section ArchUnit), [04-dossiers.md](analysis%20doc/04-dossiers.md), [05-workflow-engine.md](analysis%20doc/05-workflow-engine.md), [06-connecteurs.md](analysis%20doc/06-connecteurs.md) (`ConnectorInstanceData` alignée), [08-templates-documents.md](analysis%20doc/08-templates-documents.md) (entités converties) et [10-visualisation-workflow.md](analysis%20doc/10-visualisation-workflow.md) (note sur la réponse d'API).

---

### [x] 27. Versions figées, structure du build et packaging du frontend

**Problème :** les versions de stack n'étaient pas épinglées (`Flowable 7.x.x`, `Spring Boot 3.x`, Java 21) ; les seules versions figées (`flying-saucer-pdf-openpdf 9.9.4`, `jsoup 1.17.2`) dataient ; ni structure Maven ni packaging du frontend n'étaient définis — ce dernier point conditionnait CORS / `SameSite` / CSRF de la décision n° 15.

**Décision retenue — trois orientations :**

1. **Versions de stack.** Java **25** LTS · Spring Boot **4.1.x** · Flowable **8.0.x** · `flying-saucer-pdf` **10.4.x** (remplace `flying-saucer-pdf-openpdf`, abandonné) · jsoup **1.22.x** · Keycloak **26.7.x**. Points de vigilance actés : Jackson 3 est le défaut de Flowable 8 (compatibilité Jackson 2 encore disponible via `flowable.variable-json-mapper=jackson2` si besoin) ; l'ordre d'initialisation Liquibase / Flowable sous Boot 4 doit être vérifié dès le premier démarrage d'intégration.

2. **Build mono-module Maven.** Un seul module applicatif. La règle « domaine pur » (décision n° 17) est déjà garantie par ArchUnit en CI — un module `domain` séparé n'apporterait qu'une redondance au niveau du build, au prix d'une découpe figée trop tôt.

3. **Frontend embarqué.** Le build Vue 3 est servi depuis `src/main/resources/static/` de chaque instance Spring Boot dédiée (décision n° 13). Même origine API / SPA → confirme `SameSite=Lax` sans CORS de la décision n° 15. Chaque instance embarque le même artefact front (N copies identiques) — acceptable au regard de la simplicité opérationnelle.

> **Mise à jour** — deux angles morts de l'analyse initiale du frontend ont été comblés lors d'une
> revue technique ultérieure : (1) absence de typage statique, corrigée par l'adoption de
> **TypeScript** (`vue-tsc --noEmit` en CI, gabarit `vue-ts`) — d'autant plus pertinent que le point
> ouvert n° 24 (contrat OpenAPI vis-à-vis du frontend) ouvre la voie à un client généré typé ; (2)
> absence d'outillage qualité, corrigée par l'ajout de **Vitest** (unitaire/composants), **Playwright**
> (e2e) et **ESLint/Prettier** (`@vue/eslint-config-typescript`) comme gate CI, sur le même principe que
> `./mvnw verify` côté backend. Répercuté dans [11-frontend.md](analysis%20doc/11-frontend.md) et
> [README.md](README.md).

**Documenté dans :** [REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md), section 3, et répercuté dans [00-index.md](analysis%20doc/00-index.md), [05-workflow-engine.md](analysis%20doc/05-workflow-engine.md), [07-generation-documents.md](analysis%20doc/07-generation-documents.md), [03-authentification.md](analysis%20doc/03-authentification.md) (`SameSite` confirmé), [11-frontend.md](analysis%20doc/11-frontend.md) (packaging) et [README.md](README.md).

---

### [x] 16. Outbox transactionnel et `ConnectorTriggerRuntime` — option B

**Problème :** la décision n° 7 posait un outbox, tandis que `06`/`04` décrivaient un `ConnectorTriggerRuntime` entièrement synchrone (dossier → réserve → Flowable → confirme) sans outbox. Les deux séquences étaient incompatibles ; le pseudo-code du relais marquait en outre l'événement traité même après échec de publication.

**Décision retenue — option B (outbox après création/résolution du dossier) :**

1. **Phase producteur** (`ConnectorTriggerRuntime.onIncomingEvent`, synchrone, transaction unique) : authentification déjà faite par l'adaptateur web → `TriggerConnector.onEvent(...)` → pour chaque événement, créer le dossier **ou** résoudre `existingDossierReference` → écrire `outbox_event` (payload normalisé + `dossierId` + `connectorInstanceId`) → ACK webhook. **Jamais** de démarrage Flowable ici.

2. **Phase relais** (`@Scheduled`, transaction dédiée par événement) : lire les pending → réserver le `DossierWorkflowLink` (ou reprendre un lien `RESERVED` existant au rejeu) → démarrer Flowable → `confirmStarted` → `markProcessed` **uniquement en cas de succès, dans la même transaction**. Échec → incrément attempts, retry ; au-delà du plafond → dead-letter. Purge des événements `PROCESSED` au-delà d'une rétention configurable.

3. **Contenu de l'outbox :** `WorkflowTriggerEvent` déjà normalisé + `dossierId` (pas le payload brut) — la transformation n'est pas rejouable si elle échoue avant l'écriture (compromis accepté de l'option B).

4. **Idempotence au rejeu :** le relais ne recrée jamais le dossier ; si un lien `RESERVED`/`ACTIVE` existe déjà pour ce dossier + process, il le reprend. `WorkflowAlreadyRunningException` sur un autre workflow actif → dead-letter (le dossier existe déjà, l'ACK a été donné).

5. **`FOR UPDATE SKIP LOCKED` :** uniquement si le tenant est scalé sur plusieurs répliques (décision n° 13) ; pas une exigence du cas par défaut mono-instance.

**Écartées :** A (tout synchrone — rouvre n° 7), C (outbox du brut — ACK avant validation métier), D (hybride par source — double chemin).

**Documenté dans :** [REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md), section 3, et répercuté dans [05-workflow-engine.md](analysis%20doc/05-workflow-engine.md) (outbox + relais corrigés), [06-connecteurs.md](analysis%20doc/06-connecteurs.md) (`ConnectorTriggerRuntime` scindé) et [04-dossiers.md](analysis%20doc/04-dossiers.md) (création batch alignée).

---

### [x] 18. Résidus de refactoring et références orphelines

**Problème :** après les décisions n° 14–17, trois écarts ponctuels empêchaient d'utiliser les extraits comme spécification : `getWebhookAuthType()` inexistant sur `ConnectorInstanceData` ; `DocumentGenerationService` / `DocumentStorageService` absents de l'hexagone ; `updatedBy` affirmé par `12` mais absent de `Dossier` (et mauvaise attribution à `DossierElement`).

**Décision retenue — c1 + e1 + f1 :**

1. **c1 — `webhookAuthType` depuis `config`.** Lecture `WebhookAuthType.valueOf((String) instance.config().get("webhookAuthType"))` dans `WebhookController` — cohérent avec le `ConfigField` SELECT déjà documenté. Pas de champ de premier niveau sur le record.

2. **e1 — Aligner `08` sur l'hexagone.** `DocumentGenerationTask` injecte `GenerateDocumentUseCase` et `DocumentStoragePort` (définis dans `01` / `07`), plus de « Services » orphelins.

3. **f1 — `updatedBy` sur `Dossier`.** Champ ajouté au record, à l'entité JPA et aux mappers ; `withStatus` le propage. Pour `DossierElement`, `12` est corrigé : audit = `addedBy` / `addedAt` (pas `createdBy`/`updatedBy` — un élément est ajouté, pas mis à jour).

(Les résidus a/b/d/g étaient déjà corrigés par les décisions n° 14 et 16.)

**Documenté dans :** [REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md), section 3, et répercuté dans [06-connecteurs.md](analysis%20doc/06-connecteurs.md), [08-templates-documents.md](analysis%20doc/08-templates-documents.md), [04-dossiers.md](analysis%20doc/04-dossiers.md), [12-autorisation-conformite.md](analysis%20doc/12-autorisation-conformite.md).

---

### [x] 20. Contrôle d'accès au niveau type de dossier — `AccessContext` + `dossier_type_acl` + policy

**Problème :** le contrôle de rôle ne protège pas contre l'accès aux dossiers d'un type hors périmètre de l'utilisateur. L'esquisse `DossierAccessPort` ne distinguait pas appelant humain et système. Une première formulation de B2 posait une ACL **par instance** de dossier — amendée au profit d'une ACL **par `DossierType`**.

**Décision retenue — A2 + B2 (amendée) + C1 :**

1. **A2 — `AccessContext`.** Inchangé : `User` (ACL) · `System` (contournement) · `Service` (refus dossier). `DossierAwareTask` / `ConnectorTriggerRuntime` passent `AccessContext.system(...)`.

2. **B2 — table `dossier_type_acl`.** Source de vérité pour `DOSSIER_MANAGER` / `DOSSIER_VIEWER` : `(tenant_id, dossier_type_code, subject_user_id, permission)` avec `permission ∈ {VIEW, EDIT}` (`EDIT` ⇒ `VIEW`). Un grant sur le type s'applique à **toutes** les instances. Administration via `GrantDossierTypeAccessUseCase` / `RevokeDossierTypeAccessUseCase` (admins tenant). Création de dossier (humain) exige `EDIT` sur le type ; liste/recherche filtrée aux types `VIEW`. Pas de grant par instance, ni à la création, ni à l'assignation Flowable. `TENANT_ADMIN` / `WORKFLOW_ADMIN` contournent. Table à intégrer au point 25. Couvre en pratique la restriction « rôle × sous-ensemble de `DossierType` » du point 19.

3. **C1 — `DossierAccessPolicy`.** Service domaine ; `DossierTypeAclRepository` ; évaluation sur `dossierTypeCode` (directement ou via le dossier chargé). Refus → `AccessDeniedException` → 403 (point 24).

**Écartées / abandonnées :** ACL par instance de dossier (première B2) ; A1, A3, B1, B3, C2.

**Documenté dans :** [REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md), section 3, et répercuté dans [12-autorisation-conformite.md](analysis%20doc/12-autorisation-conformite.md), [04-dossiers.md](analysis%20doc/04-dossiers.md) et [06-connecteurs.md](analysis%20doc/06-connecteurs.md).

---

### [x] 34. ACL type de dossier sur le panneau dossier de la visualisation workflow

**Problème :** `GET /api/workflow-links/{linkId}/dossier` (feature `visualisation-workflow`,
décision DD-DOSSIER-LOAD) chargeait le dossier après authz « participant workflow ou
TENANT_ADMIN/WORKFLOW_ADMIN » **sans** repasser par `DossierAccessPolicy` (décision n° 20) —
motivé à l'origine par l'évitement d'une double vérification d'ACL (A10). Un simple participant ou
observer d'un workflow, sans entrée `dossier_type_acl` de niveau VIEW, pouvait donc lire
l'intégralité des champs métier du dossier — un modèle d'accès incohérent entre les deux portes
d'entrée pour lire le même dossier (`GetDossierUseCase` vs panneau viz), relevé par l'audit
sécurité de cette session.

**Décision retenue :** revenir sur DD-DOSSIER-LOAD/A10 — `WorkflowLinkVisualizationUseCaseImpl.getDossier`
appelle désormais `DossierAccessPolicy.assertCanView(dossier, actor)` en plus du contrôle
participant. `TENANT_ADMIN`/`WORKFLOW_ADMIN` continuent de contourner (comme partout ailleurs) ;
un participant/observer sans ACL VIEW garde l'accès au diagramme/historique/tâches (aucune donnée
métier du dossier) mais reçoit 403 sur le panneau dossier.

**Documenté dans :** [12-autorisation-conformite.md](analysis%20doc/12-autorisation-conformite.md),
[10-visualisation-workflow.md](analysis%20doc/10-visualisation-workflow.md).

