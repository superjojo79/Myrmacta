# §106 — Pré-vérification IA des dossiers et types de pièces justificatives

> **Statut :** ouvert · point de premier niveau · [registre](README.md)

[ ] en cours (issue [#211](https://github.com/superjojo79/FlowForge/issues/211)), livré par lots : **P** (types de pièces)
[x] livré ; **A** (fournisseur et extraction) [x] livré ; **B** (moteur, brique, REST, MCP) [x] livré ; **C** (panneau),
**D** (documentation et évaluation) à venir. Cadrage : [`docs/analyses/fiche-preverification-ia.md`](../analyses/fiche-preverification-ia.md).

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

**Lot A — fournisseur et extraction (livré, sans effet visible : rien ne l'appelle encore).**

- **D-PRECHECK-PROVIDER** — `spring-ai-openai` 2.0.1 (module seul, sans starter ni auto-configuration) pour les
  fournisseurs compatibles OpenAI, réponse contrainte par `response_format: json_schema` strict ; Anthropic par le
  `ChatModel` de l'assistant, schéma donné dans les consignes. Transport `java.net.http` sans redirection ni relance,
  réponse bornée (même principe que l'assistant). Dépendance réellement tirée : `openai-java-core`, `okhttp` 4.12 (déjà
  présent) ; `azure-identity` n'est pas tiré ; `kotlin-stdlib-common` 1.9.10 exclu comme pour Anthropic.
- **D-PRECHECK-CONFIG** — `app.precheck.*`, désactivé par défaut et distinct de `app.assistant.*` ; HTTPS hors boucle
  locale, sauf `allow-plain-http=true` (réseau interne, données en clair : risque documenté) ; clé facultative pour un
  serveur local (jeton factice envoyé) ; bornes validées même désactivé.
- **D-PRECHECK-BUDGET** — plafond de jetons par jour UTC et par instance, vérifié avant chaque appel (pas de
  réservation : un appel en cours peut dépasser d'une réponse).
- **D-PRECHECK-EXTRACT** — PDFBox 3.0.8 : PDF à couche texte et texte brut UTF-8, pages et caractères bornés, lecture en
  mémoire bornée (64 Mo, aucun fichier temporaire) ; image, scan, PDF chiffré, illisible ou format non pris en charge
  donnent un statut, jamais une erreur.

**Lot B — moteur, brique, REST et MCP (livré ; fonction toujours désactivée par défaut, sans panneau).**

- **D-PRECHECK-SPEC** — la brique « Pré-vérification IA » (`aiPrecheckTask`, catalogue v4) ne lit que des attributs
  littéraux : `precheckPieces` (une ligne `clé : obligatoire, lire` ; 30 types, 10 à lire), `precheckFields` (30 champs
  séparés par des virgules), `precheckQuestions` (une ligne `clé = question` ; 20 questions de 300 caractères),
  `precheckRules` (une ligne `variable:<nom> <condition> => MANQUANT|INCOHERENCE|A_VERIFIER : message` ; 10 règles ;
  conditions `est vrai`, `est faux`, `est vide`, `n'est pas vide`, `> N`, `< N`, `= N`, `= "texte"`, une valeur d'un
  autre type ne satisfaisant jamais une comparaison) et `precheckResult` ; au moins une pièce, question ou règle.
- **D-PRECHECK-BLOCK** — brique toujours `flowable:async="true"` (l'appel ne tient pas dans la transaction d'une
  complétion ; son échec devient un incident rejouable). Au déploiement : refus tant que `APP_PRECHECK_ENABLED` est
  faux ; contrôle contre chaque type de dossier lié au processus (`workflowKey`) : champ transmis connu et non sensible,
  type de pièce connu et jamais lu s'il est sensible (`obligatoire` seul accepté). Un type modifié après le
  déploiement est rattrapé à l'exécution : élément devenu sensible ou inconnu non transmis, constat « À vérifier ».
- **D-PRECHECK-PASSES** — trois passes, du plus fiable au moins fiable : complétude déterministe (champs obligatoires du
  type, y compris par règle et sensibles en présence seulement ; pièces `obligatoire` sans élément de ce type) ; règles
  sur des variables posées avant (variable absente : « règle non évaluée ») ; un seul appel au modèle, sans outils, sur
  les seuls champs et pièces désignés non sensibles (pièces les plus récentes d'abord, bornes C7, pièce illisible ou
  tronquée signalée). Pas d'appel s'il n'y a rien à transmettre. Le calcul ne lève jamais : budget atteint, fournisseur
  indisponible, fonction désactivée ou réponse non JSON donnent une exécution `FAILED` qui garde les constats
  déterministes ; la brique lève alors (incident), la relance par la file non.
- **D-PRECHECK-PROMPT** — consignes versionnées (`v1`, rangée dans le journal), contenu encadré par des bornes propres à
  l'exécution (nonce aléatoire), triples chevrons des données neutralisés ; schéma de sortie fermé
  (`additionalProperties: false`). Sortie validée avant l'agent : type et source connus (champ ou pièce transmis,
  question posée ; jamais une règle), message non vide de 300 caractères au plus, confiance dans [0, 1], 30 constats au
  plus ; le reste est écarté et compté (`discarded`). Une question sans réponse exploitable devient « À vérifier ». La
  variable de résultat ne porte que les comptes déterministes (`status`, `missing`, `inconsistent`, `runId`) : un
  routage ne dépend jamais du modèle.
- **D-PRECHECK-STORE** — tables `precheck_run` et `precheck_finding` (changeSet `040`, supprimées avec le dossier) :
  déclencheur, auteur, spécification, statut, fournisseur et modèle, version du prompt, empreinte SHA-256 de l'entrée
  (jamais l'entrée en clair), synthèse de 800 caractères, jetons, constats et décisions. Écritures dans leur propre
  transaction : une exécution en échec reste rangée même si le job du workflow est annulé (HC5). Lecture : droit de
  lecture du type ; décision (accepter, écarter, annuler) : EDIT, dossier non clos, exécution terminée, qui et quand
  tracés ; export du journal : `TENANT_ADMIN`. REST `GET|POST /api/dossiers/{id}/prechecks`,
  `PUT …/{runId}/findings/{findingId}/decision`, `GET …/{runId}/export`, présents seulement si la fonction est activée.
- **D-PRECHECK-QUEUE** — « Relancer » (REST, MCP `run_dossier_precheck`) reprend la spécification de la dernière
  exécution (la brique lance la première) et met une exécution en file : une seule en attente ou en cours par dossier
  (index unique partiel, 409 sinon), prise par le relais avec un bail de 5 minutes en `SKIP LOCKED`
  (`APP_PRECHECK_QUEUE_POLL_DELAY`, 5 s), reprise si le bail expire, abandonnée après 2 tentatives ; règles évaluées sur
  les variables courantes du process. MCP `get_dossier_precheck` lit les 3 dernières ; les deux outils sont du mode
  opération, absents de l'assistant de conception (D-ASSIST-TOOLS).

**Décisions des lots suivants (tranchées, non encore livrées).** Panneau « Pré-vérification » sur la fiche (lot C) :
marquage IA, accepter ou écarter, relancer ; la palette pose `flowable:async="true"` sur la brique. Lot D : spec, guides
(fournisseurs, RGPD, AI Act), exemple riverain et jeu d'évaluation ; détail : `docs/ai/` de la feature
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

**Revue adversariale du lot B (2026-10-26).**

- *Corrigé* — `Condition` « < N » était vraie pour une valeur non numérique : une valeur d'un autre type (texte, NaN,
  infini) ne satisfait plus aucune comparaison.
- *Corrigé* — le relais de la file avait deux constructeurs sans `@Autowired` : le contexte ne démarrait pas dès que la
  fonction était activée (trouvé par le test de bout en bout).
- *Vérifié* — « sensible nulle part » : ni la valeur d'un champ sensible, ni le texte ou l'intitulé d'une pièce sensible
  dans la requête au fournisseur ni dans les journaux (test de bout en bout avec le faux fournisseur) ; un sous-champ de
  groupe ne peut pas être sensible (le groupe l'est entier) ; une injection dans une pièce ne peut pas fermer la borne
  (nonce, chevrons neutralisés) et sa sortie reste contrainte (sources connues, longueurs) ; message du fournisseur
  jamais relayé (motif générique) ; lectures filtrées par tenant ; décision sur un constat d'une autre exécution ou d'un
  autre dossier : 404.
- *Accepté* — un échec du fournisseur est retenté par Flowable (3 tentatives par défaut) : chaque tentative est une
  exécution `FAILED` rangée et un appel compté dans le budget. Un constat du modèle peut contenir un texte trompeur
  dicté par une pièce (injection) : il est marqué « modèle », sourcé, et l'agent décide. Un champ masqué par
  `visibleWhen` mais désigné dans `precheckFields` est transmis (choix explicite du concepteur).

**Impacte :** `docs/specs/04-dossiers.md`, `docs/specs/13-modele-physique.md`, `docs/specs/15-serveur-mcp.md`,
`docs/specs/26-portail-externe.md`, `docs/guides/GUIDE-UTILISATEUR.md`, `docs/guides/GUIDE-CONCEPTION-BPMN.md`,
`openapi/openapi.yaml`.
