---
phase: design
title: System Design & Architecture
description: Define the technical architecture, components, and data models
---

# System Design & Architecture — `elements-dossier`

> Complète l'existant (`DossierElement`, `DocumentStoragePort`, `S3StorageAdapter`,
> `FilesystemStorageAdapter`, livrés avec `generation-documents`) — aucun nouveau port de
> stockage, aucune nouvelle table d'ACL. Voir
> [requirements](../requirements/2026-08-16-feature-elements-dossier.md) pour le problème et le
> périmètre (non-goals : suppression, édition, antivirus, rétention/purge).

## Architecture Overview

```mermaid
graph TD
    subgraph Frontend [frontend/src]
        ElementsList[ElementsList.vue] -->|type=ATTACHMENT: multipart| AddAttachment
        ElementsList -->|type=INFORMATION/NOTE/LINK: JSON| AddElement
        AddAttachment[api/elements.ts uploadAttachment] -->|POST multipart| CtrlAttach
        AddElement[api/elements.ts addElement] -->|POST JSON| CtrlAdd
    end

    subgraph WebLayer [infrastructure.web.dossier]
        CtrlAdd["DossierController<br/>POST /api/dossiers/{id}/elements"]
        CtrlAttach["DossierElementUploadController<br/>POST /api/dossiers/{id}/elements/attachment"]
        CtrlContent["DossierElementContentController<br/>GET /api/dossier-elements/{id}/content<br/>(infrastructure.web.document, existant)"]
    end

    subgraph Domain [domain.dossier]
        UC[AddDossierElementUseCaseImpl]
        Policy[DossierAccessPolicy<br/>existante, décision n°20]
        Model[DossierElement / ElementType<br/>+ ATTACHMENT/INFORMATION/NOTE/LINK]
    end

    subgraph Storage [infrastructure.storage — existant, réutilisé tel quel]
        Port[DocumentStoragePort]
        S3[S3StorageAdapter]
        FS[FilesystemStorageAdapter]
    end

    CtrlAdd --> UC
    CtrlAttach -->|store bytes d'abord| Port
    CtrlAttach --> UC
    UC --> Policy
    UC --> Model
    Port --> S3
    Port --> FS
    CtrlContent -->|retrieve| Port
    UC --> DB[(dossier_element<br/>varchar type, pas de CHECK)]
```

**Composants clés**

- **`DossierElementUploadController`** (nouveau, `web.dossier`) : seul point d'entrée
  `multipart/form-data`, dédié à `ATTACHMENT`. Écrit d'abord le fichier via
  `DocumentStoragePort.store(tenantId, contentType, bytes)`, obtient un `storageRef` opaque, puis
  appelle `AddDossierElementUseCase.add(...)` avec ce `storageRef` — même séquence que
  `AttachGeneratedDocumentTask` aujourd'hui pour `PRODUCED_DOC`.
- **`DossierController`** (existant, étendu) : nouvel endpoint JSON
  `POST /api/dossiers/{id}/elements` pour `INFORMATION` / `NOTE` / `LINK` — pas de fichier, pas
  de `storageRef`.
- **`AddDossierElementUseCaseImpl`** (existant, étendu) : la garde `Only PRODUCED_DOC is
  supported in V1` est remplacée par une validation par type (voir Design Decisions).
- **`DocumentStoragePort` / adaptateurs S3 / filesystem** : **inchangés**, réutilisés tels quels.
- **`DossierAccessPolicy`** : **inchangée**, réutilisée telle quelle (`assertCanEdit` pour
  ajouter, `assertCanView` pour lister/télécharger — déjà le cas pour `listElements` /
  `GetDossierElementUseCase`).

## Data Models

**`ElementType`** — extension de l'enum existant (`infrastructure` : colonne `varchar(32)` sans
`CHECK`, aucune migration Liquibase requise) :

```java
public enum ElementType {
    PRODUCED_DOC,  // existant, inchangé — ServiceTask uniquement
    ATTACHMENT,    // nouveau — upload utilisateur, storageRef renseigné
    INFORMATION,   // nouveau — bloc clé/valeur libre, storageRef null
    NOTE,          // nouveau — texte libre, storageRef null
    LINK           // nouveau — référence externe, storageRef null, jamais résolue serveur
}
```

`DossierElement` (record, inchangé structurellement) — usage du champ `metadata` déjà existant
par type :

