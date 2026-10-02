---
phase: requirements
title: Requirements — D7 internationalisation de l'interface
status: reviewed
created: 2026-10-01
feature: i18n-interface
lot: D7
todo_ref: TODO.md §90 · analyse 38
---

# Requirements — D7

## Objectifs
- G1. L'interface (SPA et portail public) existe en français et en anglais ; l'utilisateur choisit, le choix est conservé.
- G2. Langue par défaut du tenant (propriété d'instance), exposée à l'interface ; ordre de détection D-I18N-DETECT.
- G3. Dates, nombres, montants selon la langue ; messages de validation et de repli traduits.
- G4. E-mails à des tiers sans profil dans la langue du tenant ; lien du portail porteur de la langue.
- G5. Une chaîne oubliée casse la CI (parité, résolution des clés, couverture des templates).

## Hors périmètre
Libellés saisis par le tenant, détail d'erreur de l'API, diagnostics techniques des éditeurs de template et BPMN, autres langues (voir D-I18N-SCOPE, D-I18N-ERRORS).

## Critères d'acceptation
Basculer la langue re-rend tout sans recharger et survit à un rechargement ; un `?lang=` ou une valeur de tenant non gérés sont ignorés ; les e-mails français restent identiques octet pour octet.
