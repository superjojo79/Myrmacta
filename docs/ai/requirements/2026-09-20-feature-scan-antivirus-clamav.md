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
    provisionner et activer le scan en production.
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
  - `clamd` indisponible / timeout → accepté + log (fail-open, décision ci-dessous), jamais
    d'exception remontée à l'appelant pour ce cas précis.
  - Scan désactivé (`enabled=false`, défaut) → comportement actuel inchangé (NoOp, toujours
    clean).

## Success Criteria

**How will we know when we're done?**

- Upload réel d'un fichier EICAR via l'endpoint (`clamd` up, scan activé) → rejeté.
- Upload réel d'un fichier propre (`clamd` up, scan activé) → accepté, comportement identique à
  aujourd'hui.
- Upload réel avec `clamd` indisponible (scan activé) → accepté, un log est émis avec assez de
  contexte (tenant, dossier, raison) pour être exploitable en observabilité.
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
- **Timeout de scan borné** pour ne pas dégrader la latence perçue de l'upload — valeur exacte à
  trancher en Phase 3 (Design), ordre de grandeur : quelques secondes.
- Hypothèse : `clamd` tourne comme conteneur Compose (profil `clamav`, sur le modèle `kafka`),
  joignable en TCP depuis l'instance backend sur le réseau Compose interne, sans TLS/auth sur ce
  canal (cohérent avec PostgreSQL/Kafka en interne réseau Compose aujourd'hui).
- Hypothèse : le fail-open ne s'applique **qu'à l'indisponibilité/l'erreur technique** de
  `clamd` (connexion refusée, timeout, erreur protocole). Un scan qui **aboutit** et détecte une
  infection reste bloquant — pas de mode « détecté mais on laisse passer ».

## Questions & Open Items

**What do we still need to clarify?**

Cinq décisions produit/sécurité ont été tranchées explicitement en Phase 1 (via
`AskUserQuestion`, pas de décision silencieuse) :

| ID | Décision | Choix |
|---|---|---|
| D-AV-CLAMAV-PROTO | Intégration avec `clamd` | TCP natif, commande `INSTREAM`, pas de bibliothèque tierce |
| D-AV-CLAMAV-FAILMODE | `clamd` indisponible/timeout pendant un upload | **Fail-open** : upload accepté, incident loggé (pas de blocage sur panne d'un composant annexe) |
| D-AV-CLAMAV-TOPO | Topologie ops | `clamd` **mutualisé** entre tenants (stateless, pas de couplage à l'isolation PG par schéma) |
| D-AV-CLAMAV-DEFAULT | Activation par défaut | **Opt-in, désactivé par défaut** — NoOp reste le comportement par défaut ; activation explicite (`app.dossier.attachment-scan.clamav.enabled=true`) une fois `clamd` provisionné, pour ne pas casser un déploiement existant sur mise à jour |
| D-AV-CLAMAV-TEST-SCOPE | Portée de l'IT réelle (Testcontainers + EICAR) | **Isolée** du run standard `./mvnw verify`, même traitement que les IT PostgreSQL Testcontainers (tag/profil dédié, job CI séparé) |

Points laissés à la Phase 3 (Design), non bloquants pour clore la Phase 1 — détails
d'implémentation, pas des désaccords produit :

- Valeur exacte du timeout de connexion/scan `clamd`.
- Tag d'image `clamav/clamav` exact à épingler (pas `latest` en CI/prod).
- Mécanisme de mise à jour des signatures virales (`freshclam` intégré à l'image vs job séparé) —
  responsabilité ops documentée dans `DEPLOIEMENT.md`.
- Métrique/observabilité du fail-open (compteur Prometheus dédié, réutilisation de
  l'instrumentation Micrometer déjà livrée par `observabilite`, PR #59) — nice-to-have, pas un
  critère de succès bloquant pour ce cycle.
