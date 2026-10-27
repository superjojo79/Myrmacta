# §106 — Pré-vérification IA des dossiers et types de pièces justificatives

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (issue [#211](https://github.com/superjojo79/FlowForge/issues/211)), livré par lots : **P** (types de pièces,
PR #231), **A** (fournisseur et extraction, PR #233), **B** (moteur, brique, REST, MCP, PR #234), **C** (panneau, PR
#235) et **D** (spec, guides, exemple, évaluation). Spécification : [48-preverification-ia.md](../specs/48-preverification-ia.md).
La fonction reste désactivée par défaut (`APP_PRECHECK_ENABLED`). Cadrage : [`docs/analyses/fiche-preverification-ia.md`](../analyses/fiche-preverification-ia.md).

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
  serveur local (jeton factice envoyé) ; bornes validées même désactivé. `reasoning-effort` (vide par défaut) est
  transmis en `reasoning_effort` aux seuls fournisseurs compatibles OpenAI, et seulement s'il est configuré : envoyé
  d'office, il pourrait être refusé par un fournisseur qui ne le connaît pas ; absent, un modèle qui raisonne (Qwen 3
  sous Ollama, mesuré sur le jeu d'évaluation : 10 exécutions sur 10 en échec, 0 % → 93 % avec `none`) consomme le
  plafond de sortie en réflexion. Refusé avec Anthropic, où la réflexion n'est pas activée par la brique.
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

**Lot C — panneau sur la fiche (livré ; fonction toujours désactivée par défaut).**

- **D-PRECHECK-PANEL** — panneau « Pré-vérification » (région nommée) après les pièces de la fiche, masqué tant que la
  fonction est désactivée (404) ou qu'aucune exécution n'existe. Il affiche l'exécution la plus récente (les
  précédentes restent consultables), son statut, ses comptes, la synthèse et chaque constat avec son type, sa source et
  son origine : « IA » (et la confiance) pour ce que propose le modèle, « Contrôle » pour le déterministe, avec la
  mention que les constats sont indicatifs et que l'agent décide (transparence, AI Act). Avec EDIT et dossier non clos :
  accepter, écarter, annuler une décision (boutons nommés d'après le constat) et « Relancer » ; le panneau se rafraîchit
  toutes les 5 s, au plus 5 minutes, tant que l'exécution est en file ou en cours. Une exécution en échec affiche un
  motif traduit, jamais un message du fournisseur. `TENANT_ADMIN` : export JSON du journal de l'exécution affichée.
- La palette crée la brique avec `flowable:async="true"` (drapeau `async` du marqueur, catalogue v5) : déposée telle
  quelle, elle n'est plus refusée au déploiement.

**Lot D — documentation, exemple et évaluation (livré).** Spec `48-preverification-ia.md` ; guides de déploiement
(§20 : fournisseurs, `allow-plain-http`, RGPD, AI Act, évaluation), de conception BPMN (§3nonies) et utilisateur (§16).
L'exemple `exemples/autorisation-riverain` déclare trois types de pièces et une variante
`autorisation-riverain-preverification.bpmn20.xml` (contrôle code postal et commune par expression, puis la brique) ;
le scénario de la fiche est rejoué sur ces fichiers par `RiverainPrecheckExampleIT`. Le constat « doublon de plaque » de
la fiche est remplacé par l'unicité (§102) : la seconde demande est refusée à la création. Jeu d'évaluation :
`evaluation/cas.json` (10 cas, dont une consigne injectée), rejoué contre un vrai modèle par `PrecheckEvaluation` (hors
`verify`, rapport de précision et de rappel par type de constat) ; le harnais est vérifié dans `verify` avec des
modèles simulés.

**Rupture (P12).** Jusqu'ici, aucune fonction d'IA ne lisait de donnée de dossier (D-MCP-LC-MODES, D-ASSIST-TOOLS). La
pré-vérification ouvre cette porte, **et elle seule** : seuls les champs et pièces désignés par la brique, jamais un
élément sensible, partent vers le fournisseur configuré par l'instance ; l'assistant de conception reste sans aucune
donnée de dossier, et les outils MCP de la pré-vérification sont du mode opération.

**Cadrage (phases 1 à 3, versé depuis `docs/ai/` de la feature).**

- *Produit* (P1 à P13) : fournisseur compatible OpenAI ou Anthropic, choisi par instance ; pièces comprises ; brique
  asynchrone et bouton « Relancer » ; constats acceptés ou écartés, journalisés ; contrôles déclarés sur la brique ;
  PDFBox sans OCR ; sensibles toujours exclus de l'envoi ; configuration dédiée, désactivée par défaut, brique refusée
  au déploiement si désactivée ; tables dédiées et panneau ; faux fournisseur, exemple et jeu d'évaluation ; MCP lancer
  et lire ; une PR par lot.
- *Pièces* (R1 à R7) : type choisi au dépôt ; seules les pièces listées partent ; règles sur des variables posées
  avant ; échec d'une relance visible dans le panneau ; obligation déclarée dans la brique ; pièce sans type « non
  classée », reclassable ; types demandés sur la tâche externe.
- *Conception* (C1 à C8, HC1 à HC12) : `spring-ai-openai` 2.0.1 ; file persistée avec bail ; élément devenu sensible
  exclu et signalé ; attributs ligne par ligne ; variable de résultat déterministe ; complétude sur tous les champs du
  type ; plafonds prudents ; ordre P, A, B, C, D. *Écart* : le prompt `v1` vit dans `PrecheckPrompt` (texte versionné
  dans le code) plutôt que dans une ressource `precheck/prompt-v1.md` (HC2) ; même règle : un changement de texte crée
  une nouvelle version.

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

**Revue adversariale finale (lot D, 2026-10-26).**

- *Vérifié* — le scénario de bout en bout sur les fichiers de l'exemple : déploiement de la variante (contrôles de la
  brique passés), pièce d'identité sensible absente de la requête au fournisseur, constats attendus, décisions et journal
  exporté (modèle configuré, version du prompt). Après un retour du demandeur, le flux repasse par la brique (par
  construction du BPMN ; ce chemin n'est pas rejoué par le test).
- *Vérifié* — aucune donnée de dossier pour l'assistant de conception (test des modes MCP) ; routes et outils absents
  quand la fonction est désactivée ; panneau masqué.
- *Accepté* — dans l'exemple, les anciens champs `FILE` coexistent avec les types de pièces : la pré-vérification ne lit
  que les pièces jointes classées (documenté). La table code postal → commune est une démonstration.
- *Accepté* — l'export du journal (`TENANT_ADMIN`) n'est pas tracé dans le journal de sécurité : il ne contient que ce
  que le panneau montre, plus la spécification et l'empreinte de l'entrée.
- *Non fait ici* — l'évaluation contre un vrai modèle : aucune clé de fournisseur dans l'environnement de développement ;
  la commande est documentée (`DEPLOIEMENT.md` §20) et à jouer avant la mise en service.

**Revue adversariale complète (2026-10-26, après livraison des lots P à D).** Les revues précédentes, faites lot par
lot, n'avaient pas couvert le lot A ni le lot C, et aucune n'avait été menée avec la méthode `security-review` sur
l'ensemble. Acteurs considérés : tiers externe sans compte (dépôt de pièces par le portail), agent avec EDIT, client MCP
(éventuellement un agent IA), concepteur de processus, fournisseur et réseau non fiables.

- *Corrigé, moyenne (F1)* — le délai d'un appel s'arrêtait aux en-têtes : une passerelle qui distillait le corps
  retenait sans fin un thread du pool de jobs Flowable, partagé par tous les traitements asynchrones (prouvé : délai de
  2 s, appel de 8,5 s). Le client de l'assistant de conception avait le même défaut. Désormais l'échange n'aboutit
  qu'avec le corps reçu, borné à 4 Mo, et il est annulé au-delà du délai (`BoundedResponseBody`, deux clients).
- *Corrigé, moyenne (F2)* — les PDF déposés, y compris par un tiers, sont désormais analysés côté serveur : sans borne de
  temps, un PDF piégé retenait le thread à chaque exécution. Lecture interrompue au-delà de 20 s par pièce, délai vérifié
  à chaque opérateur (un formulaire imbriqué ne l'avale plus) ; statut « trop complexe » ; texte brut décodé seulement
  jusqu'à ce qui est gardé.
- *Corrigé, moyenne (F3)* — un agent avec EDIT ou un client MCP pouvait épuiser le plafond **de l'instance** par des
  relances en boucle, mettant toutes les briques en incident. Au plus 5 relances par dossier sur 24 h (réglable) ;
  au-delà de 80 % du plafond du jour, relances refusées (429), le reste gardé aux workflows.
- *Enchaînement* — F2 (ou F1) et les relances, réunis, pouvaient arrêter tout le traitement asynchrone de l'instance à
  partir d'une seule pièce de tiers : gravité élevée pour la disponibilité, levée par F1 à F3.
- *Corrigé, faible (F4)* — bail de 5 minutes plus court qu'une exécution possible (jusqu'à 600 s d'appel, plus la
  lecture) et écriture finale sans contrôle : deux répliques pouvaient appeler le modèle deux fois. Bail calculé sur le
  pire cas ; résultat rangé seulement par la tentative qui détient l'exécution.
- *Corrigé, faible (F5)* — relances, décisions et exports n'étaient pas tracés, et annuler une décision effaçait la
  précédente. Trois événements du journal de sécurité, décision précédente comprise. Remplace le point « Accepté » de la
  revue du lot D sur l'export.
- *Corrigé, faible (F6)* — l'empreinte SHA-256 incluait le nonce de l'exécution, jamais stocké : elle ne pouvait pas
  être recalculée et ne prouvait rien. Elle porte désormais sur la forme canonique (repère fixe à la place du nonce).
- *Corrigé, faible (F7)* — `get_dossier_precheck` rendait à un client MCP, peut-être un agent doté d'outils d'écriture,
  du texte produit à partir de pièces de tiers sans le signaler : marqué `untrusted`, avec une mise en garde.
- *Corrigé, faible (F8, F9)* — la cause d'une panne du fournisseur n'était pas journalisée (désormais : type d'erreur et
  statut HTTP, jamais le message) ; sans jetons déclarés par le fournisseur, le plafond ne comptait rien (désormais :
  estimation, un jeton pour quatre caractères).
- *Écarté, preuve à l'appui* — injection SQL (constantes seules, tout paramétré) ; relances cachées du SDK (une seule
  requête sur une erreur 500, testé) ; contournement des contrôles au déploiement (brouillons, publication et
  promotion passent par `validate`, et l'exécution revérifie la sensibilité) ; injection HTML dans le panneau
  (interpolations seulement) ; sortie des bornes du prompt (nonce inconnu, chevrons neutralisés) ; accès à une
  exécution ou un constat d'un autre dossier (filtré, testé) ; clé d'API (masquée dans `toString`, jamais
  journalisée).
- *Résiduel* — vulnérabilités futures de PDFBox (garder la dépendance à jour, activer l'antivirus) ; qualité réelle du
  modèle (évaluation à jouer) ; une injection peut encore tromper l'agent sur le fond d'un constat, marqué « IA » et
  laissé à sa décision ; un appel au modèle peut dépasser le plafond du jour d'une réponse (pas de réservation).

**Impacte :** `docs/specs/04-dossiers.md`, `docs/specs/13-modele-physique.md`, `docs/specs/15-serveur-mcp.md`,
`docs/specs/26-portail-externe.md`, `docs/specs/48-preverification-ia.md`, `docs/guides/GUIDE-UTILISATEUR.md`,
`docs/guides/GUIDE-CONCEPTION-BPMN.md`, `docs/guides/DEPLOIEMENT.md`, `exemples/autorisation-riverain/`,
`openapi/openapi.yaml`.
