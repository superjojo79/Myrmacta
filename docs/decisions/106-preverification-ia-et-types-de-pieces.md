# §106 — Pré-vérification IA des dossiers et types de pièces justificatives

> **Statut :** ouvert · point de premier niveau · [registre](README.md)

[ ] en cours (issue [#211](https://github.com/superjojo79/FlowForge/issues/211)), livré par lots : **P** (types de pièces)
[x] livré ; **A** (fournisseur et extraction), **B** (moteur et brique), **C** (panneau), **D** (documentation et
évaluation) à venir. Cadrage : [`docs/analyses/fiche-preverification-ia.md`](../analyses/fiche-preverification-ia.md).

**Constat.** Une brique de workflow doit pré-vérifier un dossier avant son instruction (complétude, règles, synthèse
avec constats sourcés), l'agent gardant la décision. Or une pièce jointe n'était qu'un élément `ATTACHMENT` à libellé
libre : rien ne disait qu'elle était « la preuve de domicile », ni qu'elle était sensible. Ni la complétude des pièces
ni l'exclusion des pièces sensibles n'étaient possibles. Le lot P comble ce manque ; il a une valeur propre, hors IA.

**Décisions tranchées par l'utilisateur (phases 1 à 3, 2026-10-25) — lot P.**

- **D-PIECE-TYPES** — le type de dossier déclare ses types de pièces (`pieceTypes` : clé
  `^[a-z][a-zA-Z0-9_]{0,59}$`, libellé de 100 caractères au plus, `sensitive`), 30 au plus, clés uniques ; colonne
  `dossier_type.piece_types` (changeSet `039`) ; exportés et importés par la promotion de configuration ; MCP
  `define_dossier_type` (absent = inchangé). Le caractère obligatoire d'une pièce ne vit pas sur le type mais dans la
  brique de pré-vérification, pour un stade du processus. Écarté : libellé de l'élément comparé sans casse (fragile) ;
  classement par le modèle (complétude non déterministe) ; obligation portée par le type.
- **D-PIECE-CLASSIFY** — le type choisi est la métadonnée `pieceKey` de l'élément `ATTACHMENT`, facultative au dépôt
  (fiche, MCP `add_dossier_element`, portail) ; clé inconnue du type du dossier refusée avant toute écriture dans le
  stockage. Une pièce sans type est « non classée » ; un agent avec EDIT l'attribue, la change ou la retire après coup
  (`PUT /api/dossiers/{id}/elements/{elementId}/piece-type`), dossier non clos ; tracé sur l'élément
  (`pieceClassifiedBy`, `pieceClassifiedAt`) et dans le journal de sécurité (`event=attachment_classified`, clés
  seulement). Retirer un type du type de dossier rend ses pièces non classées, sans les modifier. Écarté : type
  obligatoire au dépôt.
- **D-PIECE-PORTAL** — une tâche externe propose au tiers les types listés dans `flowable:portalPieceTypes` (clés
  séparées par des virgules, 10 au plus, forme vérifiée au déploiement) ; le tiers n'en choisit qu'un que la tâche
  propose **et** que le type du dossier déclare (sinon refus avant stockage) ; sans attribut, dépôt non classé comme
  avant. La vue du portail ne donne que la clé et le libellé, jamais `sensitive`. Écarté : classement par l'agent
  seulement.

**Décisions des lots suivants (tranchées, non encore livrées).** Fournisseur par API compatible OpenAI
(`spring-ai-openai`) et Anthropic ; configuration `app.precheck.*` désactivée par défaut ; brique asynchrone
`aiPrecheckTask` (incident rejouable) et bouton « Relancer » (file persistée avec bail) ; texte des pièces par PDFBox sans
OCR ; seules les pièces et champs désignés partent au modèle, jamais un élément sensible ; un appel sans outils, sortie
JSON validée par un schéma fermé ; variable de résultat déterministe seulement ; constats acceptés ou écartés par un
agent avec EDIT, journalisés. Ouverture des données de dossier à l'IA limitée à la pré-vérification : l'assistant de
conception reste sans aucune donnée de dossier (D-MCP-LC-MODES, D-ASSIST-TOOLS). Détail : `docs/ai/` de la feature
`preverification-ia`, versé ici au lot D.

**Revue adversariale du lot P (2026-10-25).**

- *Corrigé* — la trace du classement (`pieceClassifiedBy`, `pieceClassifiedAt`) pouvait être posée par l'appelant dans
  les métadonnées d'un nouvel élément (MCP `add_dossier_element`) : refusée, seul le reclassement l'écrit.
- *Corrigé* — `"pieceTypes": [null]` faisait une erreur 500 : refus 400. `sensitive` absent vaut `false`, comme
  `FieldDefinition.sensitive` (l'omettre donnait un refus 400).

- *Vérifié* — clé de type validée avant l'écriture dans le stockage (fiche et portail), puis de nouveau par le use case
  commun (fiche, portail, MCP) ; un tiers ne peut choisir qu'un type proposé par sa tâche ; reclassement réservé à EDIT,
  dossier non clos, élément du même dossier et du tenant (sinon 404) ; aucun libellé ni nom de fichier dans le journal
  de sécurité ; indicateur `sensitive` absent de la vue du portail.
- *Accepté* — un bundle de promotion antérieur au lot P ne déclare aucun type de pièce : l'importer vide la liste du
  type cible (même règle que `documentTemplateKeys`).

**Impacte :** `docs/specs/04-dossiers.md`, `docs/specs/13-modele-physique.md`, `docs/specs/15-serveur-mcp.md`,
`docs/specs/26-portail-externe.md`, `docs/guides/GUIDE-UTILISATEUR.md`, `docs/guides/GUIDE-CONCEPTION-BPMN.md`,
`openapi/openapi.yaml`.
