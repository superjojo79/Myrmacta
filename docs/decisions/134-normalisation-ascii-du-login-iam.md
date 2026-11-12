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
2. translittération des lettres latines qui ne se décomposent pas : `ß`→`ss`, `æ`→`ae`, `œ`→`oe`, `ø`→`o`, `ł`→`l`
   (et leurs majuscules) — revue de #85 : les supprimer donnait un login vide (`Ø`, `Ł`) envoyé en silence ;
3. minuscules ;
4. seuls `[a-z0-9.-]` sont gardés (espaces, apostrophes et le reste disparaissent) ;
5. `null` reste `null` ; un argument qui n'est pas du texte est refusé, message neutre ;
6. un texte sans aucun caractère de login (`""`, `"!!!"`) est **refusé** (`$ascii produced no login characters`) :
   l'expression échoue, le connecteur n'envoie pas `username: ""` ;
7. pas de regex. La translittération peut allonger (`ß` → deux lettres) ; le résultat reste borné par `maxResultBytes`.

Écarté : une translittération générale (tables ICU, `ö`→`oe` déjà couvert par NFD→`o`). Seules les cinq lettres
ci-dessus ne deviennent pas une lettre ASCII par NFD.

L'exemple utilise `$ascii($substring(dossier.fields.prenom, 0, 1) & dossier.fields.nom)` : `evandenbroeck`.

**Impacte :** `JsonataExpressionEngine`, `generer-bpmn.py`, le BPMN généré, le guide de conception, `OnboardingOrchestrationExampleIT`.
