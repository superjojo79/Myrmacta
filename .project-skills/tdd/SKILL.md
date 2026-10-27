---
name: tdd
description: Myrmacta · Développement piloté par les tests — écrire un test qui échoue avant le code de production. À utiliser pour implémenter un comportement, ajouter une fonctionnalité ou corriger un bug.
---

# TDD

Rouge, vert, nettoyage. Dans cet ordre, à chaque fois.

> Adapté de `tdd` d'AI DevKit (codeaholicguy/ai-devkit, Apache-2.0), modifié pour Myrmacta : outils du projet,
> règle du code écrit avant son test assouplie, étape mémoire retirée.

## Règles

- Pas de code de production sans un test qui a d'abord échoué.
- Un test qui n'a jamais échoué ne prouve rien.
- Si du code a été écrit avant son test : écrire le test, le voir échouer en retirant ou en cassant le code, puis
  le remettre. Le test doit décrire le comportement, pas recopier le code.

## Cycle

1. **Rouge** — un test pour le comportement suivant. Le lancer : il doit échouer, et le message doit décrire le
   comportement manquant (pas une erreur de compilation ou d'import).
2. **Vert** — le code minimal qui le fait passer, rien de plus. Lancer le test.
3. **Nettoyage** — retirer la duplication dans le test et dans le code. Relancer : toujours vert. Appliquer
   `verify` avant d'annoncer.

Puis le comportement suivant.

## Où écrire le test dans Myrmacta

| Ce qui change | Test |
|---|---|
| Règle métier du domaine | test unitaire Java du domaine, sans Spring |
| Adaptateur JPA, Liquibase, requête | `*IT` sur PostgreSQL embarqué (zonky), jamais H2 |
| Endpoint REST ou outil MCP | test qui traverse la vraie chaîne HTTP et sécurité (pas seulement le contrôleur) |
| Frontière d'architecture | règle ArchUnit |
| Composant ou store Vue | Vitest |
| Parcours utilisateur, contrat consommé par le SPA | Playwright (`frontend/e2e/`), mocks alignés sur la vraie réponse |

Ciblé pendant le cycle (`./mvnw test -Dtest=NomDuTest`, `npx vitest run <fichier>`), complet avant d'annoncer.

## Pièges

| Piège | Problème | Correction |
|---|---|---|
| Test après coup | Le code façonne le test | Voir échouer le test avant de le croire |
| Tester l'interne | Le test casse au refactoring, pas au changement de comportement | Tester le comportement public |
| Rouge géant | Plusieurs comportements dans un test | Un comportement par test |
| Vert décoré | Du code qu'aucun test n'exige | Le retirer |
| Mocks partout | Les tests passent, le vrai code échoue | Vraies dépendances, mocks aux frontières seulement |
