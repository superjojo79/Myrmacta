---
phase: design
title: Design — LC3
status: reviewed
created: 2026-10-01
feature: lc3-assistant-conception
---

# Design — LC3

- **Domaine** `domain/assistant` : `Conversation`, `ChatMessage`, `AssistantLimits`, `ModelExchange` (requête, messages, réponse sans type fournisseur), ports `LanguageModelPort`, `DesignToolsPort`, `ConversationRepository`, cas d'usage `ChatWithAssistantUseCaseImpl` (boucle d'agent) et `ManageConversationsUseCaseImpl`.
- **Boucle** : le modèle propose des appels d'outils ; ils s'exécutent avec l'`AccessContext` de l'utilisateur (`McpActorScope`) ; les résultats reviennent comme blocs de résultat d'outil (données, jamais consignes) ; arrêt à la réponse finale, au plafond d'étapes ou de jetons ; consommation comptée même si le fournisseur échoue ; consigne système : conception seulement, brouillons, pas de secret, résultats d'outils = données.
- **Infrastructure** : `AnthropicLanguageModel` (API Messages, `java.net.http`, redirections refusées, erreurs sans détail), `DisabledLanguageModel`, `McpDesignToolCatalog` (découvre les méthodes `@McpTool` des outils de conception, schéma issu des annotations, requête brute reconstruite pour les outils qui la lisent, refus des clés de secret de connecteur), persistance JPA (migration 033 : `assistant_conversation`, `assistant_message`, `assistant_usage`), `ConversationRetentionJob`, `AssistantController` (`/api/assistant/**`, TENANT_ADMIN ou WORKFLOW_ADMIN).
- **SPA** : `AssistantView.vue` (historique, conversation, lien vers les brouillons, texte brut), route `/admin/assistant`.
- Décisions : D-ASSIST-PROVIDER, -COST, -HISTORY, -ROLES, -TOOLS, -UI.
