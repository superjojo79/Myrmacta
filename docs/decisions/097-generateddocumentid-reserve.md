# §97 — `generatedDocumentId` réservé : pas de rattachement d'un document d'un autre dossier

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] livré (issue [#201](https://github.com/superjojo79/FlowForge/issues/201), lot `document-genere-reserve`). Constat P1 de la
revue adversariale du §96. Docs SDD : [requirements](https://github.com/superjojo79/FlowForge/blob/dcb7e49/docs/ai/requirements/2026-10-23-feature-document-genere-reserve.md),
[design](https://github.com/superjojo79/FlowForge/blob/dcb7e49/docs/ai/design/2026-10-23-feature-document-genere-reserve.md),
[planning](https://github.com/superjojo79/FlowForge/blob/dcb7e49/docs/ai/planning/2026-10-23-feature-document-genere-reserve.md).

**Constat.** « Joindre le document » (`AttachGeneratedDocumentTask`) rattache comme `PRODUCED_DOC` l'objet désigné par la variable
`generatedDocumentId`, en ne contrôlant que son existence et le préfixe du tenant. Une tâche placée entre la génération et le
rattachement permettait, à sa complétion, de remplacer la variable par la référence du document d'un autre dossier. Ce document
devenait un document produit du dossier courant : joignable à un e-mail (§96) et signable (§72). Reproduit par
`GeneratedDocumentLaunderingIT` (rouge avant le correctif).

**D-DOC-GENERATED-RESERVED** ⚠ — `generatedDocumentId` rejoint `WorkflowVariables.RESERVED`, comme `connectorResult` (§78). Toute
entrée utilisateur est refusée (400) : démarrage (dialogue, API, MCP, dossier enfant, relais outbox), complétion de tâche, portail,
callback, signal. Seul `DocumentGenerationTask` l'écrit. Le SPA refuse le nom dans « Démarrer un workflow » ; un champ de
questionnaire ne peut plus porter ce nom (`isReservedOrKnownKey`). Options de l'issue écartées :
- (1) provenance enregistrée côté serveur : nouvelle table, sans gain tant que toutes les entrées sont gardées ;
- (3) refus d'un objet déjà rattaché à un autre dossier : requête sur `storage_ref` non indexée, et ne couvre pas un objet encore
  jamais rattaché.

**Changement de comportement.** Une entrée qui portait `generatedDocumentId` est refusée au lieu d'être acceptée. Pour MCP
`generate_document`, cette variable était déjà ignorée par le filtre des modèles.

**Impacte :** `docs/guides/GUIDE-CONCEPTION-BPMN.md` (brique « Générer un document »), `docs/guides/GUIDE-UTILISATEUR.md` (variables
de démarrage), §96 (constat P1). Aucune migration, aucune route, golden OpenAPI inchangé.
