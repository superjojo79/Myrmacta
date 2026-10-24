# §102 — Unicité d'un champ de dossier

> **Statut :** ouvert · point de premier niveau · [registre](README.md)

[ ] en cours (issue [#184](https://github.com/superjojo79/FlowForge/issues/184)). Lot A (unicité, serveur et MCP) et lot B
(brique « Rechercher des dossiers ») livrés ; restent l'interface (lot C) et l'exemple riverain (lot D).

**Constat.** Aucun champ de dossier ne pouvait être déclaré unique. Dans l'exemple « autorisation riverain »
(`exemples/autorisation-riverain/DIFFICULTES.md` §1), « une seule demande par plaque » reposait sur une case cochée par
l'agent ; l'autorisation tacite accordait d'office un doublon jamais instruit.

**Règle.** Un champ `unique` (réglage de `FieldDefinition`) : une valeur ne figure que dans **un seul dossier non clôturé**
du type. Toute écriture qui créerait un doublon est refusée, y compris sous écritures concurrentes.

- **D-UNIQUE-SCOPE** (tranché par l'utilisateur) — portée : dossiers du même type ni `CLOTURE` ni `ARCHIVE`. La clôture
  libère la valeur ; aucun retour d'un statut clôturé vers un statut ouvert n'existe (`DossierStatusTransitions`).
  Écarté : unicité absolue ; portée réglable par champ.
- **D-UNIQUE-COMPARE** (tranché par l'utilisateur) — égalité stricte sur la forme canonique : texte tel quel, nombre sans
  zéro non significatif ni exposant (`1.50` = `1.5`, `1E+2` = `100`), date ISO. La normalisation métier (casse, espaces,
  tirets) passe par un champ calculé, comme `plaqueNormalisee`. Écarté : comparaison insensible à la casse et aux espaces.
  Conséquence assumée : des caractères visuellement identiques mais distincts (espace final, homoglyphe) ne sont pas des
  doublons, sauf si la formule les normalise.
- **D-UNIQUE-EXISTING** (tranché par l'utilisateur) — activer `unique` alors que des dossiers ouverts partagent une valeur :
  la mise à jour du type est refusée (409), avec les valeurs en double et les références (`duplicates`). Écarté : tolérer
  les doublons existants.
- **D-UNIQUE-STORE** (tranché par l'utilisateur) — garantie en base : table `dossier_unique_value` (changeSet `037`), clé
  primaire `(tenant_id, dossier_type_code, field_name, field_value)`, tenue à jour par `JpaDossierRepositoryAdapter.save`
  dans la transaction de l'écriture (`insert … on conflict do nothing`), reconstruite par
  `JpaDossierTypeRepositoryAdapter.save`, supprimée avec le dossier (`on delete cascade`). Toutes les écritures passent par
  `DossierRepository.save` (création, correction, formulaire de tâche, `fieldMappingTask`, dossier enfant, déclencheurs,
  MCP, statut). Écarté : verrou consultatif + requête (la garantie dépendrait de chaque chemin) ; index unique partiel par
  champ (DDL à l'exécution, hors Liquibase).
- **D-UNIQUE-TYPE-LOCK** (tranché par l'utilisateur) — verrou consultatif par type, clé
  `hashtext(current_schema() || ':' || tenant || ':' || type)` : partagé à l'enregistrement d'un dossier, exclusif à celui
  du type. Un dossier écrit pendant qu'on active `unique` ne peut pas échapper à la reconstruction. Écarté : `FOR SHARE` sur
  la ligne du type (point chaud multixact) ; aucun verrou et contrôle différé.
- **D-UNIQUE-HTTP** (tranché par l'utilisateur) — un doublon est une erreur de champ : 400, `errors[]` avec `code: "UNIQUE"`
  et `reference` (dossier détenteur). Le message de l'exception nomme désormais chaque champ en erreur, son code et son
  explication, jamais la valeur : c'est ce que reçoit un appelant MCP. Écarté : 409 dédié ; message général seulement.

Choix faits sans arbitrage :
- Types autorisés `STRING`, `NUMBER`, `DATE` ; refusé sur un champ sensible (le refus d'un doublon serait un oracle), sur un
  sous-champ de groupe, et pour un nom de plus de 100 caractères ; permis sur un champ calculé. Une valeur vide n'entre
  jamais en conflit. Une valeur unique a au plus 500 caractères (`INVALID_VALUE`) : la clé tient sous la limite d'une entrée
  B-tree.
- Les clés sont calculées sur le dossier **recalculé par les règles** (`DossierUniqueKeys`), pas sur les valeurs stockées :
  un champ calculé ajouté au type après coup, ou dont la formule change, protège aussitôt les dossiers existants, et un
  simple changement de statut n'efface pas la protection.
- Pré-contrôle (`DossierUniquenessCheck`) dans la création, la correction et l'essai à blanc MCP : l'erreur `UNIQUE` arrive
  avec les autres erreurs de champ. La base reste l'autorité.

**Tests.** `FieldUniqueModelTest`, `UniqueFieldKeysTest`, `DossierUniqueKeysTest`, tests des use cases et de l'outil MCP ;
`DossierUniqueValueIT` (doublon, clôture, purge, deux créations concurrentes) ; `DossierUniqueTypeIT` (activation refusée
ou acceptée, retrait, champ calculé ajouté après coup, course entre l'activation et une écriture) ;
`UniqueFieldApiTest` (chaîne HTTP) ; `UniqueFieldWorkflowIT` (`fieldMappingTask`). Le verrou du type, la synchronisation à
l'enregistrement et le recalcul des règles ont chacun été retirés à titre d'essai : les tests concernés échouent.

**Revue adversariale du lot A (2026-10-24).**
- *Corrigé* — un champ calculé unique ajouté à un type existant n'a pas de valeur stockée : la reconstruction et la
  synchronisation lisaient les valeurs stockées, et un changement de statut aurait effacé la clé. Les clés viennent
  désormais du dossier recalculé par les règles.
- *Corrigé* — l'essai à blanc MCP (`create_dossier`, `dryRun`) annonçait « valide » un doublon : pré-contrôle ajouté.
- *Corrigé* — un appelant MCP ne recevait que « Dossier field validation failed » : le message nomme champs, codes et
  détenteurs.
- *Corrigé* — webhook entrant dont le dossier est refusé à la validation de ses champs (défaut antérieur, rendu plus
  fréquent par l'unicité) : aucun gestionnaire, d'où un 500 et une clé d'idempotence jamais soldée, donc des réessais sans
  fin. Désormais 400 déterministe, clé soldée, corps limité aux champs et codes (pas de référence vers un système externe).
  Un doublon reste refusé avec la même clé pendant la durée de vie de l'idempotence, même si le détenteur est clôturé
  entre-temps : l'émetteur envoie un nouvel événement.
- *Accepté* — le refus `UNIQUE` révèle la référence du détenteur à qui peut écrire dans ce type (contrôle d'accès par type,
  décision n° 20) ; caractères distincts mais semblables non détectés (D-UNIQUE-COMPARE) ; coût d'un enregistrement de
  dossier : un verrou consultatif et une relecture du type, plus les écritures de la table pour un type à champ unique ;
  coût d'un enregistrement de type à champ unique : tous ses dossiers relus, comme le contrôle de compatibilité.
- *Vérifié* — aucune écriture de `dossier` hors `DossierRepository.save` (pas de requête `@Modifying`, pas de cascade) ;
  purge par `deleteById` (cascade de la table) ; insertions des clés dans un ordre stable et verrous pris en tête de
  transaction, sans cycle possible ; l'éditeur de schéma du SPA conserve les propriétés qu'il ne connaît pas (`unique`
  survit à un enregistrement depuis l'interface, en attendant le lot C).

**Brique « Rechercher des dossiers » (lot B).** `${dossierSearchTask}` compte les dossiers en cours du type du dossier
courant, lui exclu, dont 1 à 3 champs cherchables valent les valeurs données (`searchCriteria` : lignes
`champ = dossierField:<champ>` ou `champ = variable:<nom>`), et pose le nombre et la référence du plus ancien dans les
variables nommées par `searchCountResult` et `searchFirstReferenceResult`.

- **D-SEARCH-BLOCK-SCOPE** (tranché par l'utilisateur) — type courant seulement, dossiers ni `CLOTURE` ni `ARCHIVE`, dossier
  courant exclu, égalité stricte. Écarté : type cible déclaré dans le BPMN (un workflow lirait des dossiers d'un type que son
  concepteur ne voit pas : règle d'accès à trancher) ; tous les opérateurs de la recherche.
- **D-SEARCH-BLOCK-OUTPUT** (tranché par l'utilisateur) — un nombre (entier) et la référence du plus ancien (date de création,
  puis identifiant), `null` si aucun. Écarté : nombre seul ; liste de références (variable non scalaire).

Choix faits sans arbitrage :
- Champ cherché déclaré `searchable` (donc non sensible), vérifié **à l'exécution** : le type du dossier n'est pas connu au
  déploiement. Source `dossierField:` déclarée et non sensible (R8), sinon le nombre serait un oracle sur la valeur masquée.
  Un manquement est une erreur de conception : incident rejouable.
- Une valeur vide, ou qu'aucun dossier ne peut tenir (texte dans un champ nombre, date mal formée, `NUL`), donne 0 sans
  requête. Une valeur structurée (objet, liste) est une erreur de conception. Une valeur de `SELECT` se cherche telle
  quelle : une option retirée du type reste stockée dans d'anciens dossiers (revue adversariale).
- Attributs littéraux seulement, validés au déploiement et à l'exécution (mêmes règles que `formulaResult` pour les
  sorties, deux noms distincts). Égalité servie par l'index GIN (`flow_jsonb_contains`).
- La brique n'est pas une garantie : deux exécutions simultanées peuvent toutes deux trouver 0. La garantie est `unique`.

**Tests du lot B.** `DossierSearchSpecTest`, `SearchSimilarDossiersUseCaseImplTest` (portée, conversion, valeur vide,
champ non cherchable, source sensible ou non déclarée, valeur structurée, bornes), `BlockAttributeRulesTest`,
`BuildingBlockCatalogTest`, `FlowableBpmnParserAdapterBlockTest` ; `DossierOpenMatchesIT` (PostgreSQL : statuts, type,
dossier courant, plus ancien, nombres par valeur) ; `DossierSearchTaskIT` (moteur : nombre et référence, source variable,
incident, refus au déploiement).

**Revue adversariale du lot B (2026-10-24).**
- *Corrigé* — une valeur de `SELECT` qui n'est plus une option donnait 0, alors que d'anciens dossiers la tiennent.
- *Vérifié* — les critères et les sorties ne viennent que du BPMN (délégué en lecture littérale) ; `variable:` ne lit qu'une
  valeur scalaire (`dossierFields`, `connectorResult` et autres structures sont refusés) ; seuls un nombre et une référence
  sortent ; une réponse de portail ne pose que les réponses de son questionnaire, jamais un nom choisi par le tiers.
- *Accepté* — celui qui complète une tâche peut poser la variable source d'un critère `variable:` et apprendre, par la
  passerelle ou la référence, si un dossier en cours du même type tient une valeur : c'est ce que sa recherche de dossiers lui
  permet déjà (même type, champs cherchables). Le guide de conception demande de ne pas transmettre la référence à un tiers
  externe sans l'avoir voulu.

**Impacte :** `docs/specs/04-dossiers.md`, `docs/specs/13-modele-physique.md`, `docs/guides/GUIDE-UTILISATEUR.md`,
`docs/guides/GUIDE-CONCEPTION-BPMN.md` (§3octies et §7, lot B ; palette au lot C), golden OpenAPI (`FieldDefinition.unique`), catalogue des briques en version 3 (bloc `search-dossiers`),
description MCP de `define_dossier_type`. Migration `037` (nouvelle table, vide au déploiement : elle se remplit à
l'activation de `unique` sur un type).
