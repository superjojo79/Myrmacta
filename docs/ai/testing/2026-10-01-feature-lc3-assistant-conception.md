---
phase: testing
title: Testing — LC3
status: reviewed
created: 2026-10-01
feature: lc3-assistant-conception
---

# Testing — LC3

| Niveau | Fichier | Couvre |
|---|---|---|
| Domaine | `ChatWithAssistantUseCaseTest` | scénario scripté (type + processus), identité, journal et jetons, échec d'outil renvoyé au modèle, boucle infinie coupée, injection par un résultat d'outil, conversation privée, plafonds (tours, jour, conversation), rôles, échec fournisseur compté, troncature |
| Infra | `AnthropicLanguageModelTest` | requête (clé en-tête seulement, outils, résultats), lecture des appels d'outils, erreur sans fuite, redirection refusée, propriétés (clé, https) |
| Intégration | `AssistantIntegrationTest` | inactif par défaut (503), rôles, conversations privées, catalogue = conception − refusés, schémas, outils refusés, secrets de connecteur refusés, brouillon à provenance `ASSISTANT`, rôle insuffisant, compteur par jour, purge |
| Vitest | `AssistantView.spec.ts` | inactif, envoi, texte brut, identifiant de conversation, erreur, historique |
| Playwright | E2E-ASSIST-1 | notice d'inactivité puis conversation produisant des brouillons |
