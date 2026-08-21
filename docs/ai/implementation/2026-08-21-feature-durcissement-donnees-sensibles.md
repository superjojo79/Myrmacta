---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: complete
created: 2026-08-21
updated: 2026-08-21
feature: durcissement-donnees-sensibles
phase5: 2026-08-21
planning_ref: docs/ai/planning/2026-08-21-feature-durcissement-donnees-sensibles.md
---

# Implementation Guide — `durcissement-donnees-sensibles`

> Phase 5 **done** — M1–M5 livrés en TDD strict. `./mvnw verify` 960 tests
> (0 échec) + Jacoco OK ; Vitest 136 + Playwright 39 (feature backend-only,
> régression complète explicitement demandée).

## Development Setup

- `./mvnw -q -Dmaven.compiler.release=21 -Dtest=<Test> test` pour un test ciblé.
- `./mvnw verify -Dmaven.compiler.release=21` pour la suite complète en fin de lot.

## Code Structure

- Domaine pur : `com.flowforge.domain.dossier` (sniffer, redactor réutilisé),
  `com.flowforge.domain.dossier.port.out` (nouveaux ports).
- Infra : `com.flowforge.infrastructure.dossier` (adaptateurs simples),
  `com.flowforge.infrastructure.web.dossier` (contrôleurs), `infrastructure.config`
  (câblage `@Bean`).

## Implementation Notes

### M1 — Upload durci (done)

- `AttachmentContentSniffer` (`domain.dossier`, statique pur) : signatures
  magic-bytes PDF/PNG/JPEG/ZIP(OOXML), heuristique "pas d'octet nul" pour
  `text/plain`. Type déclaré non couvert par une signature connue → passe
  (le sniffer complète l'allowlist déclarative, ne la remplace pas).
- `AttachmentScanResult` (record, `domain.dossier.port.out`) : les
  factory-methods s'appellent `ofClean()`/`ofInfected(reason)` (et non
  `clean()`/`infected(...)`) pour éviter une collision de nom avec
  l'accesseur généré du composant `clean` du record — un `clean()`
  statique et un `clean()` d'instance ne peuvent pas coexister (erreur de
  compilation « invalid accessor method »).
- `AttachmentScanPort` (`domain.dossier.port.out`) + `NoOpAttachmentScanAdapter`
  (`infrastructure.dossier`) : toujours propre en V1, câblé en `@Bean` dans
  `DossierConfig` (pattern déjà utilisé pour tous les adaptateurs de ce
  module, pas de `@Component` direct).
- `DossierElementUploadController` : nouvelle dépendance constructeur
  `AttachmentScanPort` (7ᵉ paramètre) ; ordre dans `upload(...)` : ACL/statut
  (inchangé) → allowlist MIME déclarée (inchangé) → **lecture unique des
  bytes** → sniffer → scan → store. Aucun test existant cassé : les tests
  positifs utilisaient déjà des bytes `%PDF-...` valides, les tests négatifs
  échouent tous avant d'atteindre le sniffer (ACL/statut/vide).
- Tests : `AttachmentContentSnifferTest` (9), `NoOpAttachmentScanAdapterTest`
  (2), `DossierElementUploadControllerTest` (+3, total 5),
  `DossierElementUploadApiTest` (+1, total 5).

### M2 — Export CSV masqué (done)

- `DossierExportPort` (`domain.dossier.port.out`) + `JpaDossierExportAdapter`
  (`infrastructure.persistence.dossier`, `@Component`) : Criteria API +
  `getResultStream()`, réutilise `DossierSpecifications.forSearch` déjà
  utilisé par `JpaDossierSearchAdapter`. **Aucun `@Transactional` sur
  l'adaptateur** (documenté dans son javadoc) : l'annoter aurait ouvert et
  commité sa propre transaction avant que l'appelant ne consomme le flux,
  invalidant le curseur JDBC sous-jacent. La frontière transactionnelle est
  posée sur `DossierExportController.exportCsv` (seul endroit autorisé à
  porter du Spring dans la chaîne domaine → infra).
- `ExportDossiersQuery` (`domain.dossier.model`) : `dossierTypeCode`
  obligatoire (contrairement à `SearchDossiersQuery` où il est optionnel).
- `ExportDossiersUseCase`/`Impl` : réutilise **`DossierAccessPolicy.assertCanViewType`**
  plutôt que de dupliquer la logique ACL manuelle de
  `SearchDossiersUseCaseImpl` (plus simple car `dossierTypeCode` est un
  seul type obligatoire ici, pas un filtre optionnel multi-type) ;
  `DossierNotFoundException`
  (404) pour un type inconnu, même convention que
  `CreateDossierUseCaseImpl`.
