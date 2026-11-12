# §134 — Normalisation ASCII d'un login calculé

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (issue [#70](https://github.com/superjojo79/Myrmacta/issues/70)). Mandat cloud agent 2026-11-12.
Comportement : [spec 40](../specs/40-langage-expression-metier.md) § « Implémentation — `$ascii` » ;
guide de conception BPMN, liste blanche des fonctions.

**Constat.** L'exemple `exemples/onboarding-collaborateur` calcule le login IAM par
`$replace($lowercase($substring(prenom, 0, 1) & nom), ' ', '')`. Les accents restent : Élise Van den Broeck
donne `évandenbroeck`. La liste blanche n'a pas de fonction qui retire les diacritiques ; enchaîner `$replace`
pour chaque accent est incomplet.

**D-EXPR-ASCII** — `$ascii(texte)` produit un login :

1. décomposition NFD, marques combinatoires retirées (accents) ;
2. minuscules ;
3. seuls `[a-z0-9.-]` sont gardés (espaces, apostrophes et le reste disparaissent, ils ne sont pas remplacés) ;
4. `null` reste `null` ; un argument qui n'est pas du texte est refusé, message neutre ;
5. le résultat n'est jamais plus long que l'entrée, déjà bornée par `maxResultBytes` (pas de regex, déterministe).

Une ligature qui ne se décompose pas (`œ`, `ß`) est retirée : elle n'est pas dans le jeu. Écarté : l'étendre en
`oe` / `ss` (hors de la règle « caractères hors jeu supprimés »).

L'exemple utilise `$ascii($substring(dossier.fields.prenom, 0, 1) & dossier.fields.nom)` : `evandenbroeck`.

**Impacte :** `JsonataExpressionEngine`, `generer-bpmn.py`, le BPMN généré, le guide de conception, `OnboardingOrchestrationExampleIT`.
