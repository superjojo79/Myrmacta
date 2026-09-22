---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
---

# Project Planning & Task Breakdown — `scan-antivirus-clamav` (lot C)

> Découpage TDD des 12 décisions actées (Phases 1-3, `docs/ai/requirements/` et
> `docs/ai/design/2026-09-20-feature-scan-antivirus-clamav.md`). Chaque tâche de code suit le
> cycle rouge → vert → refactor (`tdd`/`verify`) ; `./mvnw verify` (scope ciblé puis complet)
> avant toute claim de tâche terminée.
>
> **Vérification faite pendant cette Phase 4, à noter** : le dépôt n'a **pas** de
> `maven-failsafe-plugin`, et `maven-surefire-plugin` (3.5.6, confirmé via
> `help:evaluate`/`help:describe -Ddetail=true`) n'a pas d'`<includes>` custom — ses patterns par
> défaut (`**/Test*.java`, `**/*Test.java`, `**/*Tests.java`, `**/*TestCase.java`) **n'incluent
> pas** `*IT.java`. C'est le mécanisme exact, jusqu'ici implicite, par lequel les IT
> Testcontainers PostgreSQL existantes (`TenantSchemaIsolationIT`, etc.) sont déjà isolées de
> `./mvnw verify` — confirme et explique D-AV-CLAMAV-TEST-SCOPE : nommer la nouvelle classe
> `ClamAvAttachmentScanIT` suffit à l'isoler, **aucun profil Maven ni tag JUnit à ajouter**. Un
> `-Dtest=ClamAvAttachmentScanIT` explicite reste nécessaire pour l'exécuter.

## Milestones

- [ ] **M1 — Socle configuration** : sélecteur `app.dossier.attachment.scan.type` en place,
      comportement par défaut (`NoOp`) strictement inchangé. Zéro régression.
- [ ] **M2 — Adaptateur ClamAV** : `ClamAvAttachmentScanAdapter` complet (protocole, fail-open,
      métrique), couverture unitaire 100 %.
- [ ] **M3 — Ops** : `clamd` démarrable en local (Compose), documentation à jour.
- [ ] **M4 — Preuve réelle** : IT Testcontainers isolée, verte.
- [ ] **M5 — Clôture** : docs de suivi (`CHANGELOG.md`, `TODO.md`, `FILE-ATTENTE.md`,
      `DEFERRED-FEATURES.md`) synchronisées, `./mvnw verify` complet vert.

## Task Breakdown

### M1 — Socle configuration (sans dépendance à `clamd`)

- [ ] **T1 — `AttachmentScanProperties`** (`infrastructure.config`)
  - TDD : `AttachmentScanPropertiesTest` d'abord — construction sans args → `type()` vaut
    `"noop"`, `clamav().host()/port()/connectTimeout()/readTimeout()` ont les défauts
    (`Duration.ofSeconds(5)`), sur le modèle de `StorageProperties`/`ConnectorProperties.Http`.
  - Dépendance : aucune.
  - Preuve : test unitaire vert, pas de code de production hors le record lui-même.

- [ ] **T2 — Sélecteur `DossierConfig` (bean `NoOp`)**
  - TDD : `DossierConfigTest` (nouveau fichier, instanciation directe `new DossierConfig()` +
    appel direct des méthodes `@Bean` concernées, même technique que `StorageConfigTest` — pas
    de contexte Spring complet) — `noOpAttachmentScanPort(properties)` avec `type=noop` (ou
    absent) retourne une instance de `NoOpAttachmentScanAdapter`.
  - Remplace le bean unique actuel `attachmentScanPort()` par
    `@ConditionalOnProperty(name = "app.dossier.attachment.scan.type", havingValue = "noop",
    matchIfMissing = true)`.
  - Dépendance : T1.
  - Preuve : `DossierConfigTest` vert + `DossierElementUploadControllerTest` et
    `NoOpAttachmentScanAdapterTest` existants **inchangés et verts** (non-régression du
    comportement par défaut, cf. testing.md § non-régression).

### M2 — Adaptateur ClamAV

- [ ] **T3 — `ClamAvAttachmentScanAdapter` : chemins nominaux**
  - TDD : test d'abord, fausse socket `clamd` locale (`ServerSocket` loopback dans le test,
    cohérent avec la préférence du dépôt pour des preuves réelles plutôt que des mocks de
    protocole) — fichier propre → `stream: OK` → `AttachmentScanResult.ofClean()` ; fichier
    « infecté » (réponse simulée) → `... FOUND` → `ofInfected(reason)` avec la signature dans
    `reason`.
  - Implémente le protocole `INSTREAM` (D-AV-CLAMAV-PROTO) : `zINSTREAM\0`, chunks préfixés
    taille 4 octets big-endian, chunk final de taille zéro.
  - Dépendance : T1 (consomme `AttachmentScanProperties.clamav()`).
  - Preuve : testing.md § Unit Tests, scénarios 1-2.

