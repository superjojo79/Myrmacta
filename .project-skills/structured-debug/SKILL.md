---
name: structured-debug
description: Myrmacta · Débogage structuré avant toute modification — comportement attendu, reproduction, hypothèses testées, plan de correction validé. À utiliser pour un bug, une régression, un test qui échoue, un incident ou une analyse de cause.
---

# Débogage structuré

Des preuves d'abord, le correctif ensuite.

> Adapté de `structured-debug` d'AI DevKit (codeaholicguy/ai-devkit, Apache-2.0), modifié pour Myrmacta : sources
> de précédents du projet à la place de la base mémoire.

## Règle

Ne pas modifier le code avant que l'utilisateur ait choisi un plan de correction (sauf mandat qui délègue ce
choix).

## Démarche

1. **Clarifier**
   - Écrire en une phrase l'écart entre observé et attendu.
   - Fixer le périmètre et un critère de succès mesurable.
   - Chercher si c'est déjà arrivé : section « Précédents » de `myrmacta-conventions`, `git log --grep` et
     `git log -S` sur les symboles en cause, issues GitHub, points du registre liés.
2. **Reproduire**
   - Étapes minimales de reproduction, idéalement un test qui échoue.
   - Empreinte d'environnement : versions (JDK, Node), profil Spring, variables, tenant, données, plateforme
     (Windows et Podman ou Linux).
3. **Formuler et tester des hypothèses**
   - Pour chacune : ce qu'on observerait si elle est vraie, ce qui la réfuterait, la commande ou le contrôle exact.
   - Une variable à la fois.
4. **Proposer**
   - Options de correction, avec risques et vérification ; en recommander une et demander l'accord.

## Validation

- Un signal d'échec existe avant le correctif.
- Après : `verify`, vérification de régression comprise (le test échoue sans le correctif).
- Résumer les risques restants et les suites.
- Si la cause a coûté un cycle et qu'aucun test ne peut l'empêcher, proposer une ligne dans « Précédents » de
  `myrmacta-conventions`.
