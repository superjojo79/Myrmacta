---
name: verify
description: Myrmacta · Preuve avant annonce — exiger une sortie de commande fraîche avant de dire qu'une tâche, un correctif, un lot ou des tests sont terminés. À utiliser avant toute affirmation « ça marche », « c'est corrigé », « les tests passent ».
---

# Verify

Prouver que ça marche avant de dire que ça marche.

> Adapté de `verify` d'AI DevKit (codeaholicguy/ai-devkit, Apache-2.0), modifié pour Myrmacta : commandes du
> projet, étapes mémoire et suivi de tâches retirées.

## Règles

- Pas d'annonce d'achèvement sans sortie de commande fraîche, produite dans cette session.
- Mots interdits dans une annonce : « devrait », « probablement », « semble », « a priori », « je pense que ça
  marche ». Ils signalent une affirmation non vérifiée.
- Une sortie ancienne, mémorisée ou d'une autre session n'est pas une preuve : relancer.

## Porte en cinq temps

1. **Identifier** la commande qui prouve l'affirmation (une porte par commande s'il en faut plusieurs).
2. **Lancer** la commande complète, maintenant.
3. **Lire** toute la sortie : code de retour, nombre de succès et d'échecs.
4. **Confirmer** que la sortie prouve exactement ce qui est affirmé.
5. **Rapporter** : la commande, le code de retour, les chiffres clés.

Si une étape échoue : s'arrêter, corriger, reprendre au début. Sans commande de vérification possible, le dire et
demander comment vérifier.

## Preuves attendues dans Myrmacta

| Affirmation | Preuve exigée | Insuffisant |
|---|---|---|
| Le backend passe | `./mvnw verify` frais, 0 échec (ArchUnit et JaCoCo compris) | un test ciblé seul, les tests frontend |
| Les tests unitaires frontend passent | `cd frontend && npm run test`, 0 échec | le backend seul |
| Le frontend est propre | `cd frontend && npm run lint`, 0 erreur | un seul fichier vérifié |
| Les E2E passent | `cd frontend && npm run test:e2e` (ou `test:all`), 0 échec | Vitest seul ; « Playwright hors scope » ; « la CI le verra » |
| Feature qui touche le SPA ou un contrat qu'il consomme | Vitest **et** Playwright dans cette session ; mocks `frontend/e2e/**` alignés sur les nouvelles formes | des specs de composant avec des mocks axios |
| La doc est cohérente | `scripts/check-docs.sh`, tous les contrôles `ok` | des liens vérifiés à la main |
| Un bug est corrigé | le symptôme reproduit, puis la vérification de régression ci-dessous | « j'ai changé le code, ça devrait aller » |
| Un lot est terminé | chaque critère du lot vérifié un par un | « les tests passent, donc c'est fini » |

## Vérification de régression (correctif de bug)

1. Écrire le test qui couvre le bug.
2. Lancer : il **passe** (avec le correctif).
3. Retirer le correctif.
4. Lancer : il **échoue** (le test attrape bien le bug).
5. Remettre le correctif.
6. Lancer : il **passe**.

Si l'étape 4 passe, le test est faux : le réécrire.

## Excuses à refuser

| Excuse | Pourquoi c'est faux | À faire |
|---|---|---|
| « Le changement est trivial » | Les changements triviaux cassent souvent | Lancer la vérification |
| « Je l'ai lancé tout à l'heure » | Le code a changé depuis | Relancer |
| « Le test est instable » | Instable ne veut pas dire ignorable | Corriger l'instabilité ; jamais désactiver ni ignorer un test |
| « Ça compile » | Compiler n'est pas fonctionner | Lancer les tests |
| « La CI le verra » | La CI est un filet, pas un substitut | Vérifier en local |
| « L'agent a dit que c'était fini » | Une affirmation d'agent se vérifie aussi | Lire le diff, lancer les tests |
