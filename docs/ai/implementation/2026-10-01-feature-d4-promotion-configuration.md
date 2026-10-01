---
phase: implementation
title: Implémentation — D4 (promotion de configuration)
---

# Implémentation (TODO §83)

- Domaine `domain/promotion` : `ConfigurationBundle` (invariants : format, doublons, tailles), `BundleEntry` (clé sûre, SHA-256), `ArtifactType` (ordre d'application), documents portables `ConfigDocs`, `ImportPlan`/`ImportReport`, `ExportConfigurationUseCaseImpl`, `ImportConfigurationUseCaseImpl`.
- Infrastructure : `JsonArtifactCodec` (JSON canonique, lecture stricte), `ZipConfigurationBundleCodec` (écriture déterministe, lecture défensive), `ConfigImportJpaEntity` + migration 032, `PromotionConfig`.
- Web : `ConfigurationPromotionController` (`/api/admin/configuration`, TENANT_ADMIN), `PromotionExceptionHandler`.
- MCP : `ExportConfigurationTool`, `ImportConfigurationTool` (plan par défaut ; l'application ne dépose que des brouillons BPMN/DMN) ; `McpToolModes` mis à jour.
- Provenance `IMPORT` ajoutée aux brouillons (D6) ; SPA `ConfigurationPromotionView`, route `/admin/configuration`, lien « Promotion ».
- Décisions d'implémentation : connecteur créé désactivé, paramètres `{{nom}}`, configuration de workflow jointe au brouillon BPMN, intégrité sans signature (voir D-PROMO-* dans DECISIONS.md).
