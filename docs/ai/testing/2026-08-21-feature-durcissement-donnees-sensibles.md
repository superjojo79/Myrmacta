---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
created: 2026-08-21
updated: 2026-08-21
feature: durcissement-donnees-sensibles
requirements_ref: docs/ai/requirements/2026-08-21-feature-durcissement-donnees-sensibles.md
design_ref: docs/ai/design/2026-08-21-feature-durcissement-donnees-sensibles.md
---

# Testing Strategy — `durcissement-donnees-sensibles`

> Backend uniquement (D-CSV-UI, Phase 3) — aucun changement `frontend/`,
> donc pas de scénario Playwright/Vitest requis pour cette feature.

## Test Coverage Goals

- Backend : Jacoco ≥ seuil projet sur le code **nouveau/modifié**
  (`./mvnw verify`).
- ArchUnit : `AttachmentScanPort`, `DossierExportPort`,
  `AttachmentContentSniffer` restent domaine pur (aucune annotation
  Spring/JPA) — couvert par les règles ArchUnit existantes, pas de règle
  nouvelle à écrire.
- OpenAPI golden (`openapi/openapi.yaml`) : diff CI doit inclure le nouvel
  endpoint `GET /api/dossiers/export.csv`.

## Unit Tests

### `AttachmentContentSniffer` (domaine pur)

- [ ] PDF valide (`%PDF-...`) déclaré `application/pdf` → match.
- [ ] PNG valide déclaré `image/png` → match.
- [ ] JPEG valide déclaré `image/jpeg` → match.
- [ ] DOCX/XLSX/PPTX valides (en-tête ZIP `PK\x03\x04`) déclarés avec le bon
      type OOXML → match.
- [ ] Fichier PNG renommé en `.pdf` (déclaré `application/pdf`, octets PNG)
      → pas de match.
- [ ] Fichier binaire quelconque déclaré `text/plain` → pas de match
      (octet nul détecté).
- [ ] Fichier texte légitime (UTF-8, pas d'octet nul) déclaré `text/plain`
      → match.
- [ ] Contenu vide (0 octet) → pas de match, pas d'exception.
- [ ] Type déclaré non reconnu par le sniffer (hors des 5 familles gérées)
      → comportement défini explicitement (à documenter : rejeté ou
      accepté par défaut) et testé.

### `AttachmentScanPort` / `NoOpAttachmentScanAdapter`

- [ ] `NoOpAttachmentScanAdapter.scan(...)` retourne toujours
      `AttachmentScanResult.clean()` quel que soit le contenu.

### `DossierElementUploadController` (upload durci)

- [ ] Upload avec type déclaré autorisé mais magic-bytes ne correspondant
      pas → 400, message distinct du rejet MIME existant.
- [ ] Upload avec `AttachmentScanPort` simulé "infecté" (double de test) →
      400, message incluant le motif du scan.
- [ ] Upload valide (type déclaré + magic-bytes cohérents + scan clean) →
      201, comportement inchangé vs avant la feature (non-régression).
- [ ] Le fichier n'est lu qu'une seule fois (`readBytes`) — pas de double
      lecture du `MultipartFile` (vérification par mock/spy si pertinent).

### `ExportDossiersUseCase` / `DossierExportPort`

- [ ] `dossierTypeCode` manquant → erreur de validation (400 côté
      contrôleur).
- [ ] `dossierTypeCode` inconnu pour le tenant → 400 ou 404 (à trancher en
      implémentation, cohérent avec le traitement existant d'un type
      inconnu ailleurs dans le code).
- [ ] Acteur non-admin sans VIEW sur le type demandé → `AccessDeniedException`
      (403).
- [ ] Acteur non-admin avec VIEW sur le type → export limité à ce type
      (même si `dossierTypeCode` correspond, pas de fuite d'autres types).
- [ ] `TENANT_ADMIN` / `WORKFLOW_ADMIN` → accès non restreint, cohérent
      avec `SearchDossiersUseCaseImpl.searchUnrestricted`.
- [ ] Filtres `status` / `reference` appliqués identiquement à la liste
      dossiers existante.
- [ ] Dossier avec un champ `sensitive` renseigné : colonne masquée
      (vide) pour un acteur non-admin, valeur en clair pour
      `TENANT_ADMIN` — même résultat que `DossierFieldRedactor` utilisé
      directement.
- [ ] `dossierTypeCode` sans aucun dossier correspondant → flux vide, pas
      d'erreur (juste l'en-tête CSV).
- [ ] Dossier avec un champ dynamique supprimé du `DossierType` depuis sa
      création → pas de crash, colonne absente pour ce dossier (valeur
      manquante gérée proprement).
- [ ] Le flux JPA (`getResultStream()`) est fermé même en cas d'exception
      pendant l'écriture (try-with-resources vérifié).

### `DossierExportController` / `CsvWriter`

- [ ] En-têtes CSV = colonnes fixes + une colonne par `FieldDefinition`
      dans l'ordre de `fieldDefinitions()`.
- [ ] Échappement RFC4180 : valeur contenant une virgule, un guillemet, ou
      un saut de ligne → correctement quotée/échappée.
- [ ] `Content-Type: text/csv; charset=UTF-8` et
      `Content-Disposition: attachment; filename="dossiers-<code>.csv"`
      présents sur la réponse.
- [ ] Champ `sensitive` masqué → cellule vide dans le CSV produit (pas de
      valeur, pas de `null` littéral).

### `HttpRestServiceConnector` (skeleton, tests directs uniquement)

- [ ] URL cible loopback/link-local/privée → `OutboundUrlGuard.assertSafe`
      lève avant tout appel HTTP (client HTTP jamais invoqué — vérifié par
      mock/spy).
- [ ] URL cible publique valide → guard passe, appel HTTP effectivement
      tenté via le client injecté (mocké dans le test).
- [ ] Résultat de `execute(...)` ne contient jamais de clé `*secret`/
      `*password` recopiée telle quelle depuis `instance.config()` (test
      de non-régression documentant la contrainte de conception D-SECRETS-AUDIT).
- [ ] `HttpRestServiceConnector` n'implémente pas `ConnectorDefinition`
      (vérification par réflexion ou simple assertion de type dans le
      test — garde contre une régression qui l'exposerait accidentellement
      au `ConnectorRegistry`).

### Garde-fou anti-fuite de secrets (D-SECRETS-AUDIT)

- [ ] `ConnectorApiDtos.ConnectorResponse` sérialisé à partir d'une
      `ConnectorInstanceData` avec un secret connu en clair dans le
      `config` d'entrée → la valeur en clair n'apparaît nulle part dans le
      JSON produit (uniquement `SECRET_MASK` + `secretConfigured=true`).
- [ ] `NotificationApiDtos.NotificationWebhookResponse` sérialisé à partir
      d'un `NotificationWebhookConfig` avec un `secretCiphertext` connu →
      la valeur déchiffrée n'apparaît jamais (le DTO transporte le
      ciphertext masqué, jamais le secret en clair — comportement déjà
      en place, test de non-régression).

