---
name: document-code
description: Myrmacta · Documenter un point d'entrée du code (module, fichier, dossier, fonction, API) par une analyse structurée et une carte des dépendances, versée dans la spec du domaine. À utiliser pour documenter, comprendre ou cartographier une partie du code.
---

# Documenter du code

Analyse d'abord, écriture ensuite.

> Adapté de `document-code` d'AI DevKit (codeaholicguy/ai-devkit, Apache-2.0), modifié pour Myrmacta : le résultat
> va dans `docs/specs/` et non plus dans `docs/ai/implementation/knowledge-*.md` (registre §107) ; base mémoire et
> page HTML retirées.

## Règle

Ne rien écrire avant d'avoir validé le point d'entrée et terminé l'analyse.

## Démarche

1. **Cadrer**
   - Confirmer le point d'entrée (fichier, dossier, classe, fonction, endpoint, outil MCP), le but (comprendre,
     documenter, préparer une modification) et la profondeur voulue.
   - Vérifier qu'il existe ; sinon proposer les candidats proches.
   - Lire d'abord ce qui existe : spec du domaine (`docs/specs/00-index.md` pour la trouver), points du registre
     liés, identifiants `D-…` cités dans le code (`docs/decisions/identifiants.md`).
2. **Rassembler le code**
   - But, éléments exposés, motifs employés.
   - Dossier : structure et modules clés. Fonction ou API : signature, paramètres, retour, erreurs.
   - Situer chaque élément dans l'hexagone : `domain` (ports `in` et `out`), `application`, `infrastructure`.
3. **Cartographier les dépendances**, jusqu'à trois niveaux, sans boucler : imports, appels, ports et adaptateurs,
   paquets externes. Exclure le code généré.
4. **Synthétiser**
   - Vue d'ensemble, logique centrale, flux d'exécution.
   - Erreurs, performance, sécurité (tenant, droits, SSRF, secrets).
   - Écarts constatés avec la spec ou le registre, risques, améliorations.

## Où écrire

| Besoin | Destination |
|---|---|
| Comprendre, sans trace à garder | la réponse seule |
| Connaissance durable du système | la spec du domaine (`docs/specs/NN-*.md`), section existante ou « Implémentation » ; diagramme mermaid si un flux le demande |
| Écart avec une décision, point à trancher | un point du registre (`docs/decisions/`), jamais tranché en silence |
| Note utile seulement à la feature en cours | le document de travail `docs/ai/` de cette feature |

Pas de nouveau fichier de connaissance à part : une spec qui manque se crée au numéro suivant de
`docs/specs/` et s'ajoute à son index.

## Gabarit de l'analyse

- Vue d'ensemble
- Fonctionnement (flux, règles, cas d'erreur)
- Dépendances (diagramme mermaid si utile)
- Écarts et risques
- Fichiers lus, date

## Validation

- Chaque rubrique du gabarit est traitée ou déclarée sans objet.
- Ce qui est écrit dans une spec est vérifié contre le code, pas déduit des noms.
- `scripts/check-docs.sh` vert si un fichier de `docs/` a changé.
