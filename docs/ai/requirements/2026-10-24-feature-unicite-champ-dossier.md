---
phase: requirements
title: Unicité d'un champ de dossier et brique « Rechercher des dossiers »
description: Issue #184 — imposer qu'une valeur ne figure que dans un dossier ouvert du type, et permettre au workflow de chercher des dossiers existants
---

# Exigences — `unicite-champ-dossier` (issue #184)

## Problème

Aucune contrainte d'unicité ne peut porter sur un champ de dossier : seule l'unicité des *noms* de champs est contrôlée
(`DossierType.requireUniqueFieldNames`). Aucune brique BPMN ne permet non plus de chercher un dossier existant depuis un
workflow : les expressions ne voient que le dossier courant.

Dans l'exemple « autorisation riverain » (`exemples/autorisation-riverain/DIFFICULTES.md` §1), la règle « une seule
demande par plaque » repose sur une clé calculée cherchable (`plaqueNormalisee`) et sur une case cochée par l'agent.
Rien n'empêche de créer un second dossier pour la même plaque. Surtout, l'autorisation tacite (30 jours sans décision)
se déclenche sans vérification : un doublon jamais instruit est autorisé d'office.

## Objectifs

1. **Unicité.** Un administrateur déclare un champ `unique`. Une valeur ne peut alors figurer que dans **un seul dossier
   non clôturé** du type. La règle s'applique à toute écriture (création, correction, formulaire de tâche, brique de
   workflow, import, MCP) et la base la garantit même sous accès concurrents.
2. **Brique « Rechercher des dossiers ».** Un workflow compte les dossiers du même type qui ont les mêmes valeurs que des
   champs choisis. Il récupère le nombre et la référence du premier dossier trouvé, pour alimenter une passerelle ou un
   message.

## Non-objectifs

- Unicité entre plusieurs types, ou sur une combinaison de champs. Une combinaison passe par un champ calculé, comme
  `plaqueNormalisee`.
