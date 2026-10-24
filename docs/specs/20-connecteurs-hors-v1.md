# Nouveaux connecteurs hors V1 — `scheduler`, `smtp`, `document-storage`

> **Statut : livré** (2026-09-01, `connecteurs-hors-v1`, TODO §50,
> [PR #99](https://github.com/superjojo79/FlowForge/pull/99)). Les 3 connecteurs candidats
> décrits ci-dessous ont été implémentés tels quels après tranchage des points ouverts en phase
> `dev-lifecycle` (voir `docs/ai/{requirements,design}/2026-09-01-feature-connecteurs-hors-v1.md`
> pour les décisions actées). Document conservé tel quel comme trace de l'analyse initiale.
> Périmètre précisé par l'utilisateur : les 3 connecteurs candidats du catalogue
> [06-connecteurs.md](06-connecteurs.md) (`scheduler`, `smtp`, `s3-minio`), avec `s3-minio`
> **généralisé** en `document-storage` plutôt que couplé à S3 (cohérent avec [`DEFERRED-FEATURES.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/DEFERRED-FEATURES.md)
> §0 : « Ne pas coupler à MinIO »).

---

## 1. Constat — 3 candidats marqués « Non » en V1 livré

`06-connecteurs.md` catalogue 5 types de connecteurs cibles ; seuls `http-rest` et
`flowable-signal` sont enregistrés dans `ConnectorRegistry` aujourd'hui. Les 3 autres :

| Type catalogue | Cible | Pourquoi jamais livré (raison déjà notée) |
|---|---|---|
| `scheduler` | TRIGGER (cron) | Aucune notion de déclenchement planifié ailleurs dans le dépôt |
| `smtp` | SERVICE | « l'email V1 passe par les notifications Spring Mail, pas ce type catalogue » |
| `s3-minio` | SERVICE | « stockage via `DocumentStoragePort` S3/filesystem, pas un connecteur » |

Les deux dernières raisons ne sont pas des « non applicable » mais des **chevauchements avec un
sous-système existant** — chacun des deux connecteurs a donc un périmètre à délimiter précisément
pour ne pas dupliquer ce qui existe déjà, pas juste un nouveau connecteur à écrire depuis zéro.

---

## 2. `scheduler` (TRIGGER) — déclenchement planifié

### Mécanisme proposé — réutilisation du pipeline trigger existant

`ConnectorTriggerRuntimeImpl.onIncomingEvent(instance, rawPayload)` est déjà générique : il ne sait
rien de HTTP, il route vers `TriggerConnector.onEvent(instance, rawPayload)` puis résout/crée un
dossier et écrit en outbox. **Le connecteur `scheduler` lui-même n'a donc rien de spécial à faire
d'autre que produire un `WorkflowTriggerEvent`** à partir de sa propre config (`processKey`,
`dossierTypeCode`) — `rawPayload` n'est pas exploité.

La seule pièce réellement nouvelle est **qui décide quand appeler `onIncomingEvent`** : un nouveau
job `@Scheduled` (infra, sur le modèle de `EvaluateDossierRetentionJob`/`PurgeOutboxJob`) qui, à
chaque tick :

1. liste les instances `connectorType == "scheduler"` activées (`ConnectorInstanceRepository.
   listByTenantId(tenantProperties.id())` — un seul tenant par instance Spring Boot, décision
   n° 13, pas de coordination multi-pod à gérer) ;
2. pour chacune, évalue si son `cronExpression` (champ de config) est due depuis le dernier
   passage (`org.springframework.scheduling.support.CronExpression`, déjà sur le classpath
   Spring — aucune nouvelle dépendance) ;
3. si due, appelle `connectorTriggerRuntime.onIncomingEvent(instance, Map.of())`.

### Points ouverts

- **D-SCHEDULER-LASTFIRED** — comment mémoriser le dernier déclenchement par instance pour éviter
  un double-tir ou un tir manqué au redémarrage ? `ConnectorInstanceData` n'a aujourd'hui aucun
  champ de ce type. Options à trancher en design : (a) nouveau champ persisté (`lastTriggeredAt`)
  sur l'entité connecteur, (b) petite table dédiée `connector_schedule_state`, (c) tolérer un tir
  manqué au redémarrage sans mémorisation (fenêtre de poll courte, risque accepté).
- **D-SCHEDULER-DOSSIER** — `ConnectorTriggerRuntimeImpl.resolveOrCreateDossier` exige aujourd'hui
  soit `existingDossierReference`, soit `dossierTypeCode` (sinon `ConnectorValidationException`) :
  chaque tir crée donc un **nouveau** dossier par défaut. Est-ce le comportement voulu pour un
  déclenchement planifié (ex. tâche récurrente = un dossier par occurrence), ou faut-il un mode
  « pas de dossier », qui n'existe pas aujourd'hui dans le pipeline trigger (à vérifier si c'est un
  changement de `ConnectorTriggerRuntimeImpl` partagé avec les autres connecteurs, ou un cas
  spécifique à `scheduler`) ?
- **D-SCHEDULER-POLL** — fréquence du job de poll (ex. `fixedDelay` 1 min, cohérent avec la
  granularité minimale utile d'un cron) — trade-off précision du déclenchement vs charge.
- **Config schema** : `cronExpression` (`STRING`, requis, format cron Spring 6 champs) et
  `processKey`/`dossierTypeCode` déjà couverts par le mécanisme trigger générique — pas de nouveau
  champ hors cron a priori.

---

## 3. `smtp` (SERVICE) — envoi email ad hoc depuis un `serviceTask`

### Mécanisme proposé — réutilisation du port `EmailPort` existant

`EmailPort`/`SmtpEmailAdapter` (livrés avec `notifications`) encapsulent déjà `JavaMailSender` —
une seule configuration SMTP par instance (`spring.mail.*`, cohérent avec le modèle « une instance
= un tenant »). Le connecteur `smtp` n'a donc **aucune configuration SMTP propre à réinventer** : il
injecte `EmailPort` et l'appelle directement depuis `execute(instance, connectorInput)`, sans passer
par le pipeline `SendNotificationUseCase`/outbox/webhook.

Différence avec les notifications existantes (pas une duplication) : la notification système est
**structurée, templée, routée par canal** (email/webhook/SSE) à partir d'un `NotificationEvent`
dossier-centrique ; le connecteur `smtp` est un **envoi direct et ad hoc**, avec sujet/corps calculés
dans le process BPMN lui-même (ex. via `${...}` FEEL/expression sur des variables de process), sans
passer par le modèle de notification.

### Points ouverts

- **Config schema** : aucun champ requis a priori (réutilise l'`EmailPort` déjà configuré) — à
  confirmer en design, ou un champ optionnel `fromOverride` si un besoin réel apparaît.
- **`connectorInput`** : `to` (requis), `subject` (requis), `body` (requis) — mêmes contraintes
  que `EmailPort.send(to, subject, body)`.
- **D-SMTP-HTML** — `SmtpEmailAdapter` envoie aujourd'hui uniquement du texte brut
  (`helper.setText(body, false)`) ; garder cette limite (cohérent avec l'existant, pas de nouveau
  besoin identifié) ou étendre `EmailPort` pour un corps HTML ? Probablement hors périmètre V1 de
  cette feature, à confirmer.
- **D-SMTP-VALIDATION** — validation du format `to` (email bien formé) : déjà faite ailleurs
  (`NotificationEvent`/canal EMAIL) ou à réintroduire ici ?

---

## 4. `document-storage` (SERVICE, généralisé depuis `s3-minio`) — lecture/écriture fichier depuis un `serviceTask`

### Généralisation demandée par l'utilisateur

Le candidat catalogue s'appelait `s3-minio` et ciblait S3 spécifiquement. **Décision actée
(2026-09-01, demande utilisateur) : le connecteur s'appuie sur le port `DocumentStoragePort` déjà
existant** (agnostique S3/filesystem, cf. [`DEFERRED-FEATURES.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/DEFERRED-FEATURES.md) §0 « Ne pas coupler à MinIO »),
pas sur un client S3 direct — nommé `document-storage`, pas `s3-minio`.

`DocumentStoragePort` expose déjà `store(tenantId, contentType, content) → storageRef`,
`retrieve(tenantId, storageRef) → bytes`, `exists(tenantId, storageRef) → boolean`,
`delete(tenantId, storageRef)`. Le connecteur les expose depuis un `serviceTask` BPMN, pour du
contenu **non lié à un `Dossier`/`DossierElement`** (ce cas est déjà couvert par les pièces jointes
existantes) — ex. artefact intermédiaire généré/consommé en cours de process, indépendant du modèle
dossier.

### Points ouverts

- **`connectorInput`** : `operation` (`STORE`/`RETRIEVE`/`EXISTS`/`DELETE`, requis) + selon
  l'opération : `content` (base64, pour `STORE`) + `contentType`, ou `storageRef` (pour
  `RETRIEVE`/`EXISTS`/`DELETE`). `connectorResult` : `storageRef` (après `STORE`), `content`
  (base64, après `RETRIEVE`), `exists` (booléen), ack (après `DELETE`).
- **D-STORAGE-SIZE-LIMIT** — un contenu base64 dans `connectorInput`/variables de process Flowable
  n'a aujourd'hui aucune limite de taille dédiée (contrairement à un upload HTTP multipart classique
  qui passerait par une limite Spring). Faut-il une limite explicite (config connecteur ou
  `ConnectorProperties` globale) pour éviter qu'un process BPMN mal conçu charge un fichier énorme
  en variable de process (mémoire Flowable) ?
- **D-STORAGE-REF-LIFECYCLE** — un `storageRef` créé via ce connecteur reste **opaque et non
  rattaché** à un `Dossier`/`DossierElement` : pas de purge automatique (contrairement aux pièces
  jointes, purgées avec le dossier). À la charge du process BPMN de le supprimer explicitement
  (`DELETE`) s'il ne doit pas persister indéfiniment — à documenter clairement comme un piège
  potentiel, pas un bug.
- **D-STORAGE-REF-DOSSIER** (TODO §101, issue #205) — `connectorInput` est posable à la complétion d'une tâche. Depuis un
  process, `RETRIEVE` / `EXISTS` / `DELETE` n'acceptent donc qu'un objet que le connecteur a stocké pour le **dossier de
  l'exécution** (registre `connector_stored_object`, rempli au `STORE`). Une pièce d'un dossier ou un contenu stocké pour
  un autre dossier est refusé. Un appel direct (admin, M2M) garde la portée du tenant.
- **Config schema** : aucun champ requis a priori (réutilise le `DocumentStoragePort` déjà
  configuré au niveau instance) — à confirmer en design.

---

## 5. Périmètre proposé

### Inclus (à confirmer en requirements)

1. `SchedulerConnector` (TRIGGER) + job `@Scheduled` de poll — 1 nouveau connecteur + 1 nouveau
   composant infra.
2. `SmtpConnector` (SERVICE), réutilise `EmailPort`.
3. `DocumentStorageConnector` (SERVICE), réutilise `DocumentStoragePort`.
4. Mise à jour `06-connecteurs.md` (catalogue, colonne « V1 livré ») et `README.md` § Système de
   connecteurs.

### Explicitement hors périmètre (proposé)

| Sujet | Pourquoi |
|---|---|
| Coordination multi-pod du scheduler | Hors V1 — décision n° 13 (une instance = un tenant, pas de réplication) |
| Corps HTML pour `smtp` | Pas de besoin identifié au-delà de l'existant `EmailPort` (texte brut) |
| Rattachement `document-storage` → `Dossier`/`DossierElement` | Cas déjà couvert par les pièces
  jointes existantes ; ce connecteur cible explicitement du contenu hors dossier |
| Nouveau client S3 direct | Écarté par la généralisation demandée — `DocumentStoragePort` reste
  la seule abstraction de stockage du dépôt (§0 [`DEFERRED-FEATURES.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/DEFERRED-FEATURES.md)) |

---

## 6. Points ouverts (résumé, à trancher en phase requirements/design)

1. **D-SCHEDULER-LASTFIRED** — mécanisme de mémorisation du dernier déclenchement par instance.
2. **D-SCHEDULER-DOSSIER** — un dossier par tir est-il le comportement voulu, ou faut-il un mode
   sans dossier (changement potentiel du pipeline trigger partagé) ?
3. **D-SCHEDULER-POLL** — fréquence du job de poll.
4. **D-SMTP-HTML** — corps HTML ou texte brut seul.
5. **D-SMTP-VALIDATION** — validation du format `to`.
6. **D-STORAGE-SIZE-LIMIT** — limite de taille du contenu base64 en variable de process.
7. **D-STORAGE-REF-LIFECYCLE** — confirmer l'absence de rattachement/purge automatique comme un
   choix assumé, pas un oubli.
8. **Nommage du lot** — proposition `connecteurs-hors-v1` ou `connecteurs-scheduler-smtp-storage`,
   à confirmer sans urgence (n'affecte pas l'implémentation).