## Integration Tests

- [ ] MockMvc : `POST /api/dossiers/{id}/elements/attachment` bout en
      bout avec un fichier dont les magic-bytes ne correspondent pas au
      type déclaré → 400.
- [ ] MockMvc : `GET /api/dossiers/export.csv?dossierTypeCode=...` bout en
      bout — `TENANT_ADMIN` voit les champs sensibles en clair,
      `DOSSIER_VIEWER` (sans droit sur le type) → 403,
      `DOSSIER_VIEWER` (avec droit) → 200 avec champs masqués.
- [ ] ArchUnit existant (`..domain..` sans Spring/JPA) reste vert avec les
      nouveaux types (`AttachmentScanPort`, `DossierExportPort`,
      `AttachmentContentSniffer`).
- [ ] OpenAPI golden : `$.paths['/api/dossiers/export.csv']` présent
      après régénération, diff CI sans écart non attendu.

## End-to-End Tests

Hors périmètre — aucun changement `frontend/` (D-CSV-UI, Phase 3). Aucun
scénario Playwright requis pour cette feature.

## Test Data

- Fichiers de test : un PDF valide, un PNG valide, un PNG renommé en
  `.pdf` (octets PNG, extension/déclaration PDF), un fichier texte UTF-8,
  un binaire arbitraire déclaré `text/plain`.
- Dossiers de test : un `DossierType` avec au moins un `FieldDefinition`
  `sensitive=true` et un `sensitive=false`, plusieurs dossiers dont un
  avec le champ sensible renseigné et un sans.
- Secrets de test : une valeur secrète connue injectée dans un
  `ConnectorInstanceData`/`NotificationWebhookConfig` de test pour le
  garde-fou anti-fuite (jamais un vrai secret).

## Test Reporting & Coverage

```bash
./mvnw verify -Dmaven.compiler.release=21
```

Pas de commande frontend requise (D-CSV-UI : aucun changement `frontend/`).

Evidence Phase 8 : à coller ici après implémentation (sortie fraîche de
`./mvnw verify`, nombre de tests, statut Jacoco).

## Manual Testing

- Ouvrir `GET /api/dossiers/export.csv?dossierTypeCode=...` directement
  dans un navigateur authentifié (session BFF) et vérifier le
  téléchargement + le contenu masqué selon le rôle.
- Tenter un upload de fichier renommé (ex. `.exe` renommé `.pdf`) via
  l'UI existante et vérifier le rejet.

## Performance Testing

Hors lot explicite (pas d'objectif de débit chiffré pour l'export, cf.
design Non-Functional Requirements). Vérification qualitative seule :
l'export d'un grand nombre de dossiers ne doit pas faire grimper la
mémoire JVM de façon linéaire avec le volume (stream, pas de
chargement intégral).

## Bug Tracking

Écarts vs les critères de succès requirements = bloquants avant Phase 9.
Toute fuite de secret détectée par le garde-fou = bloquant immédiat, pas
de contournement (pas de skip de test).
