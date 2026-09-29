---
phase: planning
status: draft
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
---

# Project Planning & Task Breakdown — `signature-electronique`

> TODO §72. **Plan initial de Phase 1** (2026-09-29), dérivé des
> [requirements](../requirements/2026-09-29-feature-signature-electronique.md) (`draft`), du
> [design](../design/2026-09-29-feature-signature-electronique.md) (esquisse) et des
> [scénarios de test](../testing/2026-09-29-feature-signature-electronique.md). À reconcilier en
> Phase 4 après les Phases 2 et 3. Toutes les tâches `not started`. Implémentation en TDD.

## Milestones

| # | Milestone | Dépend de | Parallélisable avec |
|---|---|---|---|
| M0 | Revue documentaire des API publiques (D-SIGNATURE-CONTRACT-CHECK) | Phase 2 | — |
| M1 | Contrat `SignaturePort` + modèle de domaine + persistance | M0 | — |
| M2 | Allowlist, delegate et parser BPMN | M1 | M3 |
| M3 | Stub, filigrane, preuve, garde de profil | M1 | M2 |
| M4 | Émission et liens signataires | M1, M3 | — |
| M5 | Type de tâche portail « signature » (backend) | M4 | M7 |
| M6 | Décision du signataire, issue et reprise du process | M5 | — |
| M7 | Gestion instructeur, expiration, annulation | M4 | M5 |
| M8 | Connecteur de catalogue et callback provider | M1 | M5 |
| M9 | Purge | M1 | M5 |
| M10 | Frontend portail (écran signataire) | M5 | M11 |
| M11 | Frontend interne (panneau « Signatures ») | M7 | M10 |
| M12 | Playwright | M10, M11 | — |
| M13 | Documentation | M12 | — |
| M14 | Vérification globale et revue adversariale du portail | M13 | — |

## Task Breakdown

### M0 — Revue documentaire du contrat
- [ ] Choisir 2 à 3 API publiques de référence (avec l'utilisateur) et en extraire demande,
      signataires, statuts, preuve, webhooks.
- [ ] Produire le tableau de correspondance avec `SignaturePort`, traiter ou consigner chaque écart.

### M1 — Fondation
- [ ] Records et enums de domaine, `SignaturePort`, `SignatureProviderCapabilities`, ports `in`/`out`.
- [ ] Migration Liquibase (`signature_request`, `signature_signer`), entités JPA, repository.
- [ ] Extension de `AccessContext.External` pour le signataire, règles `case External`.

### M2 — Allowlist et parser
- [ ] Ajouter `signatureRequestTask` à `AllowedBpmnDelegates` (tests d'allowlist).
- [ ] Règles de parser du bloc signature et garde de déploiement (fonctionnalité désactivée).

### M3 — Stub
- [ ] `StubSignatureAdapter` (cycle complet, issues forcées pour les tests).
- [ ] Filigrane PDF et preuve factice, `simulated` partout.
- [ ] `SignatureConfig` : sélection par propriété, garde du profil de production, second drapeau,
      avertissement au démarrage.

### M4 — Émission
- [ ] `RequestSignatureUseCase`, résolution des signataires, empreinte du document.
- [ ] Génération des liens (jeton haché) et envoi des sollicitations.

### M5 — Portail (backend)
- [ ] Type de tâche « signature » : contexte, document, état, réponses uniformes.
- [ ] Rejeu des garde-fous du portail (limite par IP, taille du corps, IDOR).

### M6 — Décision et reprise
- [ ] `DecideSignatureUseCase` (signer, refuser), compare-and-set, sémantique multi-signataires.
- [ ] `HandleSignatureOutcomeUseCase` : documents marqués, variable d'issue, reprise du process,
      notifications inbox.

### M7 — Gestion instructeur
- [ ] Liste, annulation, renvoi ; droits VIEW / EDIT ; job d'expiration.

### M8 — Connecteur et callback
- [ ] Entrée de catalogue `signature` (`configSchema`, secrets chiffrés).
- [ ] Contrôleur de callback (HMAC horodaté, idempotence, taille, rate-limit).

### M9 — Purge
- [ ] Étendre le graphe de purge aux nouvelles tables et aux documents marqués.

### M10 / M11 — Frontend
- [ ] Écran signataire (build portail).
- [ ] Panneau « Signatures » du détail du dossier, bandeau « SIMULATION ».
- [ ] Marqueur du bloc signature dans l'éditeur BPMN (si retenu).

### M12 — Playwright
- [ ] Parcours nominal, refus, lien invalide, annulation / renvoi, fonctionnalité désactivée.

### M13 — Documentation
- [ ] `GUIDE-CONCEPTION-BPMN.md`, `GUIDE-UTILISATEUR.md`, `GETTING-STARTED.md`, `.env.example`
      (sans le second drapeau), `DEPLOIEMENT.md`, `CHANGELOG.md`, golden OpenAPI, tools MCP.

### M14 — Vérification
- [ ] `./mvnw verify`, `npm run test:all`, Playwright ; revue adversariale du portail rejouée sur le
      type « signature » ; mise à jour de TODO §72, `FILE-ATTENTE.md`, `ROADMAP.md`.

## Dependencies

- **Livré :** portail externe (PR #138), `flowable-callback`, `DocumentStoragePort`,
  `GenerateDocumentUseCase`, inbox de notifications, `AttachmentScanPort`.
- **Coutures partagées à surveiller :** `ConnectorCatalog` et enregistrement des tools MCP (pas de PR
  concurrente avec D3c/D3d ou un lot `LC*`), `AllowedBpmnDelegates` (pas de PR concurrente avec LC2 ou
  D3b), portail (pas de PR concurrente sur les mêmes contrôleurs).
- **Décisions en amont :** D6 (socle MCP) pour l'exposition MCP en écriture.

## Timeline & Estimates

Estimation indicative : **L** (comparable au portail : 14 milestones). M0 court mais bloquant pour le
contrat ; M2/M3 et M5/M7/M8/M9 parallélisables à l'intérieur du lot.

## Risks & Mitigation

- **R-PORTAIL** : réutiliser au maximum les composants du portail, rejouer la revue adversariale.
- **R-SIM-EN-PROD** : garde de profil testée, drapeau absent des gabarits, marquage indélébile.
- **R-CONTRAT-TROP-ETROIT / TROP-LARGE** : M0 avant M1 ; ne pas sortir ce qui n'est pas exercé.
- **Conflit de coutures** : lot planifié hors des PR concurrentes sur catalogue, MCP et allowlist.

## Resources Needed

- Accès à la documentation publique de 2 à 3 fournisseurs (lecture seule, aucun compte requis).
- Environnement avec JDK 25 et Docker pour `verify`, ClamAV et Playwright.
