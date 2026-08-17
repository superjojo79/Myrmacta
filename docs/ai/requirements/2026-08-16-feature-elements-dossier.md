---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
---

# Requirements & Problem Understanding — `elements-dossier`

> Identifiée comme prochaine feature dans [DEFERRED-FEATURES.md](../../../DEFERRED-FEATURES.md)
> §1.4 (« Éléments de dossier (`DossierElement`) — Lot suivant — fichiers / MinIO ») et §0
> (contrainte transversale stockage objet).

## Problem Statement
**What problem are we solving?**

`DossierElement` et le port de stockage objet abstrait (`DocumentStoragePort` +
`S3StorageAdapter` + `FilesystemStorageAdapter`) existent déjà dans le code, livrés avec la
feature `generation-documents`. Mais ils sont **verrouillés au seul type `PRODUCED_DOC`** —
les documents générés automatiquement par un `ServiceTask` Flowable
(`AttachGeneratedDocumentTask`) :

```java
// AddDossierElementUseCaseImpl.add(...)
if (command.type() != ElementType.PRODUCED_DOC) {
    throw new InvalidDossierElementException("Only PRODUCED_DOC is supported in V1");
}
```

```java
public enum ElementType {
    PRODUCED_DOC
}
```

Le frontend confirme explicitement le manque — le bouton d'ajout manuel est désactivé :

```html
<!-- ElementsList.vue -->
<button type="button" disabled title="Ajout manuel hors scope V1">Ajouter</button>
```

Un utilisateur (`DOSSIER_MANAGER`) ne peut donc **rien ajouter lui-même** à un dossier : ni
déposer une pièce jointe (contrat, justificatif scanné…), ni consigner une information
structurée, ni laisser une note, ni référencer une ressource externe — alors que
[04-dossiers.md](../../../analysis%20doc/04-dossiers.md) documente `ElementType` avec cinq
valeurs (`PRODUCED_DOC`, `ATTACHMENT`, `INFORMATION`, `NOTE`, `LINK`) depuis l'origine.

**Qui est affecté :** les utilisateurs métier (`DOSSIER_MANAGER`, `DOSSIER_VIEWER` en lecture)
qui instruisent un dossier et ont besoin d'y verser des pièces ou annotations en dehors du strict
flux automatisé du workflow.

**Situation actuelle :** seul un `ServiceTask` BPMN peut ajouter un élément (`PRODUCED_DOC`
uniquement), via un chemin système (`AccessContext.system(...)`). Aucune voie humaine
(REST + UI) n'existe pour les quatre autres types.

## Goals & Objectives
**What do we want to achieve?**

**Primary goals**

- Permettre à un utilisateur autorisé (ACL `EDIT` sur le type de dossier — `DossierAccessPolicy`
  existante, décision n° 20) d'ajouter manuellement un `DossierElement` de type `ATTACHMENT`,
  `INFORMATION`, `NOTE` ou `LINK` à un dossier, via l'API REST et l'UI (`ElementsList.vue`).
- `ATTACHMENT` : upload d'un fichier binaire, stocké via le port `DocumentStoragePort` déjà
  livré (aucun nouveau port ni couplage MinIO — réutilisation stricte de l'existant, cf.
  DEFERRED-FEATURES.md §0).
- Téléchargement générique d'un `ATTACHMENT` avec son `contentType` et son nom de fichier
  d'origine préservés (le contrôleur actuel sert uniquement du `application/pdf` en dur pour
  `PRODUCED_DOC` — à généraliser sans régression sur `PRODUCED_DOC`).
- `INFORMATION` / `NOTE` / `LINK` : éléments sans contenu binaire, portés par le champ
  `metadata` (`Map<String,Object>`) déjà présent sur `DossierElement` — pas de nouveau champ de
  persistance structurant nécessaire.

**Secondary goals**

- Activer le bouton "Ajouter" de `ElementsList.vue` avec un flux adapté par type (sélecteur de
  type → formulaire dédié : file picker pour `ATTACHMENT`, formulaire clé/valeur pour
  `INFORMATION`, zone de texte pour `NOTE`, champ URL + libellé pour `LINK`).
- Poser des limites raisonnables sur l'upload `ATTACHMENT` (taille max, allowlist de types MIME)
  — hardening cohérent avec ce qui existe déjà sur les autres endpoints exposés
  (`/api/webhooks/**`, `/api/documents/generate`, TODO §30).

**Non-goals (explicitement hors scope)**

- Suppression d'un `DossierElement`, quel que soit le type — le modèle reste append-only en V1,
  cohérent avec l'existant (aucun type n'est aujourd'hui supprimable). Toute suppression
  interagirait avec la rétention/purge non tranchée (TODO §21/§22) — **non traité ici,
  explicitement reporté**.
- Analyse antivirale des fichiers déposés — déjà notée comme reste ouvert (TODO §30) ; **non
  traitée par cette feature**, ne pas la trancher silencieusement.
