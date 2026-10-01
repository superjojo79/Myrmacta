---
phase: requirements
title: Requirements — D3b (mapping réponse de connecteur → champs du dossier)
status: reviewed
created: 2026-10-01
feature: d3b-mapping-reponse-connecteur
lot: D3b
todo_ref: TODO.md §78 · analyse 34 §2
---

# Requirements — D3b

## Problème
Le résultat d'un connecteur SERVICE n'atterrit que dans la variable `connectorResult` (`{status, body}`, `body` = **texte**). Aucun delegate allowlisté n'écrit les
champs du dossier hors formulaire humain : un score, un identifiant créé dans l'ERP ou un référentiel ne peuvent pas enrichir le dossier automatiquement.
Utilisateur : intégrateur / concepteur de process (D0 = C), via la palette de l'éditeur BPMN ou l'assistant MCP.

## Objectifs
- G1. Une brique « Enregistrer des valeurs dans le dossier » : pour chaque champ cible, une expression LC2 (JSONata) évaluée sur `dossier`, `connectorResult`, `variables`, `now`.
- G2. `connectorResult.json` : le corps de réponse **parsé** (si JSON valide et borné), car une expression LC2 ne peut pas parser du texte (`$eval` interdit).
- G3. Écriture **par le chemin métier existant** (validation de type, normalisation, ACL système, versionnage, champs sensibles redigés) — jamais un contournement du `DossierFieldValidator`.
- G4. Aucune nouvelle surface d'expression : attribut **littéral** uniquement, validé au déploiement, jamais JUEL (§57, §69) ; allowlist BPMN étendue d'un seul delegate.
- G5. Exposition MCP dans le même lot : la brique entre dans `list_bpmn_building_blocks` ; `validate_expression` / `evaluate_expression` servent à la mettre au point.

## Hors périmètre
Écriture de variables de process (la brique « Évaluer une expression » existe) ; mapping en entrée de trigger (existe : `SimpleJsonPathMapper`) ; mapping vers plusieurs dossiers ; création de champs.

## Décisions ouvertes tranchées (DECISIONS.md)
D-INTEG-MAPPING-TARGET, D-MAP-FORMAT, D-MAP-UNDEFINED, D-MAP-ATOMIC, D-MAP-BODY-JSON, D-MAP-SENSITIVE.

## Critères d'acceptation
- Un process `connecteur → mapping` écrit un score numérique et un montant `MONEY` dans le dossier ; la version du dossier avance d'une seule unité (écriture atomique de tous les champs).
- Une valeur invalide (type, devise) fait échouer l'activité avec un message neutre ; **aucun** champ n'est écrit (tout ou rien).
- Une expression sans valeur (chemin absent) échoue explicitement (cohérent avec D-EXPR-UNDEFINED), sauf valeur par défaut posée par l'auteur.
- Un champ sensible n'est jamais lisible dans l'expression ; il reste inscriptible comme cible (une réponse peut alimenter un champ sensible).
- Déploiement refusé si : JSON de mapping invalide, > 20 champs, nom de champ invalide, expression refusée par le moteur, attribut non littéral.
