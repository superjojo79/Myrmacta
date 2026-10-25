---
phase: requirements
title: Pré-vérification IA des dossiers — exigences
description: Issue #211 — une brique de workflow qui pré-vérifie un dossier avant l'instruction ; l'agent garde la décision
---

# Pré-vérification IA des dossiers — exigences

Issue [#211](https://github.com/superjojo79/FlowForge/issues/211). Cadrage : [`docs/analyses/fiche-preverification-ia.md`](../../analyses/fiche-preverification-ia.md).
Phase 1 tranchée par l'utilisateur le 2026-10-25 (décisions **P1-…** ci-dessous).

## Problem Statement

- Les communes et CPAS reçoivent des demandes incomplètes ou incohérentes. L'agent passe une part importante de son
  temps à contrôler la complétude et la cohérence avant même d'instruire.
- Myrmacta sait déjà contrôler la saisie (champs obligatoires, règles JSONata, DMN), mais rien ne regarde le **contenu
  des pièces** ni ne prépare une synthèse pour l'agent.
- Aujourd'hui, aucune fonction d'IA ne lit les données d'un dossier (D-MCP-LC-MODES, D-ASSIST-TOOLS) : l'assistant de
  conception ne voit que la configuration. Un seul fournisseur de modèle existe (Anthropic, `SpringAiLanguageModel`).

## Goals & Objectives

**Objectifs principaux**

- Une brique de palette « Pré-vérification IA » (délégué `aiPrecheckTask`), **asynchrone**, placée avant la tâche
  d'instruction, et un bouton « Relancer » sur la fiche dossier (P3).
- Trois passes, du plus fiable au moins fiable :
  1. **complétude** déterministe : champs obligatoires, champs rendus obligatoires par une règle (`requiredByRule`),
     pièces attendues ;
  2. **règles** : valeurs posées par le workflow (DMN, formule, recherche de doublons) et **questions fermées** posées au
     modèle sur le texte des champs et des pièces ;
  3. **synthèse** courte et **constats** typés `MANQUANT`, `INCOHÉRENCE`, `À VÉRIFIER`, `OK`, chacun avec sa **source**
     (champ, pièce, règle ou question) et un niveau de confiance.
- Pièces comprises dès la première livraison (P2) : texte extrait par **PDFBox**, sans OCR (P6).
- L'agent **accepte ou écarte** chaque constat ; son choix est journalisé (P4). Rien n'avance le workflow ni n'écrit
  dans un champ du dossier.
- Fournisseur par **API compatible OpenAI** (Mistral, vLLM, Ollama, Azure OpenAI UE…) ; Anthropic reste possible (P1).
- **Types de pièces** sur le type de dossier, choisis au dépôt (fiche et portail), reclassables (R1 à R4).

**Objectifs secondaires**

- Outils MCP en mode **opération** pour lancer et lire une pré-vérification (P11).
- Exemple `exemples/autorisation-riverain` mis à jour avec le scénario de la fiche, et un jeu d'évaluation rejouable
  contre un vrai modèle (P10).

**Hors périmètre**

- OCR et lecture des images ou scans par un modèle multimodal : une pièce sans couche texte donne un constat
  `À VÉRIFIER` « pièce non lisible ».
- Formats autres que PDF et texte (Word, ODT…).
- Toute décision automatisée : la pré-vérification ne fait que préparer (RGPD art. 22).
- Ouvrir les données de dossier à l'assistant de conception : il reste sans aucune donnée de dossier (P12).
- Un usage pour l'éligibilité à une aide ou un subside (AI Act, annexe III 5 a) : documenté comme à analyser au cas par
  cas, sans blocage technique (H6).

## Décisions de la phase 1 (utilisateur, 2026-10-25)

| # | Sujet | Décision |
|---|---|---|
| P1 | Fournisseur | API compatible OpenAI (Mistral UE, vLLM, Ollama, Azure UE) ; Anthropic conservé ; choix par instance |
| P2 | Périmètre | Tout, pièces comprises |
| P3 | Déclenchement | Brique BPMN asynchrone (échec = incident rejouable, §94) et bouton « Relancer » sur la fiche |
| P4 | Constats | Accepter ou écarter, choix journalisé |
| P5 | Contrôles | Déclarés sur la brique BPMN (pièces à lire, questions fermées), validés au déploiement ; « Relancer » reprend la configuration de la dernière exécution |
| P6 | Extraction | PDFBox, sans OCR |
| P7 | Sensibles | Champs et pièces sensibles toujours exclus de l'envoi au modèle ; la complétude les contrôle quand même |
| P8 | Activation | Configuration dédiée `app.precheck.*`, désactivée par défaut ; BPMN avec la brique refusé au déploiement si désactivée |
| P9 | Stockage | Tables dédiées (exécution, constats) et panneau « Pré-vérification » ; purge avec le dossier |
| P9b | Droits | Lire le dossier = voir ; EDIT sur le type = accepter, écarter, relancer |
| P10 | Recette | Faux fournisseur compatible OpenAI pour les tests ; exemple riverain et jeu d'évaluation |
| P11 | MCP | Outils mode opération : lancer et lire |
| P12 | Rupture | Ouverture des données de dossier à l'IA limitée à la pré-vérification, consignée au registre |
| P13 | Livraison | Une PR par lot, fusionnée au fil de l'eau ; fonction désactivée par défaut tant que tout n'est pas livré |

## Décisions de la phase 2 (utilisateur, 2026-10-25)

| # | Sujet | Décision |
|---|---|---|
| R1 (O1) | Identifier une pièce | **Types de pièces** déclarés sur le type de dossier (clé, libellé, sensible) ; le type est choisi au dépôt (métadonnée `pieceKey` de l'élément `ATTACHMENT`) |
| R2 | Pièce obligatoire | Déclarée **dans la brique** : pièces attendues à ce stade du processus (pas d'obligation portée par le type) |
| R3 | Pièce sans type | « Non classée » : ni comptée ni lue ; un agent avec EDIT peut lui attribuer un type après coup (journalisé) ; le type reste facultatif au dépôt |
| R4 | Portail | La tâche externe peut déclarer les types de pièces demandés ; le tiers choisit parmi eux ; sans déclaration, dépôt non classé comme aujourd'hui |
| R5 (O2) | Pièces sensibles | Liste blanche : le modèle ne lit que les pièces nommées dans la brique ; un type de pièce marqué sensible est refusé au déploiement s'il est désigné pour lecture (sa présence reste contrôlable) |
| R6 (O4) | Règles déterministes | La brique lit des **variables de process** posées par des briques précédentes (DMN, formule, recherche de doublons) et en fait des constats déterministes ; elle n'évalue pas d'expression elle-même |
| R7 | Échec d'un lancement manuel | Bouton ou MCP : exécution en tâche de fond ; en échec, exécution « échouée » (motif générique) dans le panneau avec les constats déterministes, relançable ; une seule exécution en cours par dossier |

**Hypothèses de la phase 2**

- **H9** — retirer un type de pièce du type de dossier laisse les éléments déjà classés avec une clé orpheline : ils
  redeviennent « non classés » (aucune donnée perdue).
- **H10** — le champ `FILE` existant n'est pas modifié (hors périmètre) ; les types de pièces le remplacent pour cet usage.
- **H11** — O3 (adaptateur du fournisseur) est un choix technique, tranché en phase 3.

**Hypothèses acceptées (H, restituées le 2026-10-25, validées avec « OK »)**

- **H1** — un seul appel au modèle, **sans outils**, sortie JSON validée par un schéma ; le contenu des pièces est une
  donnée, jamais une consigne.
- **H2** — plafond de jetons par jour et par instance, distinct de l'assistant ; dépassement = incident.
- **H3** — bornes réglables (nombre de pièces, pages, caractères) ; dépassement tronqué et signalé par un constat.
- **H4** — journal : modèle et version, version du prompt, empreinte SHA-256 des entrées, sortie, choix de l'agent (qui,
  quand). Les entrées en clair ne sont pas conservées.
- **H5** — synthèse et constats dans la langue par défaut de l'instance (français).
- **H6** — classement AI Act « tâche préparatoire » ; la documentation avertit pour les aides et subsides.
- **H7** — lancer par MCP exige les mêmes droits que le bouton (EDIT) et consomme le même plafond.
- **H8** — nom de feature `preverification-ia`.

## User Stories & Use Cases

- **US1 — Concepteur.** En tant que concepteur de workflow, je place la brique « Pré-vérification IA » avant
  « Instruire la demande », j'y déclare les pièces à lire et les questions fermées, et le déploiement refuse une
  configuration invalide (question vide, pièce inconnue, brique non asynchrone, fonction désactivée).
- **US2 — Agent.** En tant qu'agent instructeur, j'ouvre la fiche et je vois la synthèse et les constats, chacun avec sa
  source ; j'accepte ou j'écarte chaque constat ; mon choix est tracé.
- **US3 — Agent.** Après un complément du demandeur, je relance la pré-vérification depuis la fiche ; l'exécution
  précédente reste consultable.
- **US4 — Administrateur.** Le fournisseur est en panne ou le plafond est atteint : l'étape passe en incident visible et
  rejouable (§94), sans bloquer les autres dossiers.
- **US5 — Délégué à la protection des données.** J'exporte le journal d'une exécution (modèle, version du prompt,
  empreinte des entrées, constats, choix de l'agent) pour un audit ou une AIPD.
- **US7 — Administrateur fonctionnel.** Je déclare les types de pièces d'un type de dossier (« Preuve de domicile »,
  « Certificat d'immatriculation »), dont certains sensibles ; l'agent et le tiers du portail choisissent le type au
  dépôt ; l'agent reclasse une pièce non classée.
- **US6 — Intégrateur.** Par MCP (mode opération), je lance une pré-vérification et je lis son résultat.

**Cas limites**

- Pièce scannée (image ou PDF sans texte), pièce chiffrée ou corrompue : constat `À VÉRIFIER` « pièce non lisible »,
  pas d'échec.
- Pièce trop longue : texte tronqué, constat qui le signale.
- Sortie du modèle invalide (JSON hors schéma, source inexistante) : constat ignoré, ou exécution en échec si rien
  d'exploitable (incident).
- Pièce qui contient des consignes (« ignore les instructions… ») : sans effet sur la forme de la sortie, aucun outil à
  portée (revue adversariale dédiée).
- Champ sensible demandé par une question : refusé au déploiement.
- Dossier sans pièces attendues : seule la complétude des champs et des règles s'applique.
- Lecteur sans droit EDIT : voit les constats, ne peut ni décider ni relancer.

## Success Criteria

- Le scénario de la fiche (§6) passe sur l'exemple riverain avec le faux fournisseur : pièce manquante (déterministe),
  incohérence de commune (règle), doublon de plaque (recherche), synthèse ; l'agent accepte deux constats et en écarte un ;
  le journal montre le modèle, la version du prompt et les choix.
- Aucune valeur de champ ou de pièce sensible n'apparaît dans une requête au fournisseur (test « sensible nulle part »).
- Une panne, un refus ou un dépassement de plafond donne un incident rejouable ; un rejeu réussi produit une exécution.
- Une sortie hors schéma n'atteint jamais l'agent.
- Fonction désactivée : la brique est refusée au déploiement, les routes et outils MCP sont absents ou refusés.
- Le jeu d'évaluation tourne contre un vrai modèle compatible OpenAI et produit un rapport (précision par type de
  constat).

## Constraints & Assumptions

- Architecture hexagonale (ArchUnit) : lecture des champs bruts limitée à la liste blanche
  `DOSSIER_FIELDS_READ_ALLOWLIST` ; nouveaux ports dans le domaine, adaptateurs en infrastructure.
- Une instance par tenant, un schéma PostgreSQL : nouveau changeSet `039-…` ; jamais de modification d'un changeSet.
- Palette BPMN : brique ajoutée à `BuildingBlockCatalog`, délégué à `AllowedBpmnDelegates`, attributs littéraux validés
  par `BlockAttributeRules` (même modèle que `dossierSearchTask`).
- Licences : PDFBox (Apache 2.0) compatible avec la double licence AGPL et commerciale (§103).
- RGPD : minimisation (seuls les champs et pièces déclarés partent), fournisseur sous-traitant (art. 28), AIPD
  recommandée. AI Act : supervision humaine, transparence (marquage « IA »), journalisation.
- Échéance : aucune fixée ; passe après #191 et l'authentification belge (issue).

## Questions & Open Items

Ouverts à la fin de la phase 1 ; O1, O2 et O4 tranchés en phase 2 (R1 à R7), O3 renvoyé en phase 3 :

- **O1 — Identifier une pièce attendue.** Une pièce est un élément `ATTACHMENT` à libellé libre : aucun lien avec un
  champ `FILE` (rendu comme une zone de texte) ni avec un type de pièce. Comment la brique désigne-t-elle « la preuve de
  domicile » ?
- **O2 — Sensibilité d'une pièce.** P7 exclut les pièces sensibles, mais une pièce n'a pas d'indicateur « sensible »
  aujourd'hui.
- **O3 — Adaptateur du fournisseur.** `spring-ai-openai` 2.0.1 tire `openai-java-core`, `okhttp` et `azure-identity` ;
  un client REST direct (`/v1/chat/completions`, `response_format: json_schema`) serait plus léger. À confirmer en phase 3.
- **O4 — Règles déterministes.** La passe 2 « structurée » lit-elle des variables de process posées par des briques
  précédentes (DMN, formule, recherche), ou évalue-t-elle elle-même des expressions ?