- [ ] **T4 — `ClamAvAttachmentScanAdapter` : fail-open + métrique**
  - TDD : connexion refusée / hôte injoignable / timeout connexion (>5s) / timeout lecture (>5s)
    / réponse protocole malformée (y compris taille dépassée) → dans tous les cas,
    `AttachmentScanResult` équivalent « clean » (pas d'exception propagée), log avec motif
    technique, **et** `Counter` `flowforge.attachment.scan.fail_open` incrémenté (vérifié via
    `SimpleMeterRegistry` de test).
  - Dépendance : T3 (même classe, chemins d'erreur).
  - Preuve : testing.md § Unit Tests, scénarios 3-7. **Vérifier explicitement** qu'un scan qui
    *aboutit* avec un verdict infecté ne passe jamais par ce chemin fail-open (assertion
    croisée avec T3).

- [ ] **T5 — Sélecteur `DossierConfig` (bean `ClamAV`)**
  - TDD : `DossierConfigTest` complété — `type=clamav` → bean actif devient
    `ClamAvAttachmentScanAdapter`, construit avec les propriétés `clamav` et le `MeterRegistry`.
  - Dépendance : T1, T4.
  - Preuve : testing.md § Configuration/wiring, scénario `type=clamav`.

### M3 — Ops

- [ ] **T6 — Service `docker-compose.yml` `clamav`**
  - Nouveau profil `clamav`, image `clamav/clamav:1.4` épinglée (D-AV-CLAMAV-IMAGE), volume
    signatures, `FRESHCLAM_CHECKS` (D-AV-CLAMAV-FRESHCLAM), `StreamMaxLength` aligné sur
    `APP_DOSSIER_ATTACHMENT_MAX_SIZE_MB` = 25 Mo (D-AV-CLAMAV-SIZE-ALIGN, commentaire explicite
    dans le fichier comme pour les autres alignements de config du repo), healthcheck attendant
    le chargement des définitions.
  - Dépendance : aucune techniquement, mais placé après M2 pour tester contre un vrai `clamd`
    dès qu'il existe.
  - Preuve : `docker compose --profile clamav up -d` démarre `clamd`, joignable (validation
    manuelle, testing.md § Manual Testing) ; `docker compose up -d` **sans** le profil reste
    inchangé (non-régression ops).

- [ ] **T7 — Documentation ops**
  - `.env.example` : nouvelles variables (`APP_DOSSIER_ATTACHMENT_SCAN_TYPE`,
    `CLAMAV_HOST`/`CLAMAV_PORT` ou noms équivalents résolus lors de l'implémentation de T1/T6).
  - `DEPLOIEMENT.md` : §4/§5 pattern (service externe déjà provisionné en prod,
    D-AV-CLAMAV-PROD-TOPO), alignement `StreamMaxLength`, kill switch.
  - `GETTING-STARTED.md` : démarrage local via le profil Compose.
  - Dépendance : T6 (les noms de variables doivent correspondre exactement).
  - Preuve : revue croisée manuelle (pas de test automatisé pour de la doc), conforme à
    `flowforge-conventions` § Workflow de feature point 6.

### M4 — Preuve réelle (isolée du run standard)

- [ ] **T8 — `ClamAvAttachmentScanIT`**
  - Testcontainers, image `clamav/clamav:1.4` (même tag que T6). Nommage `*IT.java` seul
    suffisant pour l'isolation (cf. note en tête de document, D-AV-CLAMAV-TEST-SCOPE) — pas de
    tag/profil Maven supplémentaire à créer.
  - Scénarios (testing.md § Integration Tests) : upload EICAR réel → rejet ; upload fichier
    propre → accepté ; `clamd` arrêté pendant l'appel → accepté (fail-open) + log + compteur
    vérifiables.
  - Dépendance : T5 (bean `clamav` actif), T6 (image de référence).
  - Preuve : `./mvnw test -Dtest=ClamAvAttachmentScanIT` vert en exécution isolée ; mesurer le
    temps de démarrage réel (`clamd` + chargement signatures) pour statuer sur l'opportunité
    d'un job CI séparé (question laissée ouverte en testing.md, à trancher avec la mesure
    réelle plutôt qu'a priori).

- [ ] **T9 — Non-régression complète**
  - `./mvnw verify -Dmaven.compiler.release=21` (suite standard, sans l'IT ClamAV) : 0 échec,
    Jacoco au seuil actuel (LINE ≥ 85%, BRANCH ≥ 75%, seuils `pom.xml` inchangés),
    `ClamAvAttachmentScanAdapter` à 100% de couverture unitaire.
  - `DossierElementUploadControllerTest`, `NoOpAttachmentScanAdapterTest` : verts sans
    modification de leurs assertions existantes.
  - Frontend : aucune modification attendue, vérification qu'aucun test Playwright existant lié
    à l'upload de pièce jointe ne régresse (exécution normale de la suite FE existante — pas de
    nouveau test Vitest/Playwright requis, cf. testing.md § Coverage Goals).
  - Dépendance : T1-T8.

### M5 — Clôture

- [ ] **T10 — Documentation de suivi**
  - `CHANGELOG.md` (section Sécurité, `[Non publié]`) : entrée synthétique 2-5 lignes.
  - `TODO.md` / `FILE-ATTENTE.md` / `DEFERRED-FEATURES.md` : lot **C** passé de « en cours » à
    « livré », référence PR.
  - `REVUE-ALIGNEMENT-SECURITE.md` § S-6 : mise à jour (reliquat comblé, fail-open documenté
    comme compromis assumé — cf. design.md § NFR Sécurité).
  - Dépendance : T9 (rien à clore tant que la suite n'est pas verte).

## Dependencies

- **Séquencement obligatoire :** T1 → (T2 ‖ T3) → T4 → T5 → T8 ; T6 peut démarrer en parallèle
  de M2 mais T8 a besoin de T5 **et** T6 pour tourner contre un vrai `clamd`. T7 dépend des noms
  de variables figés en T6. T9/T10 ferment le cycle.
- **Aucune dépendance externe nouvelle** (bibliothèque tierce) — protocole `INSTREAM` en socket
  Java standard (D-AV-CLAMAV-PROTO), conforme à la contrainte requirements.
- **Contrainte transversale non négociable, vérifiée à chaque tâche :** `git diff` nul sur
  `AttachmentScanPort.java`, `AttachmentScanResult.java`, `DossierElementUploadController.java`.
- **Ressource externe :** l'image `clamav/clamav:1.4` doit être atteignable pour T6/T8 (registre
  Docker) — pas de dépendance réseau pour T1-T5 (tests unitaires purs + socket loopback).

## Timeline & Estimates

- Pas d'estimation chiffrée en jours/points (le dépôt ne pratique pas ce suivi) — séquencement
  par tâches avec preuve de sortie (test vert) comme unité de progression, cohérent avec le
  reste des cycles `dev-lifecycle` de ce dépôt (TODO §52-§62).
- Ordre de grandeur comparable au lot A4 (`headers-http-globaux`, PR #128 : 1519 lignes / 25
  fichiers / 9 commits) — un cran au-dessus du fait de la vraie dépendance externe (protocole
  réseau + Testcontainers), cf. estimation donnée avant le lancement de ce cycle.

## Risks & Mitigation

- **Temps de démarrage `clamd` en IT (chargement des signatures, ~1-2 min)** → mesuré
  concrètement en T8 avant de décider d'un job CI séparé, pas anticipé à l'aveugle.
- **Image `clamav/clamav:1.4` indisponible dans le bac à sable de développement** (déjà vécu sur
  `docker build`/Docker Hub pour le lot A1, `provisioning-tenant`) → si constaté, documenter la
  limite comme pour A1 (`docs/ai/implementation/2026-09-08-feature-provisioning-tenant.md`) et
  valider T3/T4 par tests unitaires (socket loopback, sans dépendance à l'image réelle) : seule
  T8 serait bloquée, pas M1/M2.
- **Dérive `StreamMaxLength`/`APP_DOSSIER_ATTACHMENT_MAX_SIZE_MB`** (D-AV-CLAMAV-SIZE-ALIGN) →
  mitigée par le commentaire explicite prévu en T6/T7, pas par un contrôle automatisé (accepté
  comme limite documentée, cohérent avec la décision Phase 3).
- **Couverture Jacoco du chemin fail-open** (branches d'erreur nombreuses en T4) → si le seuil
  BRANCH global (75%) est mis sous tension, traiter comme les autres lots (Phase 8 dédiée,
  balayage classe par classe façon `questions-utilisateur-taches`/`connecteurs-cible`), pas de
  contournement du seuil.

## Resources Needed

- Aucune ressource humaine/outillage supplémentaire — même équipe (agent + revue utilisateur),
  mêmes commandes (`./mvnw verify`, `docker compose --profile clamav`).
- Accès registre Docker pour `clamav/clamav:1.4` (T6/T8) — à confirmer en Phase 5, cf. risque
  ci-dessus.
