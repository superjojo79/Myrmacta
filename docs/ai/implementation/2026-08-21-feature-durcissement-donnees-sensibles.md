---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: complete
created: 2026-08-21
updated: 2026-08-21
feature: durcissement-donnees-sensibles
phase5: 2026-08-21
phase7: 2026-08-21
phase9: 2026-08-21
planning_ref: docs/ai/planning/2026-08-21-feature-durcissement-donnees-sensibles.md
---

# Implementation Guide — `durcissement-donnees-sensibles`

> Phase 5 **done**, Phase 7 **done** (injection de formule CSV trouvée et
> corrigée), Phase 9 (revue finale) **done** — aucun blocage. `./mvnw verify`
> 964 tests (0 échec) + Jacoco OK ; Vitest 136 + Playwright 39 (feature
> backend-only, régression complète explicitement demandée). PR #62 prête
> pour revue humaine (CI indisponible : crédits GitHub Actions épuisés,
> confirmé hors de notre contrôle, suite validée localement).

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
  échappement RFC4180 minimal, aucune dépendance externe. **Corrigé en
  Phase 7** : neutralisation d'injection de formule CSV (préfixe `'` sur
  toute cellule commençant par `= + - @` ou tabulation) — `label` et les
  champs dynamiques exportés sont saisis par l'utilisateur, un tableur
  ouvrant le CSV évaluerait sinon une valeur comme `=cmd|calc!A0` comme
  formule. Effet de bord attendu et documenté : une valeur numérique
  négative légitime (ex. `-42`) apparaît préfixée d'une apostrophe dans le
  CSV brut — comportement standard de cette mitigation OWASP, pas une
  régression.
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
- M2 (trouvé et corrigé en Phase 7) : injection de formule CSV — voir
  `CsvWriter` ci-dessus.

## Phase 7 — Check Implementation (2026-08-21)

Revue fichier par fichier de tout le diff M1–M5 contre le design
(`docs/ai/design/2026-08-21-feature-durcissement-donnees-sensibles.md`) et
les requirements.

**Alignement :** conforme sur les 4 lots — aucun écart entre le design et
le code livré (ports, contrôleurs, use cases, wiring `DossierConfig`, tous
tels que spécifiés en Phase 3/4).

**Écart trouvé (sécurité, corrigé avant clôture de la phase) :**
`CsvWriter` n'avait pas de neutralisation d'injection de formule CSV —
`label` et les champs dynamiques exportés sont saisis par l'utilisateur,
donc une valeur comme `=cmd|calc!A0` aurait été évaluée comme formule par
un tableur ouvrant l'export. Ni les requirements ni le design ne
l'avaient anticipé. Corrigé en TDD (`CsvWriterTest` +4 cas) : préfixe `'`
sur toute cellule commençant par `= + - @` ou tabulation, avant
l'échappement RFC4180. Design/testing docs mis à jour rétroactivement.

