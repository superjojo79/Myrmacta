---
phase: testing
title: Pré-vérification IA des dossiers — stratégie de test
description: Issue #211 — scénarios dérivés des critères de succès (brouillon de phase 1)
---

# Pré-vérification IA des dossiers — stratégie de test

## Test Coverage Goals

- Unitaires : 100 % du code nouveau du domaine `precheck` (spécification, complétude, entrée masquée, validation de la
  sortie, décisions).
- Intégration : adaptateur du modèle contre le faux fournisseur, extraction PDFBox, dépôt, délégué, REST, MCP.
- E2E Playwright : panneau sur la fiche (voir, accepter, écarter, relancer).
- Évaluation : jeu rejouable contre un vrai modèle, hors `verify`.

## Unit Tests

### Spécification de la brique

- [ ] Pièces, questions et champs valides acceptés ; question vide, doublon de clé, plus de 20 questions refusés
- [ ] Champ sensible ou inconnu dans `precheckFields` refusé au déploiement
- [ ] Brique sans `flowable:async="true"` refusée au déploiement
- [ ] Fonction désactivée : déploiement refusé avec un message explicite

### Complétude (passe 1)

- [ ] Champ obligatoire vide → `MISSING` déterministe
- [ ] Champ rendu obligatoire par une règle (`requiredByRule`) → `MISSING`
- [ ] Champ sensible obligatoire vide → `MISSING` (contrôlé sans être envoyé)
- [ ] Pièce attendue absente → `MISSING` (selon O1)

### Entrée et sortie du modèle

- [ ] Aucun champ ni pièce sensible dans l'entrée construite
- [ ] Bornes : pièces, pages et caractères tronqués avec constat
- [ ] Empreinte SHA-256 stable pour une même entrée
- [ ] Sortie hors schéma rejetée ; `sourceRef` inconnu ignoré ; plus de 30 constats tronqués
- [ ] Texte de constat trop long tronqué

### Décisions

- [ ] Accepter et écarter exigent EDIT ; décision journalisée (qui, quand)
- [ ] Une décision sur un constat d'une autre exécution ou d'un autre dossier → 404

## Integration Tests

- [ ] Adaptateur compatible OpenAI contre le faux fournisseur : `response_format`, en-tête d'autorisation, délai,
      aucune redirection suivie, erreur du fournisseur non relayée
- [ ] PDFBox : PDF texte lu ; PDF image, chiffré, corrompu → « pièce non lisible »
- [ ] Délégué asynchrone : succès → exécution enregistrée ; panne → incident rejouable ; rejeu → exécution
- [ ] Plafond de jetons atteint → incident
- [ ] « Sensible nulle part » : aucune valeur sensible dans les requêtes reçues par le faux fournisseur, ni dans les
      journaux applicatifs
- [ ] Injection simulée dans une pièce : sortie toujours conforme au schéma, aucun effet de bord
- [ ] REST : relancer (EDIT), lire (lecture), décider (EDIT), export (TENANT_ADMIN) ; refus journalisés
- [ ] MCP : outils mode opération, absents du catalogue de l'assistant (D-ASSIST-TOOLS)
- [ ] Purge du dossier → exécutions et constats supprimés

## End-to-End Tests

- [ ] Scénario de la fiche sur l'exemple riverain avec le faux fournisseur : constats visibles, deux acceptés, un écarté
- [ ] Relancer après ajout d'une pièce : nouvelle exécution, ancienne consultable
- [ ] Lecteur sans EDIT : panneau en lecture seule
- [ ] Régression : fiche dossier, incidents (§94), assistant de conception inchangés

## Test Data

- Faux fournisseur compatible OpenAI en mémoire (`support/InMemoryOpenAiCompatibleProvider`), réponses scriptables
  (dont sortie invalide, panne, injection).
- PDF de test générés dans les tests (texte, image seule, chiffré, corrompu).
- Exemple `exemples/autorisation-riverain` (type, BPMN, dossier avec pièce manquante et incohérence de commune).

## Test Reporting & Coverage

- `./mvnw verify` (avec `FLOWFORGE_TEST_PG_*` en session), `cd frontend && npm run lint && npm run test:all`,
  `scripts/check-docs.sh`.
- Jeu d'évaluation : commande dédiée, rapport par type de constat ; non exécuté en CI.

## Manual Testing

- Démo réelle avec une clé Mistral ou un Ollama local, documentée dans le guide de déploiement.
- Accessibilité du panneau : région nommée, boutons nommés d'après le constat.

## Performance Testing

- Pièce de 50 pages : extraction sous les bornes, temps d'exécution mesuré.

## Bug Tracking

- Issues GitHub ; revue adversariale (injection par les pièces, fuite de données sensibles, contournement des droits)
  avant la PR du lot B.
