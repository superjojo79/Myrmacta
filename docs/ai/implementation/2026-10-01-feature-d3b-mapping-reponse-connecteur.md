---
phase: implementation
title: Implémentation — D3b (mapping réponse de connecteur → champs du dossier)
feature: d3b-mapping-reponse-connecteur
---

# Implémentation — D3b (TODO §78)

Voir le design pour les composants. Aucune migration, aucune variable d'environnement.

## Revue adversariale (Phase 10) — 1 passe, sous-agent indépendant avec sondes exécutées
0 Critique / 0 Haute. Trois Moyens et quatre Bas, tous corrigés avec test de non-régression :

| # | Sév. | Constat | Correctif |
|---|---|---|---|
| M1 | Moyenne | Une réponse piégée (profondeur 40, `1e400`) faisait échouer **toutes** les expressions du process (`json` parsé sans pré-validation) | `json` pré-assaini (profondeur ≤ 24, nombres hors plage → texte) ou omis ; tests profondeur/`1e400` évalués par le vrai moteur |
| M2 | Moyenne | Écriture d'un champ sensible sans audit ni attribution | `SecurityAuditLog.sensitiveFieldChangedByWorkflow` (nom, process, activité, jamais la valeur) ; auteur `workflow:<pid>/<activité>` |
| M3 | Moyenne | Éditeur : mapping sur une seule ligne, valeur multiligne signalée à tort invalide (idem `formula` avant D3b) | `BlockParameter.multiline` publié par le catalogue ; `TextAreaEntry` ; diagnostics et écriture alignés (repli sur le nom pour un ancien catalogue) |
| B1 | Basse | `connectorResult` forgeable à la complétion d'une tâche | Variable réservée (`WorkflowVariables.CONNECTOR_RESULT`) — D-MAP-RESERVED ⚠ |
| B2 | Basse | Perte de précision silencieuse des montants (double) | Lecture en décimal exact ; > 15 chiffres significatifs → texte exact |
| B3 | Basse | Erreur d'évaluation sans nom de champ ; `$error` recopie son texte | Erreur enveloppée (champ + activité) ; `$error` documenté |
| B4 | Basse | Création d'un champ non déclaré (`allowUnknownFields`) via `dump = $` | Cibles déclarées seulement (D-MAP-TARGETS) |

Décisions D-MAP-* absentes du journal : ajoutées. Passe 2 non requise (aucun Critique/Haut). Trous de tests comblés : profondeur/non fini, `json` écrasé, précision, audit, auteur, cible non déclarée, multiligne SPA, drapeau
catalogue, variable réservée. Non couverts (consignés) : rollback d'une activité suivante dans la même transaction (comportement Flowable standard), dossier clos (couvert par le use case existant).