**Pas d'autre écart, gap logique ou lacune de test identifié** lors de la
relecture des points suivants : ordre ACL/statut/scan de
`DossierElementUploadController` ; frontière transactionnelle de
`DossierExportController` (aucun `@Transactional` sur l'adaptateur/use
case, conforme au design) ; non-atteignabilité structurelle de
`HttpRestServiceConnector` (double garde vérifiée par test) ; sûreté du
`Content-Disposition` (le `dossierTypeCode` qui y figure a nécessairement
passé le pattern `DossierType.CODE_PATTERN` à la création, sinon
`typeRepository.findByCode` aurait déjà renvoyé 404 avant d'atteindre
l'en-tête — pas de risque d'injection d'en-tête HTTP).

**Verify Phase 7 :** `./mvnw -Dmaven.compiler.release=21 verify` → **964**
tests (+4 vs Phase 5, `CsvWriterTest`), 0 échec, Jacoco OK. Pas de
changement `frontend/` dans le correctif, pas de re-run Vitest/Playwright
nécessaire (déjà verts en Phase 5 sur un état sans risque frontend).

Next : `dev-testing` (Phase 8, déjà couvert en pratique par le testing doc
tenu à jour au fil de l'eau) puis `dev-review` (Phase 9).

## Phase 9 — Revue finale (2026-08-21)

Revue holistique (au-delà du fichier-par-fichier de Phase 7) :

- **Traçage des appelants** de chaque frontière publique modifiée :
  `DossierElementUploadController` (constructeur changé — seul
  `DossierElementUploadControllerTest` l'instancie directement, déjà mis à
  jour ; le reste passe par le component-scan Spring) ;
  `DossierListQueryParser.parseStatus` (2 appelants : `parse()` existant,
  `DossierExportController` nouveau) ; `AttachmentScanPort` /
  `DossierExportPort` / `ExportDossiersUseCase` (chacun un seul
  implémenteur, un seul point de câblage dans `DossierConfig`, pas de
  double enregistrement de bean).
- **Cohérence avec des modules similaires** : `JpaDossierExportAdapter`
  suit le pattern `@Component` de `JpaDossierSearchAdapter`/
  `JpaDossierRepositoryAdapter` ; `NoOpAttachmentScanAdapter` suit le
  pattern `@Bean` manuel de `FilesystemStorageAdapter`/`S3StorageAdapter`
  (pas de `@Component`, câblage explicite dans `DossierConfig`).
- **Réutilisation** : aucune utilité CSV préexistante dans le dépôt
  (vérifié par grep) — `CsvWriter` n'est pas une duplication.
- **Dépendances** : `pom.xml` inchangé (aucune nouvelle dépendance ajoutée,
  conforme au design).
- **Rollback** : aucune migration Liquibase dans ce lot — rien
  d'irréversible à risque.
- **Breaking changes** : le seul changement de signature publique
  (constructeur `DossierElementUploadController`) est interne au dépôt,
  tous les appelants mis à jour dans le même commit.

**Checklist finale :**
- [x] Correspondance design ↔ implémentation (Phase 7 + Phase 9)
- [x] Aucun gap logique restant
- [x] Sécurité traitée (magic-bytes, garde-fou secrets, injection CSV
      trouvée et corrigée)
- [x] Points d'intégration vérifiés (wiring `DossierConfig`, pas de bean
      dupliqué)
- [x] Tests couvrent les changements (964 backend, régression frontend
      complète)
- [x] Docs à jour (requirements/design/planning/implementation/testing,
      TODO.md, FILE-ATTENTE.md)

**Aucun blocage.** Feature prête — PR #62 déjà ouverte et à jour
(commit `284345c`). CI GitHub Actions indisponible pour raison externe
(crédits épuisés, confirmé par l'utilisateur) — n'empêche pas la revue
humaine ni le merge une fois les crédits reconstitués.

## Phase 8 — Écriture de tests / couverture réelle (2026-08-21)

Exécutée après la Phase 9 à la demande explicite de l'utilisateur (« joue
quand même la phase 8 ») — la mention « Phase 8 » dans le testing doc en
fin de Phase 5 citait un total global sans relecture ligne par ligne.
Reprise complète via `dev-testing` : génération du rapport Jacoco
(`target/site/jacoco/jacoco.csv` + HTML) et relecture classe par classe de
tous les fichiers créés/modifiés par la feature.

**2 lacunes réelles trouvées et comblées** (voir le testing doc pour le
détail ligne par ligne) :
- `HttpRestServiceConnector` : chemin d'erreur (`url` absent,
  `IOException`/`InterruptedException` wrappés) totalement non testé — 3
  tests ajoutés, couverture désormais 100 % lignes/branches sur cette
  classe.
- `AttachmentContentSniffer` : cas contenu vide déclaré `text/plain` non
  testé (chemin `looksLikeText`, distinct du chemin PDF déjà couvert) — 1
  test ajouté.
- `CsvWriter` : 1 test ajouté pour couvrir explicitement la branche chaîne
  vide de la neutralisation anti-formule.

**1 lacune identifiée, volontairement non comblée** : `DossierElementUploadController.readBytes`
(`catch IOException`) — méthode non modifiée par cette feature (vérifié
par `git diff origin/main...HEAD`), hors périmètre.

**Verify Phase 8 :** `./mvnw -Dmaven.compiler.release=21 verify` → **969**
tests (+5 vs les 964 de fin de Phase 7), 0 échec, Jacoco OK. Pas de
changement `frontend/`, pas de nouveau run Vitest/Playwright nécessaire.
