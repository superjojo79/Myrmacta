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

**Avant de déployer** (revue L3, I2). Un questionnaire déjà enregistré avec un champ `generatedDocumentId` ne se lirait plus :
le contrôle des noms réservés s'applique aussi au chargement, comme pour `connectorResult`. Les instances déjà en cours dont
la variable aurait été forgée la rattacheront après le déploiement. Requêtes de contrôle, sur le schéma du tenant :
`SELECT schema_key FROM task_question_schema WHERE fields::text LIKE '%"generatedDocumentId"%';` (doit être vide) et
`SELECT storage_ref FROM dossier_element WHERE type = 'PRODUCED_DOC' GROUP BY storage_ref HAVING COUNT(DISTINCT dossier_id) > 1;`
(un document produit rattaché à plusieurs dossiers trahit un blanchiment passé).

**Revue adversariale (2026-10-23, sous-agent, aucun High, aucun contournement)** — toutes les entrées qui posent des variables
passent par `assertNoReservedKeys`. Cela couvre les déclencheurs de connecteurs (webhook, IMAP/Graph, Service Bus, Kafka,
batch, planificateur) via l'outbox, les maps imbriquées contrôlées à leur entrée (`childWorkflowVariables`,
`connectorInput.variables`, mapping de callback), et aucun outil MCP ne complète ni ne corrèle une tâche. La création d'un
`PRODUCED_DOC` est refusée par l'API REST et par MCP.
- **I1 (corrigé)** — un test vérifie que la liste du SPA reste celle du serveur (`ReservedVariablesFrontendConsistencyTest`).
  Changements visibles : un événement d'outbox de démarrage déjà en attente qui porterait la clé passe `DEAD` ; un dossier
  enfant qui la transmet crée un incident ; MCP `generate_document` répond 400 au lieu d'ignorer la clé.
- **L3 (accepté, contrôle avant déploiement ci-dessus)** — questionnaire existant portant ce nom.
- **L1 (préexistant, issue [#203](https://github.com/superjojo79/FlowForge/issues/203))** — le concepteur peut recopier une valeur dans un nom réservé : `elementVariable` d'une
  boucle multi-instance, mappings `flowable:in`/`flowable:out` d'un `callActivity`. Cela vaut pour toutes les variables
  réservées, `dossierId` compris. Correctif proposé : refus au déploiement d'une cible réservée, sauf recopie à l'identique.
- **L2 (préexistant, issue [#204](https://github.com/superjojo79/FlowForge/issues/204))** — une colonne de sortie DMN peut porter un nom réservé (valeur littérale du concepteur).
- **L4 (préexistant, issue [#205](https://github.com/superjojo79/FlowForge/issues/205))** — le connecteur `document-storage` lit ou supprime n'importe quelle référence du tenant
  (`connectorInput` posable à la complétion) : les références de stockage ne sont rattachées à aucun dossier.
- **I3 (préexistant)** — deux générations dans des branches parallèles écrasent la même variable (même dossier).
- **I4 (accepté)** — l'IT couvre la complétion REST ; portail, callback et signal partagent la même garde.

**Impacte :** `docs/guides/GUIDE-CONCEPTION-BPMN.md` (brique « Générer un document »), `docs/guides/GUIDE-UTILISATEUR.md` (variables
de démarrage), §96 (constat P1). Aucune migration, aucune route, golden OpenAPI inchangé.
