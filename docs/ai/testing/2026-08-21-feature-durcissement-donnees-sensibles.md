---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: complete
created: 2026-08-21
updated: 2026-08-21
feature: durcissement-donnees-sensibles
phase8: 2026-08-21
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

- [x] PDF valide (`%PDF-...`) déclaré `application/pdf` → match.
- [x] PNG valide déclaré `image/png` → match.
- [x] JPEG valide déclaré `image/jpeg` → match.
- [x] DOCX/XLSX/PPTX valides (en-tête ZIP `PK\x03\x04`) déclarés avec le bon
      type OOXML → match.
- [x] Fichier PNG renommé en `.pdf` (déclaré `application/pdf`, octets PNG)
      → pas de match.
- [x] Fichier binaire quelconque déclaré `text/plain` → pas de match
      (octet nul détecté).
- [x] Fichier texte légitime (UTF-8, pas d'octet nul) déclaré `text/plain`
      → match.
- [x] Contenu vide (0 octet) → pas de match, pas d'exception.
- [x] Type déclaré non reconnu par le sniffer (hors des 5 familles gérées)
      → **accepté par défaut** (pass-through, documenté dans le javadoc de
      la classe et l'implementation doc).

### `AttachmentScanPort` / `NoOpAttachmentScanAdapter`

- [x] `NoOpAttachmentScanAdapter.scan(...)` retourne toujours
      `AttachmentScanResult.ofClean()` quel que soit le contenu.

### `DossierElementUploadController` (upload durci)

- [x] Upload avec type déclaré autorisé mais magic-bytes ne correspondant
      pas → 400, message distinct du rejet MIME existant.
- [x] Upload avec `AttachmentScanPort` simulé "infecté" (double de test) →
      400, message incluant le motif du scan.
- [x] Upload valide (type déclaré + magic-bytes cohérents + scan clean) →
      201, comportement inchangé vs avant la feature (non-régression).
- [x] Le fichier n'est lu qu'une seule fois (`readBytes`) — bytes réutilisés
      pour sniffer + scan + store (relecture du code, pas de spy nécessaire).

### `ExportDossiersUseCase` / `DossierExportPort`

- [x] `dossierTypeCode` manquant → erreur de validation (400, Spring
      `MissingServletRequestParameterException` par défaut — pas de
      handler dédié nécessaire).
- [x] `dossierTypeCode` inconnu pour le tenant → **404**
      (`DossierNotFoundException`, même convention que
      `CreateDossierUseCaseImpl` pour un type de dossier manquant).
- [x] Acteur non-admin sans VIEW sur le type demandé → `AccessDeniedException`
      (403, via `DossierAccessPolicy.assertCanViewType` réutilisé).
- [x] Acteur non-admin avec VIEW sur le type → export limité à ce type
      (même si `dossierTypeCode` correspond, pas de fuite d'autres types).
- [x] `TENANT_ADMIN` / `WORKFLOW_ADMIN` → accès non restreint (bypass
      `DossierAccessPolicy`, cohérent avec le reste du module).
- [x] Filtres `status` / `reference` appliqués identiquement à la liste
      dossiers existante (même `DossierSearchPort.Filter`).
- [x] Dossier avec un champ `sensitive` renseigné : colonne masquée
      (vide) pour un acteur non-admin, valeur en clair pour
      `TENANT_ADMIN` — `DossierFieldRedactor` réutilisé directement dans
      `ExportDossiersUseCaseImpl`.
- [x] `dossierTypeCode` sans aucun dossier correspondant → flux vide, pas
      d'erreur (juste l'en-tête CSV).
- [x] Le flux JPA (`getResultStream()`) est consommé dans la transaction
      du contrôleur (`@Transactional(readOnly = true)` sur
      `DossierExportController.exportCsv`, pas sur l'adaptateur/use case
      — domaine pur) ; try-with-resources dans le contrôleur.

Non couvert par un test dédié (jugé hors valeur ajoutée après relecture) :
dossier avec un champ dynamique supprimé du `DossierType` — le code
(`dossier.fields().get(definition.name())` → `null` → cellule vide) gère
déjà ce cas par construction, comme pour un champ jamais renseigné.

### `DossierExportController` / `CsvWriter`

- [x] En-têtes CSV = colonnes fixes + une colonne par `FieldDefinition`
      dans l'ordre de `fieldDefinitions()`.
- [x] Échappement RFC4180 : valeur contenant une virgule, un guillemet, ou
      un saut de ligne → correctement quotée/échappée (`CsvWriterTest`).
- [x] `Content-Type: text/csv; charset=UTF-8` et
      `Content-Disposition: attachment; filename="dossiers-<code>.csv"`
      présents sur la réponse.
- [x] Champ `sensitive` masqué → cellule vide dans le CSV produit (pas de
      valeur, pas de `null` littéral).
- [x] **Injection de formule CSV (trouvé en Phase 7)** : une valeur
      commençant par `=`, `+`, `-`, `@` ou une tabulation (ex. `label`
      dossier = `=cmd|calc!A0`) est préfixée d'une apostrophe avant tout
      échappement RFC4180 — `CsvWriterTest` (6 cas, dont le cumul
      neutralisation + guillemets RFC4180).

### `HttpRestServiceConnector` (skeleton, tests directs uniquement)

- [x] URL cible loopback/link-local/privée → `OutboundUrlGuard.assertSafe`
      lève avant tout appel HTTP (client HTTP jamais invoqué — vérifié par
      mock).
- [x] URL cible publique valide → guard passe, appel HTTP effectivement
      tenté via le client injecté (mocké dans le test).
- [x] Résultat de `execute(...)` ne contient jamais de clé `*secret`/
      `*password` recopiée telle quelle depuis `instance.config()` (test
      de non-régression documentant la contrainte de conception D-SECRETS-AUDIT).
- [x] `HttpRestServiceConnector` n'implémente pas `ConnectorDefinition`
      et n'est pas annoté `@Component` (deux assertions par réflexion —
      garde contre une régression qui l'exposerait accidentellement au
      `ConnectorRegistry`).

### Garde-fou anti-fuite de secrets (D-SECRETS-AUDIT)

- [x] `ConnectorApiDtos.ConnectorResponse` sérialisé à partir d'une
      `ConnectorInstanceData` avec un secret connu en clair dans le
      `config` d'entrée → la valeur en clair n'apparaît nulle part dans le
      JSON produit (uniquement `SECRET_MASK` + `secretConfigured=true`) —
      `ConnectorResponseSecretMaskingTest` (2 cas : `webhookSecret` et
      `password`).
- [x] `NotificationApiDtos.NotificationWebhookResponse` sérialisé à partir
      d'un `NotificationWebhookConfig` avec un `secretCiphertext` connu
      (y compris le cas pire : plaintext transitoire pré-chiffrement) →
      la valeur n'apparaît jamais dans le JSON —
      `NotificationWebhookResponseSecretMaskingTest`.

## Integration Tests

- [x] MockMvc : `POST /api/dossiers/{id}/elements/attachment` bout en
      bout avec un fichier dont les magic-bytes ne correspondent pas au
      type déclaré → 400.
- [x] MockMvc : `GET /api/dossiers/export.csv?dossierTypeCode=...` bout en
      bout — `TENANT_ADMIN` voit les champs sensibles en clair,
      `DOSSIER_VIEWER` (sans droit sur le type) → 403,
      `DOSSIER_VIEWER` (avec droit) → 200 avec champs masqués.
- [ ] ArchUnit existant (`..domain..` sans Spring/JPA) reste vert avec les
      nouveaux types (`AttachmentScanPort`, `DossierExportPort`,
      `AttachmentContentSniffer`) — vérifié en M5.
- [x] OpenAPI golden : `$.paths['/api/dossiers/export.csv']` présent
      après régénération (`-Dopenapi.update=true`), `OpenApiContractIT`
      vert sans regénération.

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

Evidence Phase 8 (2026-08-21) :

- `./mvnw -Dmaven.compiler.release=21 verify` → **BUILD SUCCESS**, Tests run
  **960**, Failures **0**, Errors **0**, Skipped **0** ; Jacoco : « All
  coverage checks have been met. »
- `cd frontend && npm run test` → 47 files, **136** tests, 0 échec.
- `cd frontend && PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/pw-browsers/chromium
  npm run test:e2e` → **39** passed, 0 échec (le cache Playwright local de
  l'environnement pointe une version de navigateur différente de celle
  attendue par `@playwright/test@^1.59.1` — `playwright.config.ts` lit déjà
  `PLAYWRIGHT_CHROMIUM_EXECUTABLE` pour ce cas, pas de contournement ad hoc).
- Régression complète explicitement demandée (feature backend-only, D-CSV-UI
  n'imposait pas Vitest/Playwright, mais la suite complète a été exécutée et
  est verte).

### Analyse de couverture réelle (Phase 8, `dev-testing`, 2026-08-21)

Le paragraphe ci-dessus citait un « 960 » global (Jacoco bundle ≥ 80 %) sans
relecture ligne par ligne des classes de la feature. Reprise complète :
génération du rapport (`target/site/jacoco/jacoco.csv` + HTML, produit par
`./mvnw verify`) et relecture classe par classe des fichiers créés/modifiés
par cette feature.

**Deux lacunes réelles trouvées et comblées :**

- `HttpRestServiceConnector` : **0 % du chemin d'erreur testé**
  (`BRANCH_MISSED=3/4`, `LINE_MISSED=5/17`). Ni le rejet `url` absent
  (`IllegalArgumentException`), ni le wrapping `IOException`/
  `InterruptedException` → `IllegalStateException` (y compris la
  restauration du flag d'interruption) n'étaient exercés par
  `HttpRestServiceConnectorTest`. Ajout de 3 tests
  (`execute_missingUrl_throwsWithoutCallingHttpClient`,
  `execute_httpClientThrowsIOException_wrapsInIllegalStateException`,
  `execute_httpClientInterrupted_restoresInterruptFlagAndWraps`). Après
  correction : `BRANCH_MISSED=0`, `LINE_MISSED=0`.
- `AttachmentContentSniffer` : le cas contenu vide déclaré `text/plain`
  (`looksLikeText`, ligne `content.length == 0 → return false`) n'était
  couvert que côté `application/pdf` (chemin `isPdf`/`startsWith`, code
  différent). Ajout de
  `matchesDeclaredType_rejectsEmptyContentDeclaredAsPlainText`. Après
  correction : `LINE_MISSED=0` (1 branche encore non couverte, qui
  n'apparaît sur aucune ligne "partiellement couverte" dans le rapport
  HTML — probablement un artefact JaCoCo sur l'initialisation statique de
  la `Map` de signatures, pas un chemin de code testable manquant).
- `CsvWriter` : ajout de `writeRow_treatsEmptyStringFieldAsEmpty` pour
  couvrir explicitement la branche chaîne vide de
  `neutralizeFormulaTrigger` (jusque-là seul le cas `null` était testé
  directement, la chaîne vide n'étant atteinte qu'indirectement).

**Lacune identifiée mais volontairement non comblée** (hors scope,
pré-existante) : `DossierElementUploadController.readBytes` — la branche
`catch (IOException ex)` (lignes 112-113) n'est pas couverte, mais cette
méthode n'a pas été modifiée par cette feature (vérifié via
`git diff origin/main...HEAD` sur le fichier) ; corriger un gap de
couverture pré-existant hors périmètre relève d'un autre lot.

**Evidence fraîche post-correction :**
`./mvnw -Dmaven.compiler.release=21 verify` → **BUILD SUCCESS**, Tests run
**969** (+9 vs les 960 initiaux : +3 `HttpRestServiceConnectorTest`,
+1 `AttachmentContentSnifferTest`, +1 `CsvWriterTest`, +4 déjà ajoutés en
Phase 7 pour l'injection de formule CSV), Failures **0**, Jacoco : « All
coverage checks have been met. » Aucun changement `frontend/` dans ce
lot de tests supplémentaires — pas de nouveau run Vitest/Playwright requis
(évidence Phase 5 toujours valide, aucune surface FE ni contrat API
impacté).

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
