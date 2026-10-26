# Pré-vérification IA des dossiers

> **Statut : livré** (TODO §106, issue [#211](https://github.com/superjojo79/FlowForge/issues/211), lots P, A, B, C, D).
> Décisions et revues : [registre §106](../decisions/106-preverification-ia-et-types-de-pieces.md). Cadrage :
> [fiche](../analyses/fiche-preverification-ia.md). Désactivée par défaut (`APP_PRECHECK_ENABLED=false`).

Une brique de workflow prépare l'instruction d'un dossier : elle contrôle sa complétude, tire des constats de règles
posées par le processus et pose des questions fermées à un modèle de langage sur les champs et pièces désignés. Elle
rend une synthèse et des **constats sourcés** ; l'agent accepte ou écarte chacun. **Rien n'est décidé
automatiquement** : la pré-vérification n'avance pas le workflow sur l'avis du modèle et n'écrit dans aucun champ du
dossier (RGPD art. 22).

## 1. Types de pièces justificatives (lot P)

Prérequis, utile aussi sans IA ([04-dossiers.md](04-dossiers.md#types-de-pièces-justificatives-todo-106-issue-211-lot-p)) :

- le type de dossier déclare ses `pieceTypes` (clé, libellé, `sensitive`), 30 au plus (`D-PIECE-TYPES`) ;
- une pièce jointe porte son type dans la métadonnée `pieceKey`, choisie au dépôt (fiche, MCP, portail) ou reclassée
  plus tard par un agent avec EDIT, journalisé (`D-PIECE-CLASSIFY`) ; sans type, elle est « non classée » ;
- une tâche externe propose au tiers les types de `flowable:portalPieceTypes` (`D-PIECE-PORTAL`).

Une pièce d'un type **sensible** n'est jamais lue par un traitement automatisé ; seule sa présence peut être contrôlée.

## 2. La brique « Pré-vérification IA » (`aiPrecheckTask`)

```xml
<serviceTask id="preverification" name="Pré-vérification IA"
             flowable:delegateExpression="${aiPrecheckTask}" flowable:async="true"
             flowable:precheckPieces="preuveDomicile : obligatoire, lire&#10;pieceIdentite : obligatoire"
             flowable:precheckFields="nom, prenom, codePostal, commune"
             flowable:precheckQuestions="domicileAuNom = La preuve de domicile est-elle au nom du demandeur ?"
             flowable:precheckRules="variable:codePostalIncoherent est vrai =&gt; INCOHERENCE : Code postal incohérent."
             flowable:precheckResult="preverification"/>
```

Attributs **littéraux** (jamais une variable), validés au déploiement et de nouveau à l'exécution (`D-PRECHECK-SPEC`) :

| Attribut | Forme | Bornes |
|---|---|---|
| `precheckPieces` | une ligne `clé : obligatoire, lire` (l'un, l'autre ou les deux) | 30 types, 10 à lire |
| `precheckFields` | champs envoyés au modèle, séparés par des virgules | 30 |
| `precheckQuestions` | une ligne `clé = question fermée` | 20 questions, 300 caractères |
| `precheckRules` | une ligne `variable:<nom> <condition> => MANQUANT\|INCOHERENCE\|A_VERIFIER : message` | 10 règles, message de 200 caractères |
| `precheckResult` | variable qui reçoit les comptes déterministes (facultatif) | — |

Conditions d'une règle : `est vrai`, `est faux`, `est vide`, `n'est pas vide`, `> N`, `< N`, `= N`, `= "texte"`. Une
valeur d'un autre type (texte comparé à un nombre, NaN) ne satisfait jamais une comparaison. Il faut au moins une pièce,
une question ou une règle.

**Au déploiement** (`D-PRECHECK-BLOCK`) : la brique est refusée si la fonction est désactivée, si elle n'est pas
`flowable:async="true"` (la palette le pose), et, pour chaque type de dossier lié au processus (`workflowKey`), si un
champ transmis est inconnu ou sensible, si un type de pièce est inconnu, ou si un type sensible est marqué `lire`. Un
type modifié après le déploiement est rattrapé à l'exécution : l'élément devenu sensible ou inconnu n'est pas transmis
et un constat « À vérifier » le dit.

## 3. Le calcul, en trois passes (`D-PRECHECK-PASSES`)

1. **Complétude, déterministe** : chaque champ obligatoire du type (fixe ou rendu obligatoire par une règle du type,
   sensibles compris, en présence seulement) et chaque pièce `obligatoire` sans élément de ce type donnent un constat
   `MISSING`. Une pièce non classée ne compte pas.
2. **Règles** : constats tirés de variables posées avant (DMN, expression, recherche de doublons) ; une variable absente
   donne « Règle non évaluée ».
3. **Modèle** : un seul appel, **sans outils**, sur les seuls champs désignés non sensibles et le texte des pièces `lire`
   non sensibles (les plus récentes d'abord, bornées en pièces, pages et caractères). Pièce scannée, chiffrée ou
   illisible : constat « non lisible », pas d'erreur. Rien à transmettre : pas d'appel.

**Entrée et prompt** (`D-PRECHECK-PROMPT`) : consignes versionnées (`v1`), contenu encadré par des bornes propres à
l'exécution (nonce aléatoire) et désigné comme donnée, chevrons triples des données neutralisés. **Sortie** : schéma
JSON fermé, puis validation avant l'agent — type connu, source parmi les champs, pièces et questions transmis (jamais
une règle), message non vide de 300 caractères au plus, confiance dans [0, 1], 30 constats au plus ; le reste est
écarté et compté. Une question sans réponse exploitable devient « À vérifier ».

**Échecs** : budget de jetons atteint, fournisseur indisponible, fonction désactivée ou réponse non JSON donnent une
exécution `FAILED` qui **garde les constats déterministes**, avec un motif générique (jamais le message du
fournisseur). Dans le workflow, la brique lève alors un incident rejouable (§94). Flowable retente un job en échec
(3 fois par défaut) : chaque tentative est une exécution rangée et un appel compté.

**Variable de résultat** : `{status, missing, inconsistent, runId}`, comptes **déterministes** seulement. Un routage
peut s'appuyer sur ces comptes, jamais sur l'avis du modèle (C5).

## 4. Journal, file et API

- **Tables** `precheck_run` et `precheck_finding` (changeSet `040`, `D-PRECHECK-STORE`, [13](13-modele-physique.md)) :
  déclencheur, auteur, spécification, statut, fournisseur et modèle, version du prompt, **empreinte SHA-256 de l'entrée
  (jamais l'entrée en clair)**, synthèse, jetons, constats, décisions. Écrites dans leur propre transaction ; supprimées
  avec le dossier.
- **Relances** (`D-PRECHECK-QUEUE`) : bouton ou MCP ; spécification de la dernière exécution, mise en file (une seule
  en attente ou en cours par dossier), relais avec bail de 5 minutes en `SKIP LOCKED`, abandon après 2 tentatives ;
  règles sur les variables courantes du processus.
- **REST**, présentes seulement si la fonction est activée :

| Méthode et chemin | Droit | Effet |
|---|---|---|
| `GET /api/dossiers/{id}/prechecks` | lecture du type | 20 dernières exécutions, la plus récente d'abord |
| `POST /api/dossiers/{id}/prechecks` | EDIT, dossier non clos | relance, 202 ; 409 si déjà en file ou sans exécution précédente |
| `PUT …/{runId}/findings/{findingId}/decision` | EDIT, dossier non clos | `{"decision":"ACCEPTED"\|"DISMISSED"\|null}` ; qui et quand tracés |
| `GET …/{runId}/export` | `TENANT_ADMIN` | journal complet de l'exécution, spécification et empreinte comprises |

- **MCP** (mode opération, [15](15-serveur-mcp.md)) : `run_dossier_precheck`, `get_dossier_precheck`. L'assistant de
  conception n'y a jamais accès (P12, D-ASSIST-TOOLS).

## 5. Le panneau de la fiche (`D-PRECHECK-PANEL`)

Après les pièces : statut, comptes, synthèse et constats, chacun avec son type, sa source et son origine — **« IA »** et
la confiance pour ce que propose le modèle, **« Contrôle »** pour le déterministe — et la mention que les constats sont
indicatifs. Avec EDIT : accepter, écarter, annuler, relancer (rafraîchissement toutes les 5 s, 5 minutes au plus).
Exécutions précédentes consultables ; export du journal pour `TENANT_ADMIN`. Masqué si la fonction est désactivée ou
si aucune exécution n'existe.

## 6. Configuration et conformité

`app.precheck.*` ([DEPLOIEMENT](../guides/DEPLOIEMENT.md), `D-PRECHECK-CONFIG`) : fournisseur compatible OpenAI
(Mistral, vLLM, Ollama, Azure OpenAI) par `spring-ai-openai` en `json_schema` strict, ou Anthropic ; HTTPS hors boucle
locale sauf `allow-plain-http` ; plafond de jetons par jour et par instance (`D-PRECHECK-BUDGET`) ; extraction PDFBox
bornée, sans OCR (`D-PRECHECK-EXTRACT`).

- **RGPD** : minimisation (seuls les champs et pièces désignés partent, jamais un élément sensible) ; le fournisseur est
  sous-traitant (art. 28) ; une AIPD est recommandée ; aucune décision automatisée (art. 22).
- **AI Act** : tâche préparatoire sous supervision humaine ; transparence (marquage « IA ») et journalisation. Un usage
  pour l'éligibilité à une aide ou un subside (annexe III, 5 a) est à analyser au cas par cas.

## 7. Exemple et évaluation

L'exemple [`exemples/autorisation-riverain`](../../exemples/autorisation-riverain/README.md) livre la variante
`autorisation-riverain-preverification.bpmn20.xml` et le scénario de la fiche, rejoué sur ses fichiers par
`RiverainPrecheckExampleIT`. Son jeu d'évaluation (`evaluation/cas.json`) mesure les réponses d'un vrai modèle :
précision et rappel par type de constat (`PrecheckEvaluation`, hors `verify`).

## 8. Limites

- Pas d'OCR ni de lecture d'image : une pièce scannée est « non lisible », à vérifier par l'agent.
- Formats lus : PDF à couche texte et texte brut.
- Le modèle peut se tromper ou être influencé par le contenu d'une pièce (injection) : ses constats sont sourcés,
  marqués et bornés, et l'agent décide.
- Un champ masqué par `visibleWhen` mais désigné dans `precheckFields` est transmis (choix explicite du concepteur).
