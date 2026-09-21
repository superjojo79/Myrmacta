---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
---

# Implementation Guide — `scan-antivirus-clamav` (lot C)

> Phase 5 exécutée (T1-T10), Phase 7 (check implementation) faite, Phase 9 (revue finale
> pré-push) faite. Ce document capture ce qui a réellement été livré, y compris les écarts au
> plan trouvés en cours de route — pas une reformulation du plan a priori.

## Phase 9 — Revue holistique pré-push

**Trouvé et résolu — le seul écart matériel de cette passe :**

- **La branche de session avait pris 15 commits de retard sur `origin/main`** pendant les
  Phases 1-8 : le lot A4 (`headers-http-globaux`, PR #128) a été mergé sur `main` en parallèle de
  ce cycle. Fichiers touchés des deux côtés : `.env.example`, `CHANGELOG.md`,
  `DEFERRED-FEATURES.md`, `DEPLOIEMENT.md`, `FILE-ATTENTE.md`, `GETTING-STARTED.md`,
  `application.yml`. Vérifié avant d'agir (`git merge-tree`, pas supposé) : **zéro conflit
  réel** — les deux lots touchent des sections disjointes des mêmes fichiers. `git merge
  origin/main` exécuté, propre, sans résolution manuelle. Suite complète re-vérifiée
  **après** la fusion (pas seulement avant) : `./mvnw verify` → **2004 tests, 0 échec**
  (1982 + les nouveaux tests A4 désormais inclus), `ArchitectureRulesTest` 7/7 ; `npm run test`
  → 352/352 ; `npm run test:e2e` → **76/76** (72 + les 4 nouveaux `E2E-HDR-*` d'A4). Diff nul
  reconfirmé sur les 3 fichiers gelés du port, cette fois contre la base `main` réellement à
  jour. Sans cette étape, le premier `git push`/la première ouverture de PR contre `main` aurait
  révélé cet écart de 15 commits à un moment moins maîtrisé.

**Vérifications complémentaires (pas de nouveau finding) :**

- Aucune référence au nom du bean Spring renommé (`attachmentScanPort` → `noOpAttachmentScanPort`/
  `clamAvAttachmentScanPort`) ailleurs dans le code — pas de `@Qualifier` ni de couplage par nom de
  bean qui aurait pu casser silencieusement au renommage (`grep` ciblé, zéro résultat en dehors du
  nom de variable/champ local dans `DossierElementUploadController`, sans rapport).
  `@ConditionalOnProperty` garantit qu'un seul bean `AttachmentScanPort` est jamais actif à la fois
  (jamais zéro ni deux) pour les deux valeurs valides de `type` ; une valeur invalide fait échouer
  le démarrage Spring plutôt que de retomber silencieusement sur un comportement inattendu — même
  comportement que `app.storage.type` pour une valeur non reconnue, cohérent, pas un nouveau gap.
- `CHANGELOG.md` : **pas encore d'entrée pour ce lot**, volontairement — la convention du dépôt
  lie l'entrée à l'ouverture/mise à jour d'une PR, qui n'a pas encore été demandée. Reste l'action
  suivante explicite avant `dev-pr`.

**Checklist finale :**

| Critère | Statut |
|---|---|
| Conforme au design (12 décisions) | ✅ |
| Aucun gap de logique connu non documenté | ✅ (4 limites assumées, toutes documentées explicitement) |
| Sécurité traitée | ✅ (fail-open assumé + observable, borne de taille, pas de nouvelle surface AuthN/AuthZ) |
| Points d'intégration vérifiés | ✅ (diff nul reconfirmé post-merge, ArchUnit vert, suite complète verte après fusion de `main`) |
| Tests couvrent les changements | ✅ (100% adaptateur, IT réelle écrite, non-régression FE confirmée) |
| Docs à jour | ✅ (requirements/design/planning/implementation/testing + DEPLOIEMENT/GETTING-STARTED/.env.example) |

**Prêt pour push** (déjà fait au fil des phases) **et prêt pour une PR** dès que demandé —
reste uniquement l'entrée `CHANGELOG.md` à ajouter au moment de l'ouverture.

## Phase 7 — Revue fichier par fichier (findings)

**Trouvés et corrigés :**

- **`readResponse()` n'avait aucune borne de taille** — écart par rapport à une convention forte
  et systématique de ce dépôt : toute lecture réseau externe est bornée
  (`ConnectorProperties.Http.maxResponseBytes`, `Webhook.maxBodyBytes`,
  `DocumentStorage.maxContentSize`, `BatchImport.maxFileBytes`...). Un `clamd` qui ne renvoie
  jamais NUL/EOF pouvait faire grossir le buffer indéfiniment (borné dans les faits par
  `readTimeout` par octet, mais pas en taille). Corrigé : `MAX_RESPONSE_BYTES = 4096` — au-delà,
  `IOException` → fail-open, comme toute autre erreur protocolaire. Testé
  (`scan_responseExceedingMaxSize_isFailOpenNotUnboundedRead`), 100% couverture maintenue.
- **Healthcheck Compose `clamav` reposait sur la redirection `/dev/tcp` de bash**, une extension
  non-POSIX dont la présence dans l'image `clamav/clamav:1.4` n'était pas vérifiable (Docker
  absent du bac à sable). Remplacé par `test -f /run/clamav/clamd.pid` (POSIX `sh`, aucune
  dépendance externe), cohérent avec le `PidFile` déjà déclaré dans `docker/clamav/clamd.conf` —
  reste sujet à la même limite documentée (chemin de montage non vérifié contre un conteneur
  réel), mais n'ajoute plus une hypothèse supplémentaire (présence de bash) par-dessus.

**Trouvés, documentés, non corrigés (accepté, hors du périmètre borné du lot)** :

- **`socket.setSoTimeout(...)` borne chaque appel `read()` individuellement, pas la durée totale
  de lecture de la réponse.** `readResponse()` lit octet par octet ; un `clamd` qui ferait
  traîner sa réponse en la livrant très lentement (un octet juste avant l'expiration de chaque
  fenêtre `readTimeout`) pourrait dépasser le budget de latence nominal (5s) sans jamais
  déclencher de timeout. Risque jugé faible : `clamd` est un service interne de confiance
  (D-AV-CLAMAV-PROD-TOPO), pas une entrée utilisateur adversariale, et la réponse réelle tient en
  quelques dizaines d'octets. Documenté plutôt que corrigé (un délai global nécessiterait un
  thread de surveillance séparé — complexité disproportionnée par rapport au risque réel ici).
- **`new InetSocketAddress(host, port)` résout le DNS de façon synchrone, avant même que
  `connectTimeout` ne s'applique** (la résolution n'est pas couverte par le paramètre de timeout
  de `Socket.connect(...)`). Un DNS lent pour `properties.host()` pourrait bloquer au-delà du
  budget nominal. Risque jugé faible : `host` est une valeur de configuration opérateur (pas une
  entrée utilisateur), typiquement `localhost` ou un nom interne à résolution quasi instantanée —
  différent du risque SSRF déjà corrigé ailleurs (TODO §44, DNS rebinding sur un webhook
  *sortant* piloté par un tenant). Documenté, pas corrigé dans ce cycle.