| Type | `storageRef` | `metadata` (convention) |
|---|---|---|
| `PRODUCED_DOC` | requis (existant) | libre (existant, inchangé) |
| `ATTACHMENT` | requis — retourné par `DocumentStoragePort.store(...)` | `{"filename": string, "contentType": string, "sizeBytes": number}` |
| `INFORMATION` | `null` | objet clé/valeur libre fourni par l'appelant (plafonné en taille, voir NFR) |
| `NOTE` | `null` | `{"text": string}` (plafonné en longueur, voir NFR) |
| `LINK` | `null` | `{"url": string, "description": string?}` |

`DossierElementView.downloadable` (calcul existant, à généraliser) :

```java
boolean downloadable = (element.type() == ElementType.PRODUCED_DOC || element.type() == ElementType.ATTACHMENT)
        && element.storageRef() != null && !element.storageRef().isBlank();
```

Aucun changement de schéma `dossier_element` (table existante suffit : `type`, `storage_ref`
nullable, `metadata` jsonb déjà présents).

## API Design

**1. `POST /api/dossiers/{id}/elements`** (nouveau, JSON, `DossierController`) — `INFORMATION` /
`NOTE` / `LINK`.

```jsonc
// Request
{ "type": "NOTE", "label": "Rappel client", "metadata": { "text": "À relancer le 20/08" } }
// 201 Created → DossierElementView (même forme que GET .../elements)
```

Rejette `type=PRODUCED_DOC` et `type=ATTACHMENT` (400 — orientés vers leurs endpoints dédiés :
ServiceTask / upload multipart respectivement).

**2. `POST /api/dossiers/{id}/elements/attachment`** (nouveau, `multipart/form-data`, nouveau
`DossierElementUploadController`) — `ATTACHMENT` uniquement.

```
Content-Type: multipart/form-data
  file: <bytes>        (requis, borné par NFR)
  label: "Justificatif" (requis)
```

→ `201 Created`, corps `DossierElementView`. Rejette fichier absent, taille hors borne, type MIME
hors allowlist (`InvalidDossierElementException` → 400/413, voir Non-Functional Requirements).

**3. `GET /api/dossier-elements/{id}/content`** (existant, `DossierElementContentController`,
généralisé) : sert désormais `PRODUCED_DOC` **et** `ATTACHMENT`, avec le `Content-Type` et le nom
de fichier réels tirés de `metadata` pour `ATTACHMENT` (au lieu du `application/pdf` en dur
actuel, conservé uniquement pour `PRODUCED_DOC`).

**Authentification/autorisation** : session BFF existante (`Authentication` → `AccessContext.User`
via `AccessContextFactory`, inchangé). ACL : `DossierAccessPolicy.assertCanEdit` sur les deux
endpoints d'ajout, `assertCanView` sur lecture/liste/téléchargement (déjà le comportement pour
`PRODUCED_DOC`/`listElements`).

## Component Breakdown

**Backend**

- `domain.dossier.model.ElementType` — 4 nouvelles valeurs.
- `domain.dossier.usecase.AddDossierElementUseCaseImpl` — validation par type (remplace la garde
  `PRODUCED_DOC`-only).
- `domain.dossier.model.DossierElementView#from` — `downloadable` généralisé à `ATTACHMENT`.
- `infrastructure.web.dossier.DossierController` — nouvel endpoint JSON `POST .../elements`.
- `infrastructure.web.dossier.DossierElementUploadController` (nouveau) — endpoint multipart,
  orchestration `DocumentStoragePort.store` → `AddDossierElementUseCase.add`.
- `infrastructure.web.dossier.DossierExceptionHandler` — ajout du mapping
  `InvalidDossierElementException` → 400 (absent aujourd'hui pour ce package, seul
  `DocumentGenerationExceptionHandler` le gère actuellement côté `web.document`).
- `infrastructure.web.document.DossierElementContentController` — généralisation du
  `Content-Type`/nom de fichier servi, à partir de `metadata` pour `ATTACHMENT`.
- `infrastructure.config.DossierConfig` — pas de nouveau bean de use case (réutilisation de
  `AddDossierElementUseCase` déjà câblé) ; `DossierElementUploadController` injecte
  `DocumentStoragePort` déjà exposé par `StorageConfig`.
- Limite de taille multipart : propriétés Spring natives
  `spring.servlet.multipart.max-file-size` / `max-request-size` (pas de filtre dédié à écrire —
  contrairement à `DocumentGenerationBodySizeLimitFilter`/`WebhookBodySizeLimitFilter` qui
  protègent des corps JSON non-multipart ; Spring rejette nativement un multipart hors borne en
  413 via `MaxUploadSizeExceededException`, à mapper en `ProblemDetail` dans
  `DossierExceptionHandler`).
