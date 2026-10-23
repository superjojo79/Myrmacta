---
phase: planning
title: Joindre un document à la brique « Notifier » — plan
description: Découpage TDD
---

# Plan — `notifier-piece-jointe`

- [ ] **T1** Catalogue et règles de forme : 2 paramètres, `VERSION` 2, contrôle `SOURCE_REF` et nom (TDD `BlockAttributeRulesTest`).
- [ ] **T2** Modèle : `EmailAttachment`, `NotificationEvent.attachment`, JSON outbox, validation « EMAIL requis » (TDD use case).
- [ ] **T3** `ResolveEmailAttachmentUseCase` : élément `PRODUCED_DOC` du dossier, non vide, plafond, PDF (TDD).
- [ ] **T4** Delegate : lecture littérale, résolution de la source, erreurs sans valeur (TDD `NotificationDispatchTaskTest`).
- [ ] **T5** `EmailPort` à 4 arguments, `SmtpEmailAdapter` multipart, relais outbox (TDD).
- [ ] **T6** Test d'intégration Flowable de bout en bout (e-mail capturé avec le PDF).
- [ ] **T7** SPA : diagnostic `SOURCE_REF` (Vitest), catalogue de test.
- [ ] **T8** Exemple riverain : pièce jointe, suppression de la transmission manuelle ; garde-fou parseur sur `exemples/`.
- [ ] **T9** Docs : spec 09, guide de conception §6c, guide utilisateur, registre §96, identifiants, DIFFICULTES §2.
- [ ] **T10** Vérifications : `./mvnw verify`, `npm run lint && npm run test:all`, `scripts/check-docs.sh` ; revue ; vider `docs/ai/`.
