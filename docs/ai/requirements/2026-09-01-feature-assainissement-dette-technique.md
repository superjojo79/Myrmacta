---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
---

# Requirements & Problem Understanding

## Problem Statement
**What problem are we solving?**

- `DEFERRED-FEATURES.md` §1 recense plusieurs lignes « Nice-to-have » jamais reprises depuis leur
  identification initiale. Vérification item par item contre le code réel (pas la doc) : deux lignes
  sont déjà closes mais non répercutées (dérive documentaire), deux restent réellement ouvertes.
- **Validation blank `processKey`/`workflowKey`** — les records domaine (`WorkflowTypeConfig`,
  `TriggerOutboxPayload`, `WorkflowStartedEvent`, `DossierType.workflowKey`) n'appliquent que
  `Objects.requireNonNull(...)`, jamais de garde blank, contrairement à `DossierType.code` qui a son
  propre `CODE_PATTERN` dans le compact constructor. **Vérifié en Phase 1** : tous les points d'entrée
  externes actuels (REST `WorkflowTypeConfigController`, `DossierWorkflowController`,
  `DossierTypeController` ; MCP `DeployWorkflowTool`) bloquent déjà une valeur blank via un helper
  `required()`/`requireString()` répété indépendamment à chaque frontière — donc aucune valeur blank
  n'est atteignable aujourd'hui par ces chemins. Le gap réel est l'absence de défense en profondeur
  dans le domaine lui-même : tout futur appelant qui construit directement un de ces records (job
  interne, test, futur connecteur) sans repasser par un DTO REST/MCP contourne la garde.
- **`dossierId` en MDC par item de purge** — `PurgeOutboxJob` (infra) enveloppe tout le batch dans
  `MdcScope.tenant(...)`, mais la boucle par dossier vit dans `ProcessPurgeOutboxUseCaseImpl`, un use
  case domaine pur (ArchUnit `domain_has_no_framework_dependency` interdit `org.slf4j..` depuis
  `..domain..`). Un log d'erreur sur un item individuel du batch n'a donc que `tenantId` en contexte
  MDC, pas le `dossierId` concerné — diagnostic plus difficile en prod sur un run multi-dossiers.
- Affecté : personne utilisatrice finale directement (aucun impact produit visible) ; l'équipe
  d'exploitation (diagnostic de logs de purge) et les futurs développeurs du domaine `workflow`
  (protection contre une régression silencieuse si un futur appelant contourne les DTOs actuels).

Référence : [analysis doc/19-assainissement-dette-technique.md](../../../analysis%20doc/19-assainissement-dette-technique.md).

## Goals & Objectives
**What do we want to achieve?**

Primaire :

- **D-BLANK-VALIDATION-SCOPE = défense en profondeur dans le domaine** (décidé 2026-09-01, option
  recommandée retenue) : ajouter une garde blank directement dans les compact constructors des
  records domaine concernés — `WorkflowTypeConfig.workflowKey`, `TriggerOutboxPayload.processKey`,
  `WorkflowStartedEvent.processKey`, `DossierType.workflowKey` — sur le modèle de
  `DossierType.code`/`CODE_PATTERN` (même mécanisme d'`IllegalArgumentException`, pas de nouveau
  pattern). Comportement des points d'entrée externes déjà validés inchangé (ils continuent de
  rejeter en amont via `required()`/`requireString()`) ; la garde domaine devient une seconde ligne
  de défense, pas la seule.
