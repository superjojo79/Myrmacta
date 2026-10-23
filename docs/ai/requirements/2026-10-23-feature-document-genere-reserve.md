---
phase: requirements
title: Réserver generatedDocumentId
description: Issue #201 — empêcher le rattachement d'un document d'un autre dossier par une variable forgée
---

# Exigences — `document-genere-reserve` (issue #201)

**Problème.** « Joindre le document » rattache comme `PRODUCED_DOC` l'objet désigné par la variable `generatedDocumentId`.
Une tâche placée entre « Générer un document » et « Joindre le document » permet, à sa complétion, de remplacer cette variable
par la référence d'un document d'un autre dossier. Ce document devient alors un document produit du dossier courant : il peut
être joint à un e-mail (§96) ou signé (§72).

**Objectif.** Aucune entrée utilisateur ne peut poser `generatedDocumentId` : démarrage manuel ou par l'API, MCP, complétion
de tâche, portail, callback, signal, variables d'un dossier enfant. Seul `DocumentGenerationTask` l'écrit.

**Non-objectifs.** Traçabilité serveur des documents générés (table de provenance) ; refus d'un objet déjà rattaché ailleurs.

**Acceptation.** Un test d'intégration reproduit l'attaque et échoue avant le correctif : une tâche s'intercale, sa complétion
pose la référence d'un autre dossier. Le test passe après le correctif, et le chemin nominal (génération, tâche, rattachement)
reste intact. `./mvnw verify`, `npm run test:all` et `scripts/check-docs.sh` sont verts.