- `DossierExportController` (nouveau, dédié — même raison que
  `DossierElementUploadController` pour l'upload : réponse non-JSON) sur
  `GET /api/dossiers/export.csv`. Param `dossierTypeCode` (et non
  `dossierTypeId`) pour rester cohérent avec le nom de filtre déjà utilisé
  par `GET /api/dossiers`.
- `DossierListQueryParser.parseStatus(...)` extrait en méthode
  package-private réutilisable (évite de dupliquer le parsing de
  `DossierStatus` entre les deux contrôleurs).
- `CsvWriter` (`infrastructure.web.dossier`, package-private) :
  échappement RFC4180 minimal, aucune dépendance externe.
- OpenAPI golden régénéré via `-Dopenapi.update=true` sur `OpenApiContractIT` ;
  springdoc documente correctement l'endpoint malgré son retour `void`
  (réponse `200` générique, sans schéma de corps — acceptable, pas de
  contrat JSON à documenter pour un flux CSV).
- Tests : `DossierExportPersistenceTest` (3), `ExportDossiersQueryTest`
  (4), `ExportDossiersUseCaseImplTest` (6), `CsvWriterTest` (6),
  `DossierExportApiTest` (5).

### M3 — Skeleton `ServiceConnector` `http-rest` (done)

- `HttpRestServiceConnector` (`infrastructure.connector.builtin`) implémente
  seulement `ServiceConnector` — pas `@Component`, pas `ConnectorDefinition`.
  Vérifié concrètement : `ExecuteConnectorUseCaseImpl` route via
  `catalog.get(instance.connectorType())` puis `instanceof ServiceConnector`
  sur le bean **catalogué**, qui reste `HttpRestTriggerConnector`
  (n'implémente pas `ServiceConnector`) — donc structurellement
  inatteignable, pas juste par convention de nommage.
- Contrat `execute(...)` : lève `IllegalArgumentException` si `url` absent
  des `inputParams`, appelle `OutboundUrlGuard.assertSafe(url)` avant toute
  requête, retourne uniquement `{status, body}` — jamais `instance.config()`.
- Tests : `HttpRestServiceConnectorTest` (5), instanciation directe
  uniquement (mock `HttpClient`), aucun test Spring-context nécessaire.

### M4 — Audit + garde-fou secrets (done)

- Re-vérification post M1-M3 : aucune des surfaces identifiées en Phase 3
  n'a été touchée par les lots précédents (M1/M2/M3 ne manipulent aucun
  secret de connecteur/webhook) — verdict inchangé, toujours sain.
- `ConnectorResponseSecretMaskingTest` (`infrastructure.web.connector`,
  même package que le record package-private `ConnectorResponse`) : sérialise
  via Jackson `ObjectMapper` nu, vérifie l'absence du secret connu dans le
  JSON produit (2 cas : clé `webhookSecret`, clé `password`).
- `NotificationWebhookResponseSecretMaskingTest`
  (`infrastructure.web.notification`) : même mécanisme, couvre le pire cas
  (`secretCiphertext` transitoire en plaintext avant chiffrement par la
  persistence, cf. javadoc `NotificationWebhookConfig`) — nécessite
  d'enregistrer `JavaTimeModule` sur l'`ObjectMapper` nu (le mapper Spring
  configuré n'est pas utilisé ici, test unitaire pur sans contexte Spring).
- Ces deux tests sont volontairement des tests de non-régression (pas de
  nouveau code de production associé — le masquage existait déjà) :
  cohérent avec la portée « garde-fou » du lot, pas une nouvelle
  fonctionnalité.

### M5 — Qualité (done)

- ArchUnit (`ArchitectureRulesTest`) vert sans modification — les nouveaux
  types domaine (`AttachmentScanPort`, `DossierExportPort`,
  `AttachmentContentSniffer`, `ExportDossiersQuery`,
  `ExportDossiersUseCase`) sont déjà couverts par les règles existantes.
- `./mvnw -Dmaven.compiler.release=21 verify` → **960** tests, 0 échec,
  Jacoco OK. `cd frontend && npm run test` → 136 tests. `cd frontend &&
  PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/pw-browsers/chromium npm run test:e2e`
  → 39 tests (voir note environnement dans le testing doc — pas de
  modification de `playwright.config.ts`, la variable d'env était déjà
  prévue).
- `TODO.md` : §23 reste réduit (masquage export livré, logs/webhooks
  sortants restent ouverts) ; §30 **clos** (`[x]`), décision Redis
  documentée comme non-action assumée.
- `FILE-ATTENTE.md` daté avec l'évidence Phase 5 ; feature reste en cours
  (pas encore déplacée vers « Récemment livré » — attend Phase 9 / PR
  mergeable, cohérent avec le pattern des lots précédents).

## Integration Points

- `AttachmentScanPort` est un point d'extension pour un futur moteur AV —
  aucun autre composant n'en dépend.
- `DossierExportPort` est consommé uniquement par `ExportDossiersUseCaseImpl` ;
  aucun autre appelant.

## Error Handling

- Rejets M1 : `InvalidDossierElementException` (400, déjà mappée par
  `DossierExceptionHandler`) — pas de nouvelle classe d'exception, message
  distinct par cause (type non autorisé / signature invalide / scan
  infecté).

## Performance Considerations

- M1 : une seule lecture des bytes du `MultipartFile` (`readBytes` appelé
  une fois, réutilisé pour sniffer + scan + store).

## Security Notes

- M1 : le scan reste no-op en V1 (documenté), la vraie protection actuelle
  vient du sniffer magic-bytes + de l'allowlist MIME existante.
