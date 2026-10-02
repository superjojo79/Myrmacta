---
phase: testing
title: Testing — D7 internationalisation de l'interface
status: done
created: 2026-10-01
feature: i18n-interface
lot: D7
todo_ref: TODO.md §90
---

# Testing — D7
- Vitest : `locales.spec` (résolution), `index.spec` (persistance, stockage bloqué, formats), `catalog.spec` (parité, paramètres, clés), `i18nCoverage.spec` (templates), `viewsEnglish.spec`, `AppShellI18n.spec`, `PortalI18n.spec`.
- Java : `I18nPropertiesTest`, `EmailSignatureInviteSenderTest`, `EmailPortalInviteSenderTest`, `AuthControllerTest`.
- Playwright : E2E-I18N-1 (bascule, rechargement, retour).
