---
phase: planning
title: Pré-vérification IA des dossiers — plan
description: Issue #211 — découpage en lots livrés par PR successives (brouillon de phase 1)
---

# Pré-vérification IA des dossiers — plan

Une PR par lot, fusionnée au fil de l'eau (P13). La fonction reste désactivée par défaut (`app.precheck.enabled=false`)
tant que les lots A à D ne sont pas livrés.

## Milestones

- [x] Phase 2 : revue des exigences, O1, O2, O4 tranchées (R1 à R7), O3 renvoyée en phase 3
- [ ] Phase 3 : conception validée
- [ ] Lot P fusionné : types de pièces
- [ ] Lot A fusionné : fournisseur et extraction
- [ ] Lot B fusionné : moteur, brique, REST, MCP
- [ ] Lot C fusionné : panneau sur la fiche
- [ ] Lot D fusionné : docs, exemple, évaluation, revue adversariale ; `docs/ai` de la feature vidé ; #211 close

## Task Breakdown

### Lot P — Types de pièces (R1 à R4)

- [ ] P1 : `pieceTypes` sur le type de dossier (modèle, validation, compatibilité, API, éditeur d'administration)
- [ ] P2 : choix du type au dépôt sur la fiche ; reclassement d'une pièce non classée (EDIT, journalisé)
- [ ] P3 : portail : `portalPieceTypes` sur la tâche externe, choix par le tiers
- [ ] P4 : Vitest, Playwright, docs de spec (`04-dossiers.md`, `26-portail-externe.md`)

### Lot A — Fournisseur et extraction

- [ ] A1 : `PrecheckModelPort` et adaptateur compatible OpenAI (O3), HTTPS, délai, sans redirection
- [ ] A2 : `app.precheck.*` (activation, fournisseur, URL, clé, modèle, délai, plafonds, bornes), démarrage refusé si
      incohérent
- [ ] A3 : plafond de jetons par jour et par instance
- [ ] A4 : `PieceTextExtractorPort` et PDFBox borné (pages, caractères), « non lisible »
- [ ] A5 : faux fournisseur de test

### Lot B — Moteur et brique

- [ ] B1 : spécification de la brique et validation (déploiement et exécution)
- [ ] B2 : passe 1 complétude
- [ ] B3 : entrée masquée, prompt versionné, validation de la sortie
- [ ] B4 : changeSet 039, dépôt
- [ ] B5 : délégué `aiPrecheckTask`, catalogue, liste blanche, refus au déploiement (async, désactivée)
- [ ] B6 : REST (relancer, lire, décider, export) et MCP (lancer, lire)
- [ ] B7 : test « sensible nulle part », injection simulée, revue adversariale du lot

### Lot C — Panneau

- [ ] C1 : `PrecheckPanel.vue`, marquage IA, accepter / écarter, relancer, i18n fr/en
- [ ] C2 : Vitest et Playwright

### Lot D — Docs et évaluation

- [ ] D1 : spec `48-preverification-ia.md`, point du registre (rupture P12), identifiants
- [ ] D2 : guides (conception BPMN, utilisateur, déploiement : fournisseurs, RGPD, AI Act)
- [ ] D3 : exemple riverain et jeu d'évaluation
- [ ] D4 : revue adversariale finale, vérifications, vidage de `docs/ai`

## Dependencies

- Lots P et A indépendants ; B dépend de P et A ; C dépend de B (API) ; D en dernier.
- Aucune dépendance externe pour les tests (faux fournisseur) ; une clé ou un Ollama local pour la démo réelle.

## Timeline & Estimates

Fiche : 35 à 57 j/p, hors types de pièces. Répartition indicative : P 5–8 (ajout de la phase 2), A 8–15, B 15–22,
C 5–8, D 7–12.

## Risks & Mitigation

- Qualité du modèle (faux positifs ou négatifs) : déterministe d'abord, source citée, décision humaine, jeu d'évaluation.
- Injection par les pièces : appel sans outils, schéma fermé, revue adversariale.
- Données personnelles chez un tiers : minimisation, sensibles exclus, fournisseur UE ou auto-hébergé.
- Pièces scannées : hors périmètre (constat « non lisible ») ; OCR possible dans une issue suivante.

## Resources Needed

- PDFBox 3.0.x (Apache 2.0).
- Pour la démo : une clé Mistral (UE) ou un Ollama local.