- **D-MDC-DOSSIER-MECHANISM = callback vers l'infra (option a)** (décidé 2026-09-01, option
  recommandée retenue) : `ProcessPurgeOutboxUseCaseImpl` expose un callback (`Consumer<UUID>` ou
  équivalent) invoqué avant le traitement de chaque item ; `PurgeOutboxJob` (infra) l'utilise pour
  ouvrir/fermer un `MdcScope.dossier(...)` autour de l'appel. Le domaine reste pur (aucune dépendance
  `org.slf4j`/MDC ajoutée), le contexte MDC est posé par item même en cas de crash mi-batch
  (contrairement à un retour enrichi post-batch qui ne loggerait qu'après coup).
- **D-PERSISTABLE-CONFIRM = confirmer redondant et retirer** (décidé 2026-09-01, option recommandée
  retenue) : vérifier explicitement en design (lecture du comportement `SimpleJpaRepository.save()`
  vis-à-vis de `@Version`, éventuellement un test ciblé) que `@Version` déjà présent sur
  `DossierJpaEntity` (ligne 54) suffit à distinguer persist/merge sans implémenter
  `Persistable<UUID>`, puis retirer la ligne de `DEFERRED-FEATURES.md` — aucun code de production à
  écrire pour ce point s'il est confirmé redondant.
- **Nettoyage documentaire** — retirer de `DEFERRED-FEATURES.md` §1 les deux lignes déjà closes :
  CHECK SQL `dossier_type_acl.permission` (migration `014-dossier-type-acl-permission-check.yaml`
  déjà livrée, PR #72) et validation blank `tenantId` (`TenantProperties.id` porte déjà `@NotBlank`).

Non-goals (explicitement hors périmètre, cf. analyse §2) :

- IT stdout ECS `tenantId` — déjà qualifié « smoke manuel » dans `DEFERRED-FEATURES.md`, vérification
  humaine ponctuelle en environnement réel, pas du code.
- Outillage provisioning tenant — piste alternative proposée puis explicitement écartée au profit de
  ce sweep.
- Nouveau connecteur hors V1 (`scheduler`/`smtp`/`s3-minio`) — aucune analyse technique existante,
  scope V2 complet, pas un reliquat de dette.
- Élargissement de la garde blank à des chaînes non listées explicitement ci-dessus (ex.
  `processDefinitionKey` en paramètre de méthode plutôt qu'en champ de record) — seuls les 4 records
  domaine cités sont dans le périmètre ; toute extension découverte en design est documentée comme
  telle, pas assumée ici.

## User Stories & Use Cases
**How will users interact with the solution?**

- En tant que développeur du domaine `workflow`, je veux qu'un `WorkflowTypeConfig`,
  `TriggerOutboxPayload` ou `WorkflowStartedEvent` construit avec un `processKey`/`workflowKey` blank
  lève une `IllegalArgumentException` immédiatement, même si l'appelant ne passe pas par un DTO REST
  ou MCP.
- En tant qu'opérateur en charge du diagnostic des purges, je veux que chaque ligne de log
  d'erreur/avertissement émise pendant le traitement d'un item de purge porte le `dossierId` concerné
  en MDC, pour retrouver rapidement les logs d'un dossier précis dans un run multi-dossiers.
- En tant que mainteneur de la documentation, je veux que `DEFERRED-FEATURES.md` §1 ne recense que
  des items réellement ouverts, pas des items déjà livrés ailleurs sans mise à jour.

Cas limites :

- `WorkflowTypeConfig`/`TriggerOutboxPayload`/`WorkflowStartedEvent` construits avec un
  `processKey`/`workflowKey` `null` → comportement inchangé (`NullPointerException` via
  `Objects.requireNonNull`, déjà couvert par les tests existants).
- `PurgeOutboxJob.process()` avec un batch de 0 item éligible → callback jamais invoqué, aucun
  changement de comportement de log par rapport à aujourd'hui (déjà couvert).
- Item de purge qui échoue (`RuntimeException` catchée dans `purgeDossier`) → le `dossierId` doit
  rester en MDC au moment où `markFailed`/le log d'erreur est émis, pas seulement pour les items qui
  réussissent.
- Item de purge dont le `dossierId` a déjà été retiré de `dossierRepository` (`dossier.isEmpty()`,
  cf. `PurgeOutboxRecord.dossierId()`) → le `dossierId` reste connu depuis `record.dossierId()` avant
  tout appel repository, donc disponible pour le callback même dans ce cas.

## Success Criteria
**How will we know when we're done?**

- Un test domaine prouve qu'un `processKey`/`workflowKey` blank (`""`, `"   "`) lève
  `IllegalArgumentException` pour chacun des 4 records ciblés (`WorkflowTypeConfig`,
  `TriggerOutboxPayload`, `WorkflowStartedEvent`, `DossierType.workflowKey`), sans régression sur les
  tests `requireNonNull` existants.
- Un test infra (`PurgeOutboxJob`/`ProcessPurgeOutboxUseCaseImpl`) prouve que le `dossierId` de
  l'item en cours de traitement est présent dans le MDC pendant l'appel du callback, et absent (ou
  restauré à sa valeur précédente) après — cohérent avec le contrat déjà testé de `MdcScope`
  (restauration sélective, pas de `MDC.clear()` global).
- `ArchitectureRulesTest.domain_has_no_framework_dependency` reste vert — aucune dépendance
  `org.slf4j`/MDC introduite dans `ProcessPurgeOutboxUseCaseImpl` ou tout autre type `..domain..`.
- `DEFERRED-FEATURES.md` §1 ne contient plus les 2 lignes déjà closes ; les 2 lignes réellement
  traitées par cette feature sont retirées ou marquées livrées avec référence à cette feature.
- `D-PERSISTABLE-CONFIRM` tranché explicitement dans le doc design (confirmé redondant ou non), avec
  la preuve utilisée (lecture de code documentée, ou test ciblé) — pas une simple affirmation.
- `./mvnw verify` vert. Feature confirmée **backend seul** (aucun fichier `frontend/**` concerné —
  tous les points traités sont des records/use cases/jobs Java) : `npm run test`/`test:e2e` passent en
  régression complète sans modification.

## Constraints & Assumptions
**What limitations do we need to work within?**

- Règle ArchUnit `domain_has_no_framework_dependency` non négociable — toute solution pour le MDC par
  item doit garder `ProcessPurgeOutboxUseCaseImpl` (et l'interface `ProcessPurgeOutboxUseCase`)
  exempts de toute dépendance `org.slf4j`/MDC ; c'est la contrainte qui a écarté d'office un appel
  direct `MDC.put(...)` dans la boucle du use case.
- Convention déjà établie pour la validation blank en record domaine : `IllegalArgumentException`
  avec message explicite nommant le champ (patron `DossierType.code`/`CODE_PATTERN`,
  `observerUserIds` dans `WorkflowTypeConfig`) — pas de nouvelle convention à inventer, réutilisation
  à l'identique.
- Changer la signature de `ProcessPurgeOutboxUseCase.processBatch(Instant, int)` (ajout d'un
  paramètre callback) est un changement d'interface de port — un seul point d'implémentation
  (`ProcessPurgeOutboxUseCaseImpl`) et un seul appelant (`PurgeOutboxJob`) à ce jour d'après
  `grep`, donc impact contenu, mais à confirmer exhaustivement en design (tests mock compris).
- Portée volontairement limitée aux 4 records identifiés en Phase 1 pour la validation blank —
  n'inclut pas une recherche exhaustive de tout champ `String` non validé du domaine (hors périmètre
  de ce sweep ciblé, cf. Non-goals).
- Faible valeur produit assumée explicitement : ce lot est de l'hygiène pure (defense-in-depth +
  diagnostic opérationnel + nettoyage doc), sans fonctionnalité visible côté utilisateur final —
  confirmé par l'utilisateur via `AskUserQuestion` le 2026-08-31 lors du choix de cette feature.

## Questions & Open Items
**What do we still need to clarify?**

Décisions actées 2026-09-01 (`AskUserQuestion`, TODO §49) :

- **D-BLANK-VALIDATION-SCOPE = défense en profondeur dans le domaine** (option recommandée retenue),
  périmètre limité aux 4 records cités (voir Goals).
- **D-MDC-DOSSIER-MECHANISM = callback vers l'infra, option (a)** (option recommandée retenue).
- **D-PERSISTABLE-CONFIRM = confirmer redondant et retirer** (option recommandée retenue), sous
  réserve de la vérification explicite en design.

Restant à trancher en phase design (pas bloquant pour clore les requirements) :

- **Forme exacte du callback MDC** — `Consumer<UUID>` simple passé en paramètre de
  `processBatch(Instant, int, Consumer<UUID>)`, ou une abstraction dédiée (ex. interface
  `PurgeItemListener`) ? Aucun précédent de ce patron ailleurs dans le dépôt (`grep Consumer<UUID>` :
  aucun résultat) — à choisir en design plutôt qu'assumé ici.
- **Portée exacte du callback** — invoqué uniquement autour de `purgeDossier(record)`, ou englobant
  aussi `markCompleted`/`markFailed` (qui touchent le repository, pas de log applicatif direct
  aujourd'hui) ? À confirmer en design selon ce qui doit effectivement apparaître en MDC au moment de
  chaque `log.warn`/`log.info` potentiel côté infra.
- **Preuve de redondance `Persistable`** — lecture de code documentée (comportement
  `SimpleJpaRepository.save()` via `@Version`) suffisante, ou un test JPA ciblé est-il jugé
  nécessaire pour fermer `D-PERSISTABLE-CONFIRM` avec un niveau de confiance suffisant avant de
  retirer la ligne de `DEFERRED-FEATURES.md` ? À trancher en design.
