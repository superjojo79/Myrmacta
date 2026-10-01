---
phase: design
title: Design — D7 internationalisation de l'interface
status: reviewed
created: 2026-10-01
feature: i18n-interface
lot: D7
todo_ref: TODO.md §90
---

# Design — D7

- `frontend/src/i18n/` : `locales.ts` (langues, normalisation, ordre de résolution — pur), `index.ts` (instance `vue-i18n`, choix persistant, défaut du tenant, `lang` du document), `format.ts` (`Intl`), `LocaleSwitcher.vue` (partagé SPA/portail, sans store ni routeur), `messages/{fr,en}/<espace>.ts` (l'anglais est typé sur la forme du français).
- Portail : `main.ts` installe `i18n`, `?lang=` lu au chargement ; la règle ESLint d'isolation du portail est inchangée.
- Backend : `I18nProperties` (`app.i18n.default-locale`, fr|en), `AuthUserResponse.defaultLocale`, `EmailSignatureInviteSender` et `EmailPortalInviteSender` par langue (français inchangé, anglais avec `?lang=en`).
- Tests : `src/test/navigator.ts` fixe le navigateur à `fr-FR` avant tout import ; `setup.ts` installe le plugin et remet la langue entre deux tests ; Playwright en `fr-FR`.
- Clés dynamiques (`t(\`…${code}\`)`) uniquement avec repli (`te`) quand le code vient du serveur.