**Vérifié conforme au design, rien à signaler :**

- `AttachmentScanProperties` / wiring `DossierConfig` : correspond exactement au design (Phase 3),
  même structure que `StorageProperties`/`StorageConfig`.
- `application.yml` : bloc `app.dossier.attachment.scan.*` conforme, mêmes défauts que documentés.
- Diff nul confirmé (à nouveau, après les corrections ci-dessus) sur `AttachmentScanPort.java`,
  `AttachmentScanResult.java`, `DossierElementUploadController.java`.
- `./mvnw verify -Dskip.frontend=true` : **1982 tests, 0 échec**, seuils Jacoco globaux tenus,
  `ClamAvAttachmentScanAdapter` toujours à 100% (instruction/ligne/branche) après les correctifs.

## Development Setup

**How do we get started?**

- Backend uniquement (aucun fichier frontend touché). `./mvnw verify -Dmaven.compiler.release=21
  -Dskip.frontend=true` pour la suite standard.
- Scan désactivé par défaut (`app.dossier.attachment.scan.type=noop`) — aucune configuration
  supplémentaire requise pour développer sur le reste du dépôt.
- Pour tester le scan réel : `docker compose --profile clamav up -d` (lent, chargement
  `freshclam`) puis `APP_DOSSIER_ATTACHMENT_SCAN_TYPE=clamav` dans `.env` (voir
  `GETTING-STARTED.md` §13).