- Comparaison tolérante (casse, espaces, ponctuation) : la normalisation reste dans un champ calculé.
- Recherche dans un autre type que celui du dossier courant (question d'accès inter-types non tranchée).
- Opérateurs autres que l'égalité dans la brique (intervalle, appartenance, plein texte) ; inclusion des dossiers
  clôturés.
- Étape équivalente dans le concepteur de parcours (étapes avancées hors V1, #194).
- Variable de sortie de type liste.

## Récits utilisateur

- **Administrateur fonctionnel (`TENANT_ADMIN`)** : je coche « Valeur unique » sur un champ du type, pour qu'aucun
  agent, import ni workflow ne crée un doublon.
- **Administrateur** : si des doublons existent déjà parmi les dossiers ouverts, la modification est refusée et je vois
  quelles valeurs et quels dossiers sont en cause, pour les résoudre avant de recommencer.
- **Agent (`DOSSIER_MANAGER`)** : quand je saisis une plaque déjà utilisée par une demande en cours, le formulaire
  m'indique le champ en cause et la référence du dossier existant.
- **Concepteur de workflow (`WORKFLOW_ADMIN`)** : je place la brique « Rechercher des dossiers » avant une passerelle,
  pour router différemment un dossier qui a des « voisins » (même demandeur, même contrat…).
- **Intégrateur via MCP** : je déclare `unique` dans `define_dossier_type` et j'utilise la brique dans un BPMN, avec les
  mêmes règles et les mêmes erreurs que l'API.

## Règles fonctionnelles

### Unicité

| # | Règle |
|---|---|
| U1 | `unique` est un réglage facultatif de `FieldDefinition` (défaut : faux). |
| U2 | Types autorisés : `STRING`, `NUMBER`, `DATE`. Refusé sur `BOOLEAN`, `SELECT`, `MONEY`, `EXTERNAL_REF`, `FILE`, groupe répétable et sous-champ de groupe. |
| U3 | Refusé sur un champ `sensitive` : le refus d'un doublon révélerait la valeur masquée par essais successifs (même raison que `searchable`). |
| U4 | Autorisé sur un champ calculé (`formula`) : la valeur contrôlée est la valeur calculée. |
| U5 | Portée : dossiers du **même type** dont le statut n'est ni `CLOTURE` ni `ARCHIVE`. Passer en `CLOTURE` libère la valeur ; aucun retour en arrière n'existe (`DossierStatusTransitions`). |
| U6 | Comparaison : égalité stricte sur la forme canonique enregistrée (texte tel quel, nombre sans zéros non significatifs, date ISO). |
| U7 | Une valeur absente ou vide n'entre jamais en conflit. |
| U8 | Toute écriture qui créerait un doublon est refusée **avant** d'être enregistrée : erreur de validation de champ, code `UNIQUE`, avec le libellé du champ et la référence du dossier existant. |
| U9 | Deux écritures simultanées de la même valeur : une seule réussit, l'autre reçoit la même erreur qu'en U8. |
| U10 | Activer `unique` (ou changer un champ unique) alors que des doublons existent parmi les dossiers ouverts : la mise à jour du type est refusée, avec les valeurs en double et des références d'exemple. Aucune écriture partielle. |
| U11 | Retirer `unique` lève la contrainte immédiatement. |
| U12 | La promotion de configuration applique U10 sur l'environnement cible. |
| U13 | Une valeur de champ unique dépasse 1 000 caractères : refusée (erreur de validation), la garantie en base ayant une longueur bornée. |

### Brique « Rechercher des dossiers »

| # | Règle |
|---|---|
| R1 | `serviceTask` dédié (nom technique fixé en conception), dans `AllowedBpmnDelegates`, le catalogue de briques et la palette. |
| R2 | Entrée : 1 à 3 critères `champ = source`. Le champ doit être déclaré `searchable` dans le type du dossier courant ; la source est `dossierField:<champ>` (dossier courant) ou `variable:<nom>` (variable de process scalaire). |
| R3 | Portée : dossiers du même type que le dossier courant, non clôturés, **hors dossier courant**. |
| R4 | Sortie : deux variables nommées par le concepteur, le nombre de dossiers trouvés et la référence du premier (vide si aucun). Ordre du « premier » : le plus ancien par date de création. Ces noms ne peuvent pas être des variables réservées (§99). |
| R5 | Validé au déploiement : brique connue, nombre de critères, syntaxe des sources, noms de sortie. Le caractère cherchable d'un champ est revérifié à l'exécution (le type peut changer après le déploiement). |
| R6 | Une source vide ou absente à l'exécution : critère jamais satisfait (nombre 0), pas d'incident. Un champ qui n'est plus cherchable : incident visible et rejouable. |
| R7 | La brique ne lit que les champs critères et la référence : aucune valeur de champ d'un autre dossier n'est copiée dans le process. |
| R8 | Une source `dossierField:<champ>` doit désigner un champ **déclaré et non sensible** du type, comme pour les expressions (§75) : sinon le nombre renvoyé ferait de la brique un oracle sur une valeur masquée. Vérifié à l'exécution, incident sinon. |

## Critères d'acceptation

- [ ] Créer, corriger (PATCH), compléter une tâche ou exécuter `fieldMappingTask` avec une valeur déjà présente dans un
      dossier ouvert du type : refus `UNIQUE`, rien n'est écrit.
- [ ] Même valeur sur un dossier clôturé : acceptée.
- [ ] Deux créations concurrentes de la même valeur : une seule réussit (test d'intégration sur PostgreSQL).
- [ ] Activer `unique` sur un type qui contient des doublons ouverts : refus avec les valeurs et des références ; sans
      doublon : accepté, et la contrainte s'applique tout de suite.
- [ ] `unique` refusé sur un type non autorisé (U2) et sur un champ sensible (U3), côté serveur et côté SPA.
- [ ] Un champ calculé unique (`plaqueNormalisee`) bloque `1-abc 123` quand `1 ABC 123` est déjà ouvert.
- [ ] Brique : 0 voisin → nombre 0 et référence vide ; 2 voisins → nombre 2 et référence du plus ancien ; le dossier
      courant et les clôturés ne sont jamais comptés.
- [ ] Brique : déploiement refusé pour un critère mal formé ou une sortie réservée ; incident si le champ n'est plus
      cherchable.
- [ ] SPA : case « Valeur unique » dans l'éditeur de schéma, erreur `UNIQUE` affichée sur le champ du formulaire de
      dossier, brique présente dans la palette (Vitest + Playwright).
- [ ] MCP : `define_dossier_type` accepte `unique` ; les erreurs `UNIQUE` remontent comme dans l'API.
- [ ] Exemple riverain : `plaqueNormalisee` unique, case de vérification manuelle retirée, R7 attend un refus ;
      `DIFFICULTES.md` §1 marquée levée.
- [ ] `./mvnw verify`, `cd frontend && npm run lint && npm run test:all` et `scripts/check-docs.sh` verts.

## Contraintes

- Architecture hexagonale (ArchUnit) : la règle d'unicité et la forme canonique vivent dans le domaine ; la garantie
  concurrente est un détail de persistance.
- Schéma par tenant : nouveau changeSet Liquibase, jamais de modification d'un changeSet existant. Pas de DDL à
  l'exécution.
- Tests sur PostgreSQL embarqué (zonky) ; *IT inclus dans `verify`.
- `ArchitectureRulesTest.DOSSIER_FIELDS_READ_ALLOWLIST` : toute nouvelle classe qui lit `Dossier.fields()` (calcul des
  clés uniques, résolution des sources de la brique) y est ajoutée avec sa justification.

## Hypothèses validées (cadrage du 2026-10-24)

Périmètre, portée, refus en présence de doublons, égalité stricte, forme de la sortie, type courant seulement, critères
d'égalité, surfaces (BPMN + palette + MCP) : tranchés par l'utilisateur. U2, U3, U4, U7, R4 (ordre), R6 et le refus
plutôt que l'avertissement : posés en cadrage, à confirmer en phase 2.

## Revue de phase 2 (2026-10-24)

Constats vérifiés dans le code et corrections apportées :

- Écritures de champs : aucune requête SQL directe ne modifie `dossier` (seules des tables annexes ont des `@Modifying`) ;
  le formulaire de tâche `dossier-fields` passe par `PATCH /api/dossiers/{id}/fields` ; la purge passe par
  `DossierRepository.deleteById`. Une garantie tenue dans `DossierRepository.save` couvre donc toutes les écritures.
- Ajout de **R8** : une source `dossierField:` sensible ferait de la brique un oracle (même règle que les expressions).
- Ajout de **U13** : longueur bornée d'une valeur unique, imposée par la garantie en base.
- Ajout de la contrainte ArchUnit sur la lecture de `Dossier.fields()`.
- Le lint ai-devkit attend une branche `feature-unicite-champ-dossier` : écart assumé, la session impose la branche
  `ccr-1b2a2fd0-r71qj0`.

Hypothèses retenues sans arbitrage explicite, révisables en phase 3 : U2 (types), U3 (sensible), U4 (calculé), U7 (vide),
R4 (premier = plus ancien), R6 (source vide → 0 ; champ plus cherchable → incident), U13 (1 000 caractères).

## Questions ouvertes

- Code HTTP d'un refus `UNIQUE` : 400 (comme toute erreur de champ) ou 409. À trancher en conception.
- Mécanisme de garantie en base : table de valeurs uniques ou index. À trancher en conception.
