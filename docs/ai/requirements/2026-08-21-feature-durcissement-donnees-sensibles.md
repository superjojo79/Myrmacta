---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-08-21
updated: 2026-08-21
feature: durcissement-donnees-sensibles
phase1: 2026-08-21
phase2: 2026-08-21
stakeholder_decisions: 2026-08-21
todo_ref: TODO.md §23 (reste) · §30 (reste) · FILE-ATTENTE.md (priorité 1) · REVUE-PRE-IMPLEMENTATION.md §9
---

# Requirements & Problem Understanding — `durcissement-donnees-sensibles`

> Feature produit issue du regroupement TODO §23 (reste) + §30 (reste),
> priorité 1 de `FILE-ATTENTE.md` (2026-08-20). Prolonge directement
> l'audit sécurité de la session `observabilite` (rate limiting, SSRF
> PDF/webhooks déjà livrés, TODO §30). Phase 1 et 2 tenues ensemble
> (4 décisions produit tranchées 2026-08-21).

## Problem Statement
**What problem are we solving?**

Quatre lacunes de durcissement identifiées par l'audit sécurité restent
ouvertes, sans qu'aucune ne bloque le lancement mais toutes exposant un
risque réel une fois le volume de données/tenants croissant :

1. **Pièces jointes non inspectées.** `DossierElementUploadController`
   (`POST /api/dossiers/{id}/elements/attachment`) ne vérifie que le
   `Content-Type` **déclaré** par le client (`AttachmentUploadProperties`
   allowlist) — aucun contrôle du contenu réel du fichier, aucun scan
   antivirus. Un fichier malveillant renommé avec un type MIME autorisé
   passe sans détection.
2. **Aucun export de données dossiers.** Le flag `FieldDefinition.sensitive`
   est déjà masqué à l'affichage/API (`DossierFieldRedactor`), mais rien
   n'existe pour extraire les dossiers d'un type en masse — un besoin
   métier récurrent (reporting, migration, contrôle qualité) que les
   utilisateurs contournent aujourd'hui via des captures d'écran ou des
   copier-coller manuels, sans garantie de masquage des champs sensibles.
3. **`http-rest` limité au rôle TRIGGER.** Le modèle `ConnectorCapability`
   prévoit déjà `SERVICE`, mais aucune implémentation `ServiceConnector`
   n'existe pour `http-rest`. Le guard SSRF `OutboundUrlGuard` existe et
   est éprouvé sur les webhooks sortants, mais n'a jamais été validé pour
   un usage connecteur sortant à la demande d'un workflow (appel HTTP
   arbitraire déclenché par une tâche de service).
4. **Surface de fuite de secrets non auditée en profondeur.** Les endpoints
   connecteurs admin masquent déjà les secrets (`ConnectorResponse.maskSecrets`),
   mais l'audit initial (TODO §30) n'a couvert ni les autres endpoints admin
   (ex. `NotificationWebhookConfig`/`secretCiphertext`), ni les messages
   d'exception qui pourraient exposer une config déchiffrée en trace
   d'erreur. Aucun garde-fou anti-régression n'empêche qu'un futur endpoint
   réintroduise une fuite.

- **Affectés :** `TENANT_ADMIN` / `WORKFLOW_ADMIN` (upload de pièces,
  export de dossiers) ; ops/sécurité (audit secrets, futur connecteur
  SERVICE) ; conformité (masquage sensible sur export).
- **Situation actuelle :** voir `TODO.md` §23 et §30 (partiels), et
  `docs/specs/04-dossiers.md`, `06-connecteurs.md`, `09-notifications.md`,
  `12-autorisation-conformite.md`.

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

1. **Upload durci** : validation du contenu réel du fichier (magic-bytes)
   en complément de l'allowlist MIME déclarative existante, sur
   `DossierElementUploadController`. Nouveau port de domaine
   `AttachmentScanPort` invoqué dans le flux d'upload, avec un adaptateur
   **no-op** par défaut (retourne toujours « propre ») — le contrat est en
   place pour brancher un vrai moteur antivirus plus tard sans redévelopper
   ni changer la signature du port.