- **Non vérifié dans ce cycle** : le démon Docker est absent du bac à sable de développement
  (`docker pull clamav/clamav:1.4` échoue avec « failed to connect to the docker API »). Ni
  `docker-compose.yml` (service `clamav`) ni `ClamAvAttachmentScanIT` (Testcontainers) n'ont pu
  être exécutés contre un conteneur réel — à faire au premier environnement avec Docker (CI ou
  poste de dev). Documenté comme limite assumée, même traitement que le Dockerfile du lot A1
  (`docs/ai/implementation/2026-09-08-feature-provisioning-tenant.md`).

## Code Structure

**How is the code organized?**

- `domain.dossier.port.out.AttachmentScanPort` / `AttachmentScanResult` — **inchangés** (diff
  nul vérifié à chaque commit du cycle).
- `infrastructure.config.AttachmentScanProperties` (nouveau) — `@ConfigurationProperties(prefix
  = "app.dossier.attachment.scan")`, sélecteur `type` (`noop`/`clamav`) + sous-record `clamav`
  (host/port/connectTimeout/readTimeout).
- `infrastructure.dossier.ClamAvAttachmentScanAdapter` (nouveau) — implémente
  `AttachmentScanPort`, protocole `INSTREAM` en socket Java standard.
- `infrastructure.config.DossierConfig` — le bean unique `attachmentScanPort()` est remplacé par
  deux beans `@ConditionalOnProperty` (`noOpAttachmentScanPort`/`clamAvAttachmentScanPort`), même
  idiome que `StorageConfig`/`app.storage.type`.
- `application.yml` — nouveau bloc `app.dossier.attachment.scan.*`, même structure que
  `app.storage.*`.
- `docker-compose.yml` / `docker/clamav/clamd.conf` — profil `clamav`, local/démo uniquement.
- `.env.example` / `DEPLOIEMENT.md` §8 / `GETTING-STARTED.md` §13 — doc ops.

## Implementation Notes

**Key technical details to remember:**

### Core Features

- **Sélecteur de config, pas un booléen** : `app.dossier.attachment.scan.type=noop|clamav`
  (D-AV-CLAMAV-CONFIG-SELECTOR), décidé en Phase 3 en repérant que `StorageConfig` avait déjà
  résolu exactement ce problème (choisir un adaptateur unique parmi plusieurs) — pas réinventé.
- **Protocole `INSTREAM`** : commande `zINSTREAM\0`, chunks préfixés 4 octets big-endian
  (`DataOutputStream.writeInt`), terminateur de taille zéro. Taille de chunk 8192 octets
  (`CHUNK_SIZE`), arbitraire mais sans impact fonctionnel — `clamd` réassemble le flux quelle que
  soit la découpe côté client.
- **Lecture de la réponse jusqu'à NUL *ou* fin de flux** — pas seulement NUL. Trouvé en visant
  100% de couverture branche (Jacoco), pas anticipé au design : si `clamd` (ou un proxy
  intermédiaire) ferme la connexion juste après avoir écrit la réponse sans envoyer l'octet NUL
  terminal, `readResponse()` doit quand même s'arrêter proprement sur `-1` plutôt que bloquer ou
  lever une exception inattendue.
- **Fail-open couvre aussi les réponses protocolaires reconnaissables mais non concluantes** :
  une réponse qui commence par `stream: ` sans se terminer par ` FOUND` (ex. un statut clamd
  futur/non standard) n'est **pas** traitée comme "presque infectée" — elle tombe dans la même
  case que toute réponse inattendue (fail-open), pas dans un état intermédiaire ambigu.

### Patterns & Best Practices