- Rétention par `DossierType` (TODO §21) et purge cohérente PostgreSQL/stockage objet (TODO §22)
  — points ouverts non tranchés, cette feature ne doit pas rendre leur résolution future plus
  difficile (ex. `storageRef` doit rester adressable pour un futur job de purge) mais ne
  l'implémente pas.
- Modification (édition) d'un élément existant (`INFORMATION`/`NOTE`) après ajout — création
  uniquement en V1.
- Résolution / suivi de disponibilité côté serveur d'un `LINK` — l'URL est stockée comme texte
  opaque, jamais requêtée par le backend (élimine toute surface SSRF nouvelle liée à ce type).
- Nouveau modèle d'ACL dédié aux éléments — `DossierAccessPolicy` existante (granularité
  `DossierType`, décision n° 20) réutilisée telle quelle.
- `DossierWorkflowLink` / `AttachWorkflow` (autre item du même tableau DEFERRED-FEATURES §1.4) —
  lot distinct, non traité ici.

## User Stories & Use Cases
**How will users interact with the solution?**

- En tant que `DOSSIER_MANAGER` disposant d'un accès `EDIT` sur le type du dossier, je veux
  déposer un fichier (justificatif scanné, contrat signé…) sur un dossier, afin qu'il soit
  consultable et téléchargeable par toute personne ayant accès en lecture à ce dossier.
- En tant que `DOSSIER_MANAGER`, je veux consigner une information structurée (ex. un résultat de
  vérification externe) directement sur le dossier, sans passer par un workflow BPMN dédié.
- En tant que `DOSSIER_MANAGER`, je veux laisser une note libre à l'attention des autres
  instructeurs du dossier.
- En tant que `DOSSIER_MANAGER`, je veux référencer un lien externe pertinent pour le dossier
  (ex. billet d'un outil tiers), sans que le serveur n'aille le résoudre.
- En tant que `DOSSIER_VIEWER` (accès `VIEW` uniquement), je veux consulter et télécharger les
  éléments d'un dossier, mais pas en ajouter (comportement déjà garanti par
  `DossierAccessPolicy.assertCanEdit` vs `assertCanView`).
- En tant qu'utilisateur sans ACL `VIEW`/`EDIT` sur le type du dossier, je dois recevoir un 403
  sur toute tentative d'ajout ou de lecture d'élément — déjà garanti par la policy existante,
  aucune régression attendue.

**Edge cases**

- Upload d'un fichier dépassant la taille max → rejet explicite (400/413), pas de troncature
  silencieuse.
- Upload d'un type MIME hors allowlist → rejet explicite (400).
- `dossierId` inexistant ou d'un autre tenant → 404 (comportement déjà garanti par
  `AddDossierElementUseCaseImpl`, à conserver pour les nouveaux types).
- Dossier dans un statut terminal (`CLOTURE`/`ARCHIVE`) : ajout manuel refusé (`DossierConflictException`
  → 409), par cohérence avec `UpdateDossierFieldsUseCaseImpl` — voir Questions & Open Items (résolu).
- `LINK` avec une URL malformée → validation basique côté serveur (format URI), mais **aucune
  résolution/fetch**.

## Success Criteria
**How will we know when we're done?**

- `POST` d'ajout d'élément accepte `ATTACHMENT`, `INFORMATION`, `NOTE`, `LINK` en plus de
  `PRODUCED_DOC`, avec validation par type et rejet explicite (`InvalidDossierElementException` →
  400) des combinaisons invalides (ex. `ATTACHMENT` sans fichier, `LINK` sans URL valide).
- `GET /api/dossier-elements/{id}/content` sert un `ATTACHMENT` avec le bon `Content-Type` et le
  nom de fichier d'origine ; le comportement existant pour `PRODUCED_DOC` (PDF) est inchangé —
  couvert par un test de non-régression.
- `DossierElementView.downloadable` reflète correctement l'ATTACHMENT (true dès que le
  `storageRef` est présent), et reste `false` pour `INFORMATION`/`NOTE`/`LINK`.
- ACL : un utilisateur avec seulement `VIEW` reçoit 403 sur toute tentative d'ajout, quel que soit
  le type ; test couvrant chacun des 4 nouveaux types.
- Upload hors limites (taille/MIME) → 400/413 avec `ProblemDetail`, test dédié.
- `ElementsList.vue` : bouton "Ajouter" fonctionnel, formulaire adapté par type, couverture
  Vitest (composant) **et** Playwright (parcours e2e upload + affichage), conformément à
  `flowforge-conventions` (Vitest + Playwright obligatoires dès que le SPA ou son contrat API est
  touché).
- `./mvnw verify` (ArchUnit + Jacoco ≥ seuil existant) vert ; aucune exception d'autoconfig ni de
  couplage domaine → framework introduite.
- Aucune régression sur `AttachGeneratedDocumentTask` / flux `PRODUCED_DOC` existant.
- Ajout manuel (`ATTACHMENT`/`INFORMATION`/`NOTE`/`LINK`) sur un dossier `CLOTURE`/`ARCHIVE` →
  409 (`DossierConflictException`), test dédié ; `PRODUCED_DOC` via `ServiceTask` non concerné par
  cette garde (non-régression du flux workflow existant).