2. **Export CSV masqué** : nouvel endpoint (type
   `GET /api/dossiers/export.csv?dossierTypeId=...`) qui réutilise les
   filtres de la liste dossiers existante (statut, etc.), exporte les
   colonnes fixes **et** les champs dynamiques du `DossierType`, avec les
   champs `sensitive` masqués selon la même règle que
   `DossierFieldRedactor` (masqué pour tout `AccessContext.User` non
   `TENANT_ADMIN`). Accès réservé aux mêmes rôles que la lecture liste
   dossiers (`DOSSIER_VIEWER` et au-dessus) — pas de restriction admin
   supplémentaire.
3. **Skeleton `ServiceConnector` `http-rest`** : nouvelle implémentation
   `HttpRestServiceConnector` (package `com.flowforge.infrastructure.connector.builtin`)
   réutilisant `OutboundUrlGuard.assertSafe(...)` avant tout appel sortant.
   **Non enregistré** dans le `ConnectorRegistry` — pas d'exposition API/UI,
   pas de rôle `SERVICE` activable en V1 (cohérent avec FILE-ATTENTE : le
   connecteur lui-même reste hors V1, seul le hardening est pré-positionné).
   Tests dédiés prouvant que le guard bloque bien loopback / link-local /
   DNS rebinding sur ce nouveau point d'appel.
4. **Audit exhaustif anti-fuite de secrets** : relecture de tous les
   contrôleurs admin (connecteurs, webhooks de notification, purge,
   templates, workflow-type-configs) et des `ExceptionHandler` pour vérifier
   qu'aucun secret déchiffré n'apparaît en clair dans une réponse JSON ou un
   message d'exception. Corrections ciblées si une fuite est trouvée (le
   candidat identifié en amont : `NotificationWebhookConfig`/`secretCiphertext`
   côté admin webhooks). Ajout d'un garde-fou anti-régression (test
   dédié — sérialise chaque DTO admin exposant un champ potentiellement
   sensible et vérifie qu'aucune valeur déchiffrée n'y figure).

### Secondary goals

- Documenter dans `docs/specs/06-connecteurs.md` le contrat
  `ServiceConnector` + `OutboundUrlGuard` pour faciliter l'activation
  future du rôle SERVICE.
- Documenter le contrat `AttachmentScanPort` (javadoc + `docs/specs/04-dossiers.md`)
  pour qu'un futur adaptateur AV (ClamAV ou autre) sache où s'insérer.

### Non-goals (actés)

- Rate limiter distribué (Redis) — décision déjà actée TODO §30, hors
  stack figée, pas de multi-réplique prévue à court terme.
- Intégration réelle d'un moteur antivirus (ClamAV ou autre) — seul le
  port + adaptateur no-op sont livrés.
- Activation réelle du connecteur `http-rest` en rôle SERVICE (UI, registry,
  workflow) — V1 reste TRIGGER uniquement (FILE-ATTENTE, hors V1).
- Import CSV / upload de templates — aucune lacune identifiée, ces
  fonctionnalités n'existent pas et ne sont pas demandées ici.