- Allowlist MIME : validation applicative dans `DossierElementUploadController` (liste de
  `contentType` acceptés, configurable via `@ConfigurationProperties`, cohérent avec
  `StorageProperties`).

**Frontend**

- `frontend/src/components/dossier/ElementsList.vue` — bouton "Ajouter" activé : sélecteur de
  type → sous-formulaire (file picker pour `ATTACHMENT`, champs dédiés pour les 3 autres).
- `frontend/src/api/elements.ts` — nouvelles fonctions `uploadAttachment(dossierId, file, label)`
  et `addElement(dossierId, command)`.
- `frontend/src/stores/dossier.ts` — action de rafraîchissement de la liste d'éléments après
  ajout (réutilise le pattern existant de fetch des éléments).
- `frontend/src/api/types.ts` — `ElementType` élargi côté TS, types de requête pour les deux
  nouveaux endpoints.

## Design Decisions

1. **Deux endpoints (JSON vs multipart) plutôt qu'un seul polymorphe.** Un unique
   `POST .../elements` acceptant à la fois JSON et multipart complexifierait le contrôleur
   (désérialisation conditionnelle) pour un gain nul — le FE connaît déjà le type choisi avant
   l'appel. Endpoint dédié `.../elements/attachment` pour le seul cas avec fichier binaire,
   symétrique à la séparation déjà en place entre `DossierController` (CRUD JSON) et
   `DossierElementContentController` (binaire).
2. **Réutilisation stricte de `DocumentStoragePort`/`DossierAccessPolicy`/`DossierElement`** —
   aucun nouveau port, aucune nouvelle table. Conforme à DEFERRED-FEATURES.md §0 (ne pas créer un
   second mécanisme de stockage) et à la décision n°20 (ACL par `DossierType`, pas par élément).
3. **Limite de taille via propriétés multipart Spring natives**, pas un filtre `Content-Length`
   maison — plus simple et plus fiable pour du multipart (les filtres existants
   `WebhookBodySizeLimitFilter`/`DocumentGenerationBodySizeLimitFilter` visaient des endpoints
   `@RequestBody` JSON exposés à un flux non authentifié/moins fiable ; ici l'endpoint est
   authentifié + CSRF, risque moindre, mais la borne reste nécessaire contre l'épuisement de
   ressources).
4. **`LINK` jamais résolu côté serveur.** Élimine par construction toute nouvelle surface SSRF —
   pas de `RestrictedResourceUserAgent`/`OutboundUrlGuard` à étendre à ce type. Seule une
   validation de forme (URI bien formée) est faite côté serveur.
5. **Pas de schéma de validation pour `INFORMATION`** (contrairement à `FieldDefinition` sur
   `DossierType`) — cohérent avec la nature "libre" documentée dans l'analyse doc ; seule une
   borne de taille anti-abus s'applique (NFR).

## Non-Functional Requirements

**Sécurité**

- Taille max upload `ATTACHMENT` : **25 Mo** par défaut (`app.storage.upload.max-size`,
  configurable) — cohérent avec les autres bornes déjà posées sur les endpoints exposés
  (TODO §30).
- Allowlist MIME par défaut : `application/pdf`, `image/png`, `image/jpeg`,
  `application/vnd.openxmlformats-officedocument.*` (Word/Excel/PowerPoint), `text/plain` —
  configurable, pas figée en dur dans le contrôleur.
- Antivirus : **explicitement hors scope** (TODO §30, non traité ici — ne pas régresser
  silencieusement sur ce point ouvert déjà documenté).
- `INFORMATION`/`NOTE` : plafond de taille du JSON `metadata` (ex. 8 Ko) pour éviter l'abus d'un
  endpoint sans limite de fichier — valeur à confirmer en implémentation, pas bloquante pour le
  design.
- Aucune extension de la matrice ACL existante (décision n°20) : les 4 nouveaux types suivent
  strictement `DossierAccessPolicy`.

**Performance**

- Pas d'exigence nouvelle : upload synchrone (même modèle que `PRODUCED_DOC`), taille bornée à
  25 Mo évite les temps de réponse dégradés.

**Fiabilité**

- Séquence `store` puis `add` (non transactionnelle entre les deux, comme pour
  `AttachGeneratedDocumentTask` aujourd'hui) : un échec de `AddDossierElementUseCase.add(...)`
  après un `store(...)` réussi laisse un objet orphelin dans le storage. **Risque déjà accepté
  pour `PRODUCED_DOC`** (même schéma) — pas de compensation transactionnelle introduite par cette
  feature ; un futur job de nettoyage relèverait de TODO §22 (purge), non traité ici.