- `MeterRegistry` injecté au constructeur, `Counter`/`Timer` construit et enregistré une seule
  fois — copie exacte du pattern déjà établi par `FlyingSaucerPdfAdapter` (`flowforge.pdf.render`),
  pas un nouveau `MeterBinder` (ceux-ci re-interrogent un repository à chaque scrape, adaptés à
  un état courant — `Gauge` — pas à un compteur d'événements ponctuels).
  `flowforge.attachment.scan.fail_open` suit la convention de nommage du dépôt
  (`flowforge.<bc>.<sujet>.<événement>`).
- Tests unitaires de l'adaptateur : **faux `clamd` réel** (`ServerSocket` loopback dans le test),
  pas un mock du protocole — cohérent avec la préférence du dépôt pour des preuves réelles
  (`FlyingSaucerPdfAdapterTest` rend un vrai PDF plutôt que de mocker le moteur de rendu).
- `DossierConfigTest` : instanciation directe de `DossierConfig` + appel direct des méthodes
  `@Bean`, sans contexte Spring — même technique que `StorageConfigTest`.

## Integration Points

**How do pieces connect?**

- Aucun nouveau point d'intégration côté domaine — `DossierElementUploadController` continue
  d'appeler `AttachmentScanPort.scan(...)` exactement comme avant (diff nul).
- `clamd` : TCP brut, pas de bibliothèque cliente (contrainte D-AV-CLAMAV-PROTO). Port par défaut
  3310 (port `clamd` standard), configurable.
- Observabilité : `/actuator/prometheus` expose `flowforge_attachment_scan_fail_open_total`
  (convention de nommage Prometheus de Micrometer pour un `Counter` : points → underscores,
  suffixe `_total`) — même endpoint déjà câblé par `observabilite` (PR #59), aucun changement
  d'infra.

## Error Handling

**How do we handle failures?**

- Toute erreur technique (connexion refusée, timeout connexion/lecture, réponse protocole
  inattendue) → **fail-open** (D-AV-CLAMAV-FAILMODE) : `AttachmentScanResult.ofClean()`, log
  `WARN` avec le motif technique, compteur `flowforge.attachment.scan.fail_open` incrémenté.
  Jamais d'exception propagée à `DossierElementUploadController`.
- Un scan qui **aboutit** avec un verdict infecté reste bloquant — ce chemin ne passe jamais par
  `failOpen(...)`, testé explicitement (`scan_infectedResponse_returnsInfectedWithSignature`).
- Limite assumée et documentée (pas cachée) : le contexte du log de fail-open est borné par la
  signature du port (`scan(byte[] content, String declaredContentType)`, pas de `dossierId`).
  Le `tenantId` reste disponible de façon ambiante via `MdcTenantFilter` (filtre appliqué à
  toute requête, `@Order(Ordered.HIGHEST_PRECEDENCE)`) puisque l'appel a lieu sur le thread de la
  requête HTTP — aucun changement nécessaire côté adaptateur pour en bénéficier. Le `dossierId`,
  lui, n'est **pas** ambiant sur ce chemin (pas de `MdcScope` dossier ouvert par
  `DossierElementUploadController`) et n'a pas pu être ajouté sans toucher ce fichier gelé — écart
  entre l'ambition initiale du document requirements (« contexte tenant, dossier ») et ce qui est
  réellement livré : log = tenant (ambiant) + raison technique, pas dossier.

## Performance Considerations

**How do we keep it fast?**

- Timeouts bornés (5s connexion + 5s lecture, D-AV-CLAMAV-TIMEOUT) — au-delà, fail-open immédiat.
- Pas de mesure de latence réelle contre un vrai `clamd` dans ce cycle (Docker absent du bac à
  sable) — à faire lors de la première exécution réelle de `ClamAvAttachmentScanIT`.

## Security Notes

**What security measures are in place?**

- Le fail-open est un compromis sécurité assumé et documenté à plusieurs endroits
  (`DEPLOIEMENT.md` §8, `GETTING-STARTED.md` §13, ce document) : une panne `clamd` prolongée
  désactive le scan sans bloquer les uploads — atténué par le compteur Micrometer
  (D-AV-CLAMAV-HEALTH), pas éliminé. Pas un point aveugle : observable, à alerter dessus en prod.
- `clamd.conf` (dev/démo) fixe `StreamMaxLength` à la même valeur que la taille max upload
  applicative (25 Mo) — désynchronisation possible si l'un des deux est relevé sans l'autre,
  documentée explicitement (`docker/clamav/clamd.conf`, `DEPLOIEMENT.md` §8).
- Aucune nouvelle surface d'authentification/autorisation — le chemin d'upload garde exactement
  les mêmes gardes ACL qu'avant (`DossierElementUploadController` inchangé).