- Export d'autres entités que les dossiers (workflows, connecteurs…).
- Nouvelle table d'audit ou écran dédié à l'export CSV (l'action reste
  journalisée via `SecurityAuditLog` existant si pertinent, pas de nouvelle
  UI d'audit).
- Modifier le modèle multi-tenant, l'authentification, ou les rôles RBAC
  existants (décisions n° 13, 19 déjà actées).

## Alternatives considérées

| Sujet | Id | Approche | Trade-off | Verdict |
|---|---|---|---|---|
| Antivirus | A | ClamAV (clamd) via docker-compose dès V1 | Nouvelle dépendance d'infra hors stack figée | Écarté V1 |
| Antivirus | B | **Magic-bytes + port pluggable no-op** | Pas de vrai scan de signatures virales V1, mais contrat prêt | **Retenu** |
| Antivirus | C | Ne rien faire (allowlist MIME déclarative seule) | Ne couvre pas le TODO §30 (type déclaré non vérifié) | Écarté |
| Export CSV | A | **Nouvel endpoint dossiers filtrable + masquage** | Nouvelle fonctionnalité (au-delà du strict hardening) | **Retenu** |
| Export CSV | B | Réinterpréter le point comme déjà couvert (masquage API existant) | Ne répond pas au besoin métier d'extraction en masse évoqué au TODO §23 | Écarté |
| `http-rest` SERVICE | A | Retirer le sous-point (guard déjà testé sur webhooks) | Ne prouve pas la réutilisabilité pour un connecteur à la demande d'un workflow | Écarté |
| `http-rest` SERVICE | B | **Skeleton non enregistré + tests guard** | Code mort tant que non activé, mais zéro risque d'exposition et prêt à l'activation | **Retenu** |
| `http-rest` SERVICE | C | Activer complètement le connecteur SERVICE (registry, UI, workflow) | Dépasse le périmètre FILE-ATTENTE (« pas le connecteur lui-même ») | Écarté |
| Fuite secrets | A | Clore sans développement (existant jugé suffisant) | Le candidat `secretCiphertext` webhooks n'a pas été vérifié | Écarté |
| Fuite secrets | B | **Audit exhaustif endpoints admin + erreurs + garde-fou** | Effort plus large mais ferme réellement le TODO §30 | **Retenu** |

## User Stories & Use Cases

1. **En tant qu'** utilisateur métier (`DOSSIER_MANAGER`), **je veux** que
   toute pièce jointe uploadée soit vérifiée sur son contenu réel (pas
   seulement son type déclaré), **afin de** réduire le risque qu'un fichier
   malveillant renommé soit accepté.
2. **En tant qu'** opérateur (`DOSSIER_VIEWER` et au-dessus), **je veux**
   exporter en CSV les dossiers d'un type donné avec les mêmes filtres que
   la liste, **afin de** produire un rapport sans exposer les champs
   marqués `sensitive` si je ne suis pas `TENANT_ADMIN`.
3. **En tant que** `TENANT_ADMIN`, **je veux** que l'export CSV masque les
   champs sensibles pour les rôles non-admin exactement comme le fait déjà
   l'API de lecture dossier, **afin de** garder une règle de masquage
   cohérente entre canaux.
4. **En tant qu'** ops/sécurité, **je veux** la certitude qu'aucun secret de
   connecteur ou de webhook déchiffré n'apparaît dans une réponse admin ou
   une trace d'erreur, **afin de** clore l'audit sécurité TODO §30.
5. **En tant que** développeur futur, **je veux** un `ServiceConnector`
   `http-rest` déjà écrit et testé contre le guard SSRF, **afin de**
   pouvoir l'activer rapidement quand le rôle SERVICE sera mis en V1, sans
   redécouvrir les pièges SSRF.

### Edge cases

- Upload : fichier dont l'extension/MIME déclaré est autorisé mais dont les
  magic-bytes ne correspondent à aucun type autorisé → rejeté (400),
  message distinct du rejet MIME actuel pour permettre le diagnostic.
- Upload : fichier vide (0 octet) → pas de crash sur la détection de
  magic-bytes ; traité comme non reconnu → rejeté.
- Export CSV : `dossierTypeId` sans dossiers correspondants → CSV avec
  en-têtes seulement (pas d'erreur).
- Export CSV : dossier dont un champ dynamique a été supprimé du
  `DossierType` depuis sa création → colonne absente, pas de crash.
- Export CSV : très grand volume de dossiers → pas de chargement intégral
  en mémoire imprévisible (streaming ou pagination interne à documenter en
  design).
- `http-rest` SERVICE skeleton : URL cible résolvant vers une IP privée
  après redirection (rebinding) → `OutboundUrlGuard` bloque, comportement
  identique aux webhooks sortants.
- Audit secrets : un futur endpoint admin ajoute un champ secret sans le
  masquer → le garde-fou anti-régression doit échouer en CI, pas seulement
  documenter.

## Success Criteria
**How will we know when we're done?**

- [ ] `DossierElementUploadController` rejette un fichier dont les
      magic-bytes ne correspondent pas à un type autorisé, même si le
      `Content-Type` déclaré est dans l'allowlist.
- [ ] `AttachmentScanPort` existe (domaine), invoqué dans le flux d'upload,
      avec adaptateur no-op par défaut documenté comme tel.
- [ ] `GET /api/dossiers/export.csv?dossierTypeId=...` : 200 + CSV filtré
      (mêmes filtres que la liste dossiers), champs `sensitive` masqués
      pour non-`TENANT_ADMIN`, en clair pour `TENANT_ADMIN` — cohérent avec
      `DossierFieldRedactor`. Rôles < `DOSSIER_VIEWER` → 403.
- [ ] `HttpRestServiceConnector` implémente `ServiceConnector`, appelle
      `OutboundUrlGuard.assertSafe(...)` avant toute requête sortante,
      **non enregistré** dans `ConnectorRegistry` (absent de toute réponse
      API/UI catalogue connecteurs).
- [ ] Audit exhaustif livré : liste des endpoints admin vérifiés (au moins
      connecteurs, webhooks de notification, purge, templates,
      workflow-type-configs) avec verdict par endpoint ; correction poussée
      si fuite trouvée sur `NotificationWebhookConfig`/`secretCiphertext`
      ou ailleurs.
- [ ] Garde-fou anti-régression (test) qui échoue si un champ secret
      apparaît en clair dans une réponse DTO admin sérialisée.
- [ ] Vitest + Playwright si l'export CSV ou tout autre point touche le SPA
      (écran/bouton d'export) — sinon backend seul, à confirmer en design.
- [ ] `./mvnw verify` vert (dont ArchUnit).
- [ ] TODO §23 / §30 mis à jour (clos ou reste réduit) ; FILE-ATTENTE et
      DEFERRED-FEATURES mis à jour après implémentation.

## Constraints & Assumptions

**Contraintes stables :**

- Hexagone : `AttachmentScanPort` et le futur `ServiceConnector` restent
  des ports/adaptateurs — zéro dépendance Spring/JPA dans le domaine.
- Pas de nouvelle dépendance d'infrastructure hors stack figée (pas de
  ClamAV, pas de Redis) — cf. TODO §30 déjà tranché sur ce point.
- `OutboundUrlGuard` est réutilisé tel quel (déjà framework-agnostic) —
  pas de duplication de la logique SSRF.
- `DossierFieldRedactor` est le point de référence pour la règle de
  masquage — l'export CSV doit produire le même résultat de masquage que
  l'API de lecture pour un même rôle.
- Format CSV : pas de bibliothèque CSV externe imposée par la stack — a
  minima ne pas introduire de dépendance PDF/AGPL (cohérent avec
  interdiction iText déjà actée).

**Décisions produit (Phase 1+2, toutes actées 2026-08-21) :**

| Id | Sujet | Choix |
|---|---|---|
| **D-AV** | Scan antivirus pièces jointes | **B** — magic-bytes + `AttachmentScanPort` pluggable, adaptateur no-op par défaut |
| **D-CSV-SCOPE** | Périmètre export | **A** — dossiers d'un type donné, champs dynamiques inclus, masquage sensible |
| **D-CSV-ROLE** | Accès export | Mêmes rôles que lecture liste dossiers (`DOSSIER_VIEWER`+) |
| **D-SERVICE-HTTP** | `http-rest` SERVICE | Skeleton `ServiceConnector` non enregistré (registry), guard réutilisé, tests dédiés |
| **D-SECRETS-AUDIT** | Fuite secrets | Audit exhaustif de tous les endpoints admin + messages d'exception, garde-fou anti-régression |

## Questions & Open Items

### Clos

| Id | Décision | Choix |
|---|---|---|
| **D-AV** | Stratégie antivirus | **B** (2026-08-21) — pas de ClamAV V1, port pluggable no-op |
| **D-CSV-SCOPE** | Export CSV | **A** (2026-08-21) — construit, dossiers d'un type + champs dynamiques |
| **D-CSV-ROLE** | Accès export CSV | Rôles lecture liste dossiers, pas de restriction admin |
| **D-SERVICE-HTTP** | `http-rest` SERVICE | Skeleton non enregistré (2026-08-21) |
| **D-SECRETS-AUDIT** | Fuite secrets | Audit exhaustif (2026-08-21), pas de clôture sans développement |

### Ouvertes

Aucune à l'issue de cette phase. Points techniques à trancher en design
(non bloquants pour la Phase 3, pas des décisions produit) :
- Format de streaming CSV pour gros volumes (buffer complet vs. écriture
  incrémentale sur `HttpServletResponse`).
- Liste exhaustive finale des DTO admin couverts par le garde-fou
  anti-régression (à établir en design après relecture ligne à ligne des
  contrôleurs `infrastructure.web.**.admin`).

### Hors file

Rate limiter distribué (Redis), connecteur `http-rest` SERVICE activé en
production, import CSV, upload de templates, nouvelle table d'audit export.

## Phase 2 — revue close (2026-08-21)

Template README : OK.

**Validé :** 4 décisions produit (D-AV, D-CSV-SCOPE, D-CSV-ROLE,
D-SERVICE-HTTP, D-SECRETS-AUDIT) ; non-goals ; stories ; critères de
succès ; alternatives par sous-sujet.

Status : **`reviewed`**. Prochaine phase : `dev-design` (Phase 3).
