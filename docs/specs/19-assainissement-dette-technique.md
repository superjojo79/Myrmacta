# Assainissement de la dette technique — reliquats `DEFERRED-FEATURES.md` §1

> **Statut : livré** (2026-09-01, TODO §49, cycle `dev-lifecycle` complet Phases 1-9). Les 3
> décisions listées en « Points ouverts » ont été tranchées via `AskUserQuestion` (options
> recommandées) — voir [`docs/ai/requirements/2026-09-01-feature-assainissement-dette-technique.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-01-feature-assainissement-dette-technique.md)
> et [`docs/ai/design/2026-09-01-feature-assainissement-dette-technique.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-09-01-feature-assainissement-dette-technique.md) pour le détail. Choisi
> comme prochaine feature le 2026-08-31 : le backlog V1 (analyses 01–18 + tous les
> points du registre des décisions, y compris §48 `connecteur-http-rest-service` désormais livré) est de nouveau
> entièrement épuisé, aucun autre skeleton pré-positionné n'existe dans le code (recherche
> exhaustive de `skeleton`/`non enregistré`/références `TODO §` dans `src/main/java`). Retenu parmi
> trois pistes proposées à l'utilisateur (les deux autres — outillage provisioning, nouveau
> connecteur hors V1 type `scheduler`/`smtp` — restent en réserve, aucune analyse écrite).

---

## 1. Constat — vérification item par item contre le code réel

`DEFERRED-FEATURES.md` §1 recense plusieurs lignes « Nice-to-have » jamais reprises depuis leur
identification initiale. Avant de proposer un périmètre, chaque ligne a été vérifiée contre le code
actuel plutôt que reprise telle quelle — deux se révèlent déjà closes, non répercutées dans le doc.

### 1.1 Déjà fait, jamais coché (dérive documentaire)

| Item (`DEFERRED-FEATURES.md` §1) | Constat réel |
|---|---|
| `CHECK SQL dossier_type_acl.permission ∈ {VIEW,EDIT}` (§1.4) | **Déjà livré.** Migration `014-dossier-type-acl-permission-check.yaml` (`ALTER TABLE dossier_type_acl ADD CONSTRAINT chk_dossier_type_acl_permission CHECK (permission IN ('VIEW','EDIT'))`) — ajoutée hors cycle formel le 2026-08-25 (PR #72, déjà notée dans `FILE-ATTENTE.md` § « Hors file produit », jamais répercutée dans `DEFERRED-FEATURES.md` §1.4). |
| Validation blank `tenantId` (§1.1, moitié de la ligne) | **Déjà livré.** `TenantProperties` (`infrastructure/config/TenantProperties.java`) porte `@Validated` + `@NotBlank String id` — le démarrage échoue déjà si `app.tenant.id` est vide ou blanc, pas seulement `null`. |

Ces deux lignes sont proposées à la suppression de `DEFERRED-FEATURES.md` §1 dans le cadre de cette
feature (documentation uniquement, aucun code à écrire pour elles).

### 1.2 Réels, encore ouverts

| Item | Constat réel |
|---|---|
| Validation blank `processKey`/`workflowKey` (§1.1, autre moitié de la ligne) | **Toujours ouvert.** `WorkflowTypeConfig` (compact constructor) et les records associés (`TriggerOutboxPayload`, `WorkflowStartedEvent`) n'appliquent que `Objects.requireNonNull(processKey, ...)`/`requireNonNull(workflowKey, ...)` — une chaîne vide ou blanche (`""`, `"   "`) passe la validation actuelle. Pas de garde-fou domaine équivalent à celui déjà en place pour `DossierType.code` (TODO §38, `CODE_PATTERN`) ou pour `DossierType.name` (unicité, TODO §47). |
| `dossierId` en MDC par item de purge (§1.8) | **Toujours ouvert, confirmé dans `PurgeOutboxJob`.** `MdcScope.tenant(tenantProperties.id())` enveloppe tout le batch (`process()`), mais la boucle par dossier vit dans `ProcessPurgeOutboxUseCaseImpl` — un use case domaine pur, sans dépendance MDC/SLF4J (respect volontaire de la règle ArchUnit `domain_has_no_framework_dependency`, cf. `01-architecture-hexagonale.md`). Un log d'erreur sur un item individuel du batch (ex. échec de purge d'un dossier précis) n'a donc aujourd'hui que le `tenantId` en contexte MDC, pas le `dossierId` concerné — diagnostic plus difficile en prod sur un run multi-dossiers. |

### 1.3 Hors périmètre code (vérification manuelle, pas une feature)

