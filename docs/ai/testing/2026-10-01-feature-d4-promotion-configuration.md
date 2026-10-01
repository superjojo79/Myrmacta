---
phase: testing
title: Tests — D4 (promotion de configuration)
---

- Domaine : `ConfigurationPromotionUseCaseTest` (conflit jamais forcé, inchangé, nom/contenu discordants, dépendance DMN en brouillon, BPMN sans configuration, configuration jointe ou directe, échec isolé, paramètre manquant, secret absent du journal, journal tolérant, droits, invariants du bundle).
- Archive : `ConfigurationBundleCodecTest` (aller-retour déterministe, JSON canonique et strict, altération, chemins hostiles, fichier non déclaré / manquant / format inconnu, fichier trop gros, doublon).
- API : `ConfigurationPromotionApiTest` (environnement neuf : plan, création, brouillons IMPORT, rejouer converge, publication puis tout « inchangé » ; export sans secret ; bundle portant un secret refusé ; archive altérée ; somme différente ; droits ; canal MCP restreint).
- MCP : `ConfigurationPromotionToolsTest`, `McpToolModesTest`, `McpEndpointApiTest`.
- Frontend : `configuration.spec.ts`, `ConfigurationPromotionView.spec.ts` ; e2e `E2E-PROMO-1`.
