---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
---

# Implementation Guide — `scan-antivirus-clamav` (lot C)

> Phase 5 exécutée (T1-T10). Ce document capture ce qui a réellement été livré, y compris les
> écarts au plan trouvés en cours de route — pas une reformulation du plan a priori.

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