| Item | Pourquoi hors périmètre |
|---|---|
| IT stdout ECS `tenantId` (§1.8) | Déjà qualifié « smoke manuel » dans `DEFERRED-FEATURES.md` lui-même — la config `ecs` (format de log) est livrée (`observabilite`, PR #59) ; il ne reste qu'une vérification humaine ponctuelle en environnement réel, pas du code à écrire. |
| `Long version` / `Persistable` pour `isNew()` (§1.4) | **Probablement obsolète, à confirmer en design plutôt qu'assumé ici.** `@Version` est déjà présent sur `DossierJpaEntity` (ligne 54) — c'est précisément le mécanisme standard Spring Data JPA qui permet de distinguer une entité nouvelle d'une entité existante quand l'`@Id` est assigné côté application (UUID généré en Java, pas par séquence DB) : `SimpleJpaRepository.save()` route déjà correctement vers `persist`/`merge` sur la seule base de `@Version == null`. Implémenter `Persistable<UUID>` en plus serait redondant, sauf si un besoin précis (ex. `saveAndFlush` sur une entité pré-remplie côté test) le justifie — à vérifier explicitement en phase design plutôt que d'assumer le besoin. |

---

## 2. Périmètre proposé

### Inclus (à confirmer en requirements)

1. **Validation blank `processKey`/`workflowKey`** — garde-fou domaine (probablement compact
   constructor de `WorkflowTypeConfig`, sur le modèle de `CODE_PATTERN` pour `DossierType.code`),
   couvrant les chemins d'écriture existants (création/déploiement de `WorkflowTypeConfig`,
   `TriggerOutboxPayload`, `WorkflowStartedEvent`).
2. **`dossierId` en MDC sur le job de purge** — porter le contexte MDC par item plutôt que
   seulement par batch, sans faire fuir de dépendance MDC/SLF4J dans le domaine (contrainte
   ArchUnit à respecter, cf. §3).
3. **Nettoyage documentaire** — retirer de `DEFERRED-FEATURES.md` §1 les deux lignes déjà closes
   (§1.1).

### Explicitement hors périmètre (proposé)

| Sujet | Pourquoi |
|---|---|
| `Persistable`/`isNew()` sur `DossierJpaEntity` | Probablement redondant avec `@Version` déjà en place — à documenter comme « vérifié, non nécessaire » plutôt qu'implémenté sans besoin identifié (cf. §1.3). |
| IT stdout ECS `tenantId` | Vérification manuelle, pas du code (cf. §1.3). |
| Outillage provisioning tenant | Explicitement écarté par l'utilisateur au profit de ce sweep (piste alternative proposée, non retenue). |
| Nouveau connecteur hors V1 (`scheduler`/`smtp`/`s3-minio`) | Explicitement écarté — aucune analyse technique existante, scope V2 complet, pas un reliquat de dette. |

---

## 3. Contrainte architecturale à respecter — `dossierId` en MDC sans polluer le domaine

Le point le plus délicat de ce sweep n'est pas la validation blank (mécanique, même patron que
`DossierType.code`/`name`), mais le MDC par item de purge : `ProcessPurgeOutboxUseCaseImpl` est un
use case domaine pur, et la règle ArchUnit `domain_has_no_framework_dependency`
(`ArchitectureRulesTest.java`) interdit toute dépendance à `org.slf4j..` depuis `..domain..` — donc
`MDC.put("dossierId", ...)` ne peut pas être appelé directement dans la boucle du use case.

Options à trancher en phase design (aucune tranchée ici) :

- **(a)** Le use case expose un callback/écouteur (`Consumer<UUID>` ou événement domaine) invoqué
  avant chaque item, que `PurgeOutboxJob` (infrastructure) utilise pour ouvrir/fermer un
  `MdcScope.dossier(...)` autour de l'appel — le domaine reste pur, l'infra pilote le MDC.
- **(b)** Le use case retourne une structure enrichie par item (au lieu d'un simple compteur `int
  processed`) que le job peut logger lui-même avec le `dossierId`, sans MDC dans la boucle interne
  — déplace le logging par item entièrement côté infra, au prix de logs moins granulaires
  pendant l'exécution (seulement après coup, pas en cas de crash mi-batch).
- **(c)** Accepter le statu quo (MDC batch seulement) et fermer ce point comme « risque accepté,
  pas un gap bloquant » — cohérent avec le fait que ce point est déjà qualifié `nice-to-have` depuis
  `observabilite` (PR #59) sans avoir bloqué cette feature.

---

## 4. Points ouverts

À trancher en phase requirements/design (`dev-lifecycle`), pas dans ce document :

1. **D-BLANK-VALIDATION-SCOPE** — la garde blank doit-elle couvrir uniquement
   `WorkflowTypeConfig.workflowKey`, ou aussi `processKey` partout où il apparaît en entrée externe
   (ex. `ManualStartOrchestrator.start(..., processDefinitionKey, ...)`, jamais vérifié blank à ce
   jour) ? Périmètre exact à confirmer par une recherche exhaustive des points d'entrée en phase
   design, pas supposé ici.
2. **D-MDC-DOSSIER-MECHANISM** — option (a), (b) ou (c) ci-dessus (§3).
3. **D-PERSISTABLE-CONFIRM** — confirmer explicitement en design que `Persistable`/`isNew()` est
   bien redondant avec `@Version` (§1.3) avant de le retirer définitivement de
   `DEFERRED-FEATURES.md`, plutôt que de l'assumer depuis cette seule analyse.
4. **Nommage du lot** — proposition `assainissement-dette-technique`, à confirmer en séquençant
   avec le reste (aucun autre nom réservé dans `FILE-ATTENTE.md`/`DEFERRED-FEATURES.md` pour ce
   sujet à ce jour).
5. **Valeur produit limitée, à assumer explicitement** — contrairement aux features précédentes
   (parcours utilisateur ou sécurité), ce lot est de l'hygiène pure, sans impact visible pour un
   utilisateur final. À confirmer que c'est bien le choix voulu avant d'engager le cycle complet
   (déjà fait via `AskUserQuestion`, 2026-08-31 — rappelé ici pour traçabilité).
