---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
---

# Requirements & Problem Understanding — `scan-antivirus-clamav` (lot C)

> Positionnement file produit : [DEFERRED-FEATURES.md](../../../DEFERRED-FEATURES.md) §6, lot **C**
> « Adaptateur AV (ClamAV) », après A1 (`provisioning-tenant`, livré PR #119). Aucune
> surface partagée avec A4 (`headers-http-globaux`, PR #128, `SecurityConfig` uniquement) ni
> B1 (`connecteurs-cible`, livré PR #129).

## Problem Statement

**What problem are we solving?**

- Les pièces jointes (`ElementType.ATTACHMENT`) déposées via
  `POST /api/dossiers/{id}/elements/attachment` (`DossierElementUploadController`) ne sont
  aujourd'hui validées que sur le type déclaré, les magic-bytes (`AttachmentContentSniffer`) et
  la taille (`spring.servlet.multipart.max-file-size`) — **aucun moteur antivirus réel** ne les
  inspecte. `AttachmentScanPort` existe et est appelé sur ce chemin, mais son unique
  implémentation, `NoOpAttachmentScanAdapter`, retourne toujours « clean » (décision D-AV,
  `durcissement-donnees-sensibles`, PR #62).
- Affecte tout tenant qui autorise l'upload de pièces jointes dans un dossier — un fichier
  infecté déposé par un utilisateur authentifié (n'importe quel rôle avec `EDIT`) est stocké et
  redistribué tel quel (téléchargement par d'autres utilisateurs du dossier, éventuel export).
- Situation actuelle : le risque est **documenté et assumé explicitement** comme limite V1
  (`REVUE-ALIGNEMENT-SECURITE.md` §S-6, `ROADMAP.md` §2.3) — pas un incident, une dette connue
  avec un contrat déjà prêt pour la combler.

## Goals & Objectives

**What do we want to achieve?**

- **Objectif principal :** fournir une implémentation de référence de `AttachmentScanPort`
  adossée à un vrai moteur antivirus (ClamAV / `clamd`), activable par configuration, **sans
  modifier le port ni son appelant** (`DossierElementUploadController`) — le port reste
  générique, ClamAV n'est qu'un adaptateur `infrastructure` parmi de futurs possibles (décision
  explicite utilisateur : un autre moteur AV pourra remplacer ClamAV plus tard en n'ajoutant
  qu'un nouvel adaptateur).
- **Objectifs secondaires :**
  - Kill switch opt-in (comportement par défaut inchangé — NoOp — tant que l'opérateur n'a pas
    explicitement activé le scan et provisionné `clamd`).
  - Service `clamd` démarrable en local/démo via `docker-compose.yml` (nouveau profil, sur le
    modèle `kafka`/`minio`).
  - Documentation ops à jour (`DEPLOIEMENT.md`, `GETTING-STARTED.md`, `.env.example`) pour
    activer le scan en production, en traitant `clamd` comme un service externe déjà
    provisionné (même modèle que PostgreSQL/Keycloak — voir décision D-AV-CLAMAV-PROD-TOPO).
  - **Signal de santé actif sur la disponibilité de `clamd`** (décision D-AV-CLAMAV-HEALTH,
    Phase 2) — le fail-open retenu (D-AV-CLAMAV-FAILMODE) ne doit pas pouvoir désactiver le
    scan silencieusement et durablement sans qu'un opérateur puisse le détecter autrement qu'en
    scrutant les logs requête par requête.
- **Non-goals (hors scope explicite) :**
  - Modifier `AttachmentScanPort` / `AttachmentScanResult` ou leur contrat d'appel.
  - Système à plugins pour faire tourner **plusieurs** moteurs AV simultanément — rien ne le
    demande aujourd'hui ; l'architecture hexagonale existante suffit déjà à changer de
    fournisseur le jour venu (un adaptateur = un moteur actif).
  - Politique AV par tenant (un seul comportement global par déploiement, comme le reste de la
    configuration infra).
  - Mise en quarantaine / re-scan différé des pièces jointes déjà stockées avant l'activation du
    scan (pas de rescan rétroactif).
  - Scan des documents générés en interne (`PRODUCED_DOC`, `generation-documents`) — hors
    périmètre, seul le chemin d'upload utilisateur (`ATTACHMENT`) est concerné.
  - Écran d'administration dédié — l'activation reste une configuration de déploiement (variable
    d'environnement), comme le kill switch headers de la PR #128, pas un réglage exposé en UI.

## User Stories & Use Cases

**How will users interact with the solution?**

- En tant qu'opérateur de la plateforme, je veux pouvoir activer le scan antivirus par
  configuration (sans changer de version applicative) une fois `clamd` provisionné, afin de
  combler S-6 sans big-bang de déploiement.
- En tant qu'utilisateur avec accès `EDIT` sur un dossier, quand je dépose une pièce jointe
  détectée infectée par `clamd`, je veux recevoir un rejet clair (même comportement HTTP que les
  rejets existants : type non autorisé, magic-bytes incohérents) plutôt qu'un stockage silencieux.
- En tant qu'opérateur, si `clamd` tombe ou timeout pendant un upload, je veux que la plateforme
  **continue de fonctionner** (upload accepté, comme aujourd'hui) plutôt que de bloquer tous les
  dépôts de pièces jointes du tenant sur la panne d'un composant annexe — avec une trace
  exploitable (log) de l'incident pour investigation a posteriori.
- Edge cases :
  - Fichier propre, `clamd` up → accepté (comportement inchangé du point de vue utilisateur).
  - Fichier infecté (test EICAR), `clamd` up → rejeté, `InvalidDossierElementException`
    (« attachment rejected: ... »), même famille d'erreur que les rejets existants.
  - `clamd` indisponible / timeout → accepté + log + compteur (fail-open, décision ci-dessous),
    jamais d'exception remontée à l'appelant pour ce cas précis.
  - Scan désactivé (`app.dossier.attachment.scan.type=noop`, défaut) → comportement actuel
    inchangé (NoOp, toujours clean).

## Success Criteria

**How will we know when we're done?**

- Upload réel d'un fichier EICAR via l'endpoint (`clamd` up, scan activé) → rejeté.
- Upload réel d'un fichier propre (`clamd` up, scan activé) → accepté, comportement identique à
  aujourd'hui.
- Upload réel avec `clamd` indisponible (scan activé) → accepté, un log est émis avec assez de
  contexte (tenant, dossier, raison) pour être exploitable en observabilité, **et** le compteur
  Micrometer `flowforge.attachment.scan.fail_open` (exposé via `/actuator/prometheus`, même
  mécanisme que `observabilite` PR #59 ; nom confirmé en Phase 3, D-AV-CLAMAV-HEALTH) s'incrémente
  — vérifiable par un test réel, pas seulement par lecture de log.
- Scan désactivé (défaut) → comportement strictement identique à l'état actuel (`NoOp`), aucune
  régression sur les tests existants (`DossierElementUploadControllerTest`,
  `NoOpAttachmentScanAdapterTest`).
- `AttachmentScanPort` / `AttachmentScanResult` / la signature d'appel dans
  `DossierElementUploadController` restent **inchangés** (diff nul sur ces fichiers, hors
  éventuel commentaire).
- `./mvnw verify` vert sur la suite standard (l'IT Testcontainers ClamAV en est exclue par
  défaut, cf. décision test ci-dessous) ; l'IT ClamAV dédiée verte en exécution isolée.
- `docker-compose --profile clamav up -d` démarre `clamd`, joignable par le backend.
- `DEPLOIEMENT.md` / `GETTING-STARTED.md` / `.env.example` documentent la variable d'activation
  et le profil Compose.
- `CHANGELOG.md` (section Sécurité) + `TODO.md` / `FILE-ATTENTE.md` / `DEFERRED-FEATURES.md`
  mis à jour à la clôture du cycle (lot C passé de « backlog » à « livré »).

## Constraints & Assumptions

**What limitations do we need to work within?**

- **Architecture hexagonale stricte** : l'adaptateur ClamAV vit entièrement dans
  `infrastructure` (ex. `infrastructure.dossier.ClamAvAttachmentScanAdapter`), zéro fuite dans
  `domain` — même règle ArchUnit qu'aujourd'hui.
- **Pas de nouvelle dépendance runtime tierce** pour parler à `clamd` — protocole `INSTREAM` en
  TCP natif implémenté directement (décision D-AV-CLAMAV-PROTO ci-dessous).
- **Taille déjà bornée en amont** (`spring.servlet.multipart.max-file-size`/`max-request-size`) —
  l'adaptateur ne duplique pas cette limite, il scanne ce qui a déjà passé ce filtre.
- **`clamd` mutualisé, pas de contrainte multi-tenant PostgreSQL** — décision D-AV-CLAMAV-TOPO
  ci-dessous, écart assumé au principe « 1 instance Spring Boot = 1 tenant » (qui porte sur
  l'isolation des données, pas sur un service sans état comme un scan AV).
- **Timeout de scan borné** pour ne pas dégrader la latence perçue de l'upload — **5 s connexion
  + 5 s lecture** (D-AV-CLAMAV-TIMEOUT, Phase 3, alignés sur `ConnectorProperties.Http`/
  `NotificationProperties.Webhook`, déjà à 5s dans ce dépôt pour tout appel réseau sortant).
- **Local/démo uniquement :** `clamd` tourne comme conteneur Compose (profil `clamav`, sur le
  modèle `kafka`), joignable en TCP depuis l'instance backend sur le réseau Compose interne, sans
  TLS/auth sur ce canal (cohérent avec PostgreSQL/Kafka en réseau Compose interne aujourd'hui).
  `docker-compose.yml` reste une infra locale/démo (en-tête du fichier) — ce n'est **pas** le
  mécanisme de déploiement prod.
- **Production :** `clamd` est un service externe déjà provisionné, au même titre que
  PostgreSQL/Keycloak aujourd'hui (`DEPLOIEMENT.md` §4/§5) — l'application ne fait que s'y
  connecter via la config `host`/`port` (décision D-AV-CLAMAV-PROD-TOPO, Phase 2). Le
  provisioning réel (dimensionnement mémoire, mise à jour des signatures, éventuelle HA) reste
  une procédure manuelle hors scope de ce cycle, documentée comme limite assumée dans
  `DEPLOIEMENT.md` — pas d'équivalent `provision-tenant.sh` pour `clamd` dans ce lot.
- Le fail-open ne s'applique **qu'à l'indisponibilité/l'erreur technique** de `clamd` (connexion
  refusée, timeout, erreur protocole — y compris une réponse « taille de flux dépassée » côté
  `clamd`, `StreamMaxLength`, traitée comme une erreur technique et non une infection). Un scan
  qui **aboutit** et détecte une infection reste bloquant — pas de mode « détecté mais on laisse
  passer ».

## Questions & Open Items

**What do we still need to clarify?**

Douze décisions produit/sécurité/architecture ont été tranchées au fil des trois premières
phases — 5 en Phase 1 (`AskUserQuestion`), 2 en Phase 2 (`AskUserQuestion`, après avoir challengé
le fail-open et l'ambiguïté dev/prod du brouillon), 5 en Phase 3 (tranchées directement par
alignement sur des patterns déjà établis dans le dépôt, sans nouvelle question produit — voir
détail et alternatives écartées dans le document design associé) — aucune décision silencieuse :

| ID | Phase | Décision | Choix |
|---|---|---|---|
| D-AV-CLAMAV-PROTO | 1 | Intégration avec `clamd` | TCP natif, commande `INSTREAM`, pas de bibliothèque tierce |
| D-AV-CLAMAV-FAILMODE | 1 | `clamd` indisponible/timeout pendant un upload | **Fail-open** : upload accepté, incident loggé (pas de blocage sur panne d'un composant annexe) |
| D-AV-CLAMAV-TOPO | 1 | Topologie ops | `clamd` **mutualisé** entre tenants (stateless, pas de couplage à l'isolation PG par schéma) |
| D-AV-CLAMAV-DEFAULT | 1 | Activation par défaut | **Opt-in, désactivé par défaut** — `NoOp` reste le comportement par défaut ; activation explicite une fois `clamd` provisionné, pour ne pas casser un déploiement existant sur mise à jour (mécanique précisée en Phase 3, D-AV-CLAMAV-CONFIG-SELECTOR) |
| D-AV-CLAMAV-TEST-SCOPE | 1 | Portée de l'IT réelle (Testcontainers + EICAR) | **Isolée** du run standard `./mvnw verify`, même traitement que les IT PostgreSQL Testcontainers (tag/profil dédié, job CI séparé) |
| D-AV-CLAMAV-HEALTH | 2 | Observabilité du fail-open, au-delà du log | **Signal de santé actif exigé dans ce cycle** — compteur Micrometer dédié, nom confirmé en Phase 3 : `flowforge.attachment.scan.fail_open` (pas `clamav.scan.fail_open` comme envisagé en Phase 1/2) |
| D-AV-CLAMAV-PROD-TOPO | 2 | Déploiement de `clamd` en production | **Service externe déjà provisionné**, même modèle que PostgreSQL/Keycloak (`DEPLOIEMENT.md` §4/§5) — l'app se contente de la config `host`/`port` ; provisioning réel hors scope de ce cycle, documenté comme limite assumée |
| D-AV-CLAMAV-TIMEOUT | 3 | Timeout connexion/lecture `clamd` | **5 s chacun**, configurables — alignés sur `ConnectorProperties.Http`/`NotificationProperties.Webhook` (déjà 5s dans ce dépôt pour tout appel réseau sortant), pas les 10s du SFTP (appel distant, pas comparable) |
| D-AV-CLAMAV-IMAGE | 3 | Tag d'image `clamav/clamav` | **Épinglé** (`clamav/clamav:1.4`), pas `latest`/`stable` — cohérent avec le pinning `postgres:17-alpine`/`keycloak:26.7.1`/`apache/kafka:3.8.0` sur un composant sécurité-critique |
| D-AV-CLAMAV-FRESHCLAM | 3 | Mise à jour des signatures virales | Mécanisme **natif de l'image officielle** (`FRESHCLAM_CHECKS`) — pas de job Compose/cron séparé, aucun nouveau composant à opérer |
| D-AV-CLAMAV-SIZE-ALIGN | 3 | Alignement taille `clamd`/appli | `clamd.conf` `StreamMaxLength` = `APP_DOSSIER_ATTACHMENT_MAX_SIZE_MB` (25 Mo par défaut de part et d'autre aujourd'hui), **documenté explicitement** pour éviter une désynchronisation silencieuse si l'un des deux est relevé sans l'autre |
| D-AV-CLAMAV-CONFIG-SELECTOR | 3 | Mécanique d'activation | Propriété `app.dossier.attachment.scan.type` (`noop`/`clamav`), **calquée sur `app.storage.type`** (`StorageConfig`) — remplace le booléen `enabled` envisagé en Phase 1/2, même sémantique opt-in mais réutilise un idiome déjà établi dans le dépôt pour sélectionner un adaptateur unique parmi plusieurs |

Aucun point laissé ouvert à l'issue de la Phase 3 — les 5 détails d'implémentation identifiés en
Phase 2 (timeout, tag d'image, mécanisme `freshclam`, alignement de taille, nom de métrique) sont
tous tranchés ci-dessus. Prochaine phase : 4 (planning), pour découper ces décisions en tâches
d'implémentation TDD.
