---
phase: requirements
title: Requirements — LC3 (assistant de conception IA)
status: reviewed
created: 2026-10-01
feature: lc3-assistant-conception
lot: LC3
todo_ref: TODO.md §86 · analyse 41
---

# Requirements — LC3

## Problème
Concevoir types, formulaires, processus et règles demande de connaître les écrans et les formats. Un utilisateur métier doit pouvoir décrire son besoin en langage naturel et relire un résultat.

## Objectifs
- G1. Une conversation (écran *Admin → Assistant*) où l'assistant appelle le **catalogue d'outils de conception** pour proposer la configuration.
- G2. **Brouillons seulement** : processus BPMN et tables DMN restent à relire/publier par un humain ; l'assistant ne publie jamais.
- G3. Identité = celle de l'utilisateur de la session (aucun jeton de service), provenance `ASSISTANT`.
- G4. **Aucune donnée de dossier** à portée ; **aucun secret** écrit par l'assistant.
- G5. Plafonds de tours, d'appels d'outils et de jetons (conversation, tenant, jour) ; historique conservé 30 jours, propre à son auteur.
- G6. Désactivé par défaut ; clé et modèle par configuration serveur ; aucune requête sortante sinon.

## Hors périmètre
Exposition MCP (l'assistant *consomme* le catalogue, il n'en est pas un outil), streaming des réponses, panneau flottant, évaluation automatique de qualité par modèle réel (scénarios scriptés seulement), autres fournisseurs.

## Critères d'acceptation
- Sans configuration : `GET /api/assistant/status` → `enabled:false`, `POST /messages` → 503, aucun appel sortant.
- Le catalogue offert = outils de conception moins ceux refusés ; jamais d'outil d'opération.
- Un tour scripté (« type + processus ») dépose un brouillon `ASSISTANT` relisible dans Admin → Brouillons.
- Un secret de connecteur est refusé ; un résultat d'outil n'est jamais réinjecté comme consigne.