## Constraints & Assumptions
**What limitations do we need to work within?**

**Technical constraints**

- Architecture hexagonale stricte : le domaine (`domain.dossier`) reste pur Java ; toute
  validation de taille/type MIME liée à HTTP (upload multipart) reste dans
  `infrastructure.web`, la validation métier générique (label requis, type cohérent avec le
  contenu) reste dans le use case / un service domaine (symétrie avec `DossierFieldValidator`,
  décision n° 8).
- Réutilisation stricte de `DocumentStoragePort` existant (`store`/`retrieve`/`exists`,
  `tenantId` + `contentType` + `bytes` → `storageRef` opaque tenant-préfixé) — pas de nouveau
  port de stockage.
- Multi-tenant par schéma : `storageRef` doit rester préfixé par `tenantId` (déjà garanti par
  `S3StorageAdapter`/`FilesystemStorageAdapter` et vérifié dans `AddDossierElementUseCaseImpl`
  via `requireValidStorageRef`).
- ACL réutilisée telle quelle : `DossierAccessPolicy.assertCanEdit` (ajout) /
  `assertCanView` (lecture/liste/téléchargement), pas de nouvelle table ni de nouveau modèle de
  permission.
- `ElementType` passe de enum à une seule valeur (`PRODUCED_DOC`) à cinq valeurs — **aucune
  migration Liquibase requise** : `dossier_element.type` est un `varchar(32)` sans contrainte
  `CHECK` (`005-dossier-element-tables.yaml`), l'enum Java est mappé en `STRING`.
- Taille max upload et allowlist MIME : valeurs raisonnables à fixer en design (proposition de
  départ : 25 Mo, allowlist PDF/images/Office/texte), configurables via propriété Spring plutôt
  qu'en dur — cohérent avec `WebhookBodySizeLimitFilter` / `DocumentGenerationBodySizeLimitFilter`
  déjà en place pour d'autres endpoints exposés.

**Business constraints**

- Pas de suppression, pas d'édition post-création (non-goals ci-dessus).
- Antivirus explicitement hors scope (reste dans TODO §30).

**Assumptions**

- `LINK` : simple couple `{url, description}` stocké dans `metadata`, jamais résolu côté serveur.
- `NOTE` : texte libre stocké dans `metadata.text` (ou clé équivalente définie en design), pas de
  limite de longueur stricte au-delà d'un plafond raisonnable anti-abus.
- `INFORMATION` : objet clé/valeur libre stocké dans `metadata`, sans schéma de validation dédié
  (contrairement aux `FieldDefinition` de `DossierType`) — cohérent avec le caractère "bloc de
  données structurées" libre décrit par l'analyse doc, à confirmer en design si un besoin de
  schéma émerge.
- Le flag `sensitive` (décision liée au TODO §23, déjà livré pour `FieldDefinition`) ne
  s'applique pas aux `DossierElement` dans cette feature — aucun signal documentaire ne l'exige
  pour les éléments ; resterait un point ouvert distinct si un besoin apparaît.

## Questions & Open Items
**What do we still need to clarify?**

- ~~**Ajout autorisé sur un dossier `CLOTURE`/`ARCHIVE` ?**~~ **Résolu (Phase 2 — review
  requirements) :** refuser, par cohérence avec le précédent direct déjà livré dans le code —
  `UpdateDossierFieldsUseCaseImpl` bloque déjà toute modification des champs d'un dossier en
  statut `CLOTURE`/`ARCHIVE` (liste `BLOCKED_STATUSES`, `DossierConflictException` → 409). Un
  dossier terminal ne doit pas rester modifiable par un autre chemin (ajout d'élément) alors que
  ses champs sont verrouillés — incohérence évitée en réutilisant la même garde. `PRODUCED_DOC`
  (chemin `ServiceTask`, `AccessContext.system(...)`) n'est **pas** concerné par cette garde : un
  workflow déjà démarré doit pouvoir terminer normalement même si le dossier atteint `CLOTURE`
  entre-temps — la garde s'applique uniquement au chemin humain (`ATTACHMENT`/`INFORMATION`/
  `NOTE`/`LINK`).
- **Valeurs exactes** de la taille max et de l'allowlist MIME — laissées à la phase design
  (proposition de départ ci-dessus), pas un point bloquant pour la suite du requirements.
- ~~Contrainte SQL existante sur `dossier_element.type`~~ **Résolu** — vérifié dans
  `005-dossier-element-tables.yaml` : colonne `varchar(32)` sans contrainte `CHECK`. Aucun
  changelog Liquibase requis pour élargir l'enum `ElementType`.
- Rétention (TODO §21) et purge (TODO §22) restent des points ouverts **non traités par cette
  feature** — rappelé ici pour ne pas les rouvrir/trancher silencieusement pendant
  l'implémentation.
