---
phase: requirements
title: Requirements — D3c (e-mail entrant) et D3d (Azure Service Bus)
status: reviewed
created: 2026-10-01
feature: d3cd-email-entrant-bus-azure
lot: D3c + D3d
todo_ref: TODO.md §87 · analyse 34
---

# Requirements — D3c + D3d

## Problème
Les réclamations arrivent par e-mail et les Business Events de D365 F&O sur Azure Service Bus ; FlowForge ne sait recevoir que des webhooks, Kafka, du SFTP.

## Objectifs
- G1. **E-mail entrant** : un message crée un dossier ou alimente celui dont la référence figure dans l'objet ; corps et expéditeur mappés ; pièces jointes rattachées après les contrôles de l'upload (types, contenu, antivirus).
- G2. **Azure Service Bus** : un message JSON d'une file ou d'un abonnement crée ou alimente un dossier (même mapping que Kafka).
- G3. Authentification : mot de passe ou OAuth2 `client_credentials` (D3a) ; SAS pour le bus. Aucun secret affiché ni journalisé.
- G4. Bornes sur tout (taille, nombre, durée) ; refus des réponses automatiques (pas de boucle) ; hôtes publics seulement.
- G5. Plusieurs répliques : une seule lit une boîte ou une file à la fois (D5).
- G6. Exposition MCP : via `list_connector_definitions` / `define_connector_instance` (schéma déjà exposé) ; **sans secret par l'assistant** (P9).

## Hors périmètre
Microsoft Graph, Azure Event Grid, détection de doublon par `Message-ID`, réponse automatique à l'expéditeur, lecture temps réel (AMQP).

## Critères d'acceptation
- Un e-mail avec pièce jointe saine crée un dossier avec son élément `ATTACHMENT` ; une pièce infectée est écartée sans perdre le dossier.
- Une réponse « RE: [DOS-…] » alimente ce dossier ; une référence inconnue crée un nouveau dossier.
- Un message de bus converti est retiré de la file ; un message illisible est rendu au courtier.
- Un hôte privé ou hors domaines Service Bus est refusé à la création de l'instance.
