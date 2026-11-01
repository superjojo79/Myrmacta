# §117 — Promotion de configuration, tranche 2 : groupes, templates, parcours, réglages de type

> **Statut :** ouvert · point de premier niveau · [registre](README.md)

Suite de [§83](083-d4-promotion-de-configuration-entre-environnements.md), issue
[#279](https://github.com/superjojo79/Myrmacta/issues/279). La première tranche transporte les types de dossier, les
schémas de questions, les connecteurs, les tables DMN, les processus BPMN et les configurations de workflow. La Digital Mail
Room ([§113](113-digital-mail-room-courrier-dossier-et-fils.md) à [§115](115-echeances-de-dossier-rappel-escalade-et-minuteurs.md))
a ajouté des réglages qui n'y figurent pas. Sur la cible, l'exécution se dégrade alors en silence. Le cadrage du 2026-11-01
ajoute les templates de documents et les parcours.

Principes de §83 inchangés : écriture par les cas d'usage existants, plan à blanc puis application, rejouer converge, aucun
secret. L'import ne supprime jamais rien.

**D-PROMO-FORMAT2** (conception) — le bundle passe au format 2 :

- L'export écrit le format 2. L'import lit le format 1 et le format 2. Un bundle de format 1 qui contient un nouveau type
  d'artefact est refusé.
- Une instance antérieure refuse un bundle de format 2 en le disant (« formatVersion 2 »).
- Les constantes existantes d'`ArtifactType` gardent leur rang, dont dépend l'empreinte d'un bundle déjà journalisé.
  L'ordre d'application est un rang explicite : groupes, types de dossier, schémas, templates, connecteurs, DMN, BPMN,
  configurations de workflow, workflows « réponse », échéances, parcours.

**D-PROMO-GROUP** (tranché par l'utilisateur, 2026-11-01) — un groupe d'utilisateurs ([§114](114-groupes-d-utilisateurs-et-assignation-par-groupe.md))
voyage avec son code, son libellé et son groupe de l'IdP (`idpGroup`) :

- **Jamais ses membres :** ils sont propres à chaque environnement.
- `idpGroup` accepte `{{paramètre}}`, comme une valeur de connecteur (D-PROMO-PARAMS). Un paramètre absent bloque le groupe.
- **Création :** avec l'`idpGroup` du bundle, paramètres remplacés.
- **Mise à jour :**
  - seul le libellé change ;
  - l'`idpGroup` de la cible n'est jamais remplacé : promouvoir la recette ne doit pas rattacher un groupe de production au
    groupe d'un autre IdP ;
  - le plan signale un `idpGroup` qui diffère.
- Écartés :
  - ne jamais transporter `idpGroup` : un oubli laisse le groupe vide, sans signal ;
  - l'appliquer tel quel : on change en silence qui voit les tâches.

**D-PROMO-TEMPLATE** (tranché par l'utilisateur, 2026-11-01) — un template de document voyage comme la **version active** de
chaque combinaison (clé, variante, langue) : libellé et contenu HTML.

- **À l'import :** une nouvelle **version DRAFT** est créée, sauf si la version active ou un brouillon existant a déjà le même
  contenu (*Inchangé*). Le template **n'est jamais activé** par l'import : un humain l'active dans l'écran Templates, comme il
  publie un brouillon BPMN ou DMN (D-PROMO-APPLY).
- **Le plan bloque** un contenu trop gros ou une syntaxe refusée par le contrôle de l'activation.
- **BPMN qui référence un template présent seulement dans le bundle :** il est *Bloqué* au premier passage, avec la raison.
  Une fois le brouillon de template créé, un second import l'accepte en brouillon. La publication reste refusée tant que le
  template n'est pas activé (D-DRAFT-REF-STATUS).
- Écartés :
  - activer directement : rien n'est relu avant la mise en ligne des documents ;
  - transporter l'historique des versions : lourd, et les numéros divergent.

**D-PROMO-PARCOURS** (tranché par l'utilisateur, 2026-11-01) — un parcours ([§88](088-lc5-concepteur-de-parcours.md)) voyage
par sa **définition** (libellé, statuts, étapes, type de dossier) :

- **À l'import :** la définition est enregistrée sans être compilée. Le BPMN du bundle reste la référence et devient un
  brouillon, comme en tranche 1.
- **Signal du plan :** l'export note si la définition a changé depuis sa dernière compilation, ou si le BPMN déployé n'est
  pas le dernier compilé ; le plan l'avertit. Ce repère n'entre pas dans la comparaison.
- **Mise à jour :** elle remplace la définition de la cible.
- Écarté : recompiler à l'import. Le BPMN obtenu pourrait différer de celui qui tournait sur la source.

**D-PROMO-TYPE-SETTINGS** (conception) — le workflow « réponse » (D-MAILROOM-REPLY-WORKFLOW) et l'échéance (D-DEADLINE-FIELD)
d'un type sont **deux types d'artefacts à part**, de clé = code du type :

- **Ordre :** ils sont appliqués après les configurations de workflow et les groupes, dont ils dépendent.
- **Document du type :** il ne change pas, ni sa comparaison.
- **Le plan bloque** une dépendance qui manque à la fois sur la cible et dans le bundle :
  - le type ;
  - la configuration du workflow « réponse » ;
  - le type enfant ;
  - le champ `DATE` de l'échéance ;
  - le groupe d'escalade.
- **Configuration jointe à un brouillon BPMN :** si la configuration du workflow « réponse » n'existera qu'à la publication de
  ce brouillon, le réglage est *Bloqué*. Il faut publier le brouillon, puis relancer l'import.
- **Réglage absent du bundle :** la cible le garde, puisque l'import ne supprime jamais rien.

**D-PROMO-ORPHAN** (tranché par l'utilisateur, 2026-11-01) — une référence **orpheline** (absente de la cible et du bundle)
est traitée selon son effet :

- **Bloqué** quand l'écriture serait refusée de toute façon (D-PROMO-TYPE-SETTINGS).
- **Avertissement** en tête de la raison (`WARNING:`), quel que soit le statut de l'artefact, qui reste appliqué, quand
  l'exécution se dégrade sans erreur :
  - groupe nommé par une sortie `routageService` d'une table DMN ;
  - attribut `groupeTri` de « Router le courrier » ;
  - `candidateGroups` littéraux d'une tâche ;
  - clé de template d'un type de dossier sans version active ni template dans le bundle.

**D-PROMO-MCP-TEMPLATE** (tranché par l'utilisateur, 2026-11-01) — par le canal MCP, `import_configuration` applique aussi les
**brouillons de templates**. Comme un brouillon BPMN ou DMN, ils ne s'exécutent pas avant qu'un humain les active
(D-PROMO-MCP). Groupes, parcours, workflows « réponse » et échéances restent réservés à l'écran : ils prennent effet tout de
suite ou remplacent le travail d'un concepteur.

**Hors périmètre, confirmé :**

- ACL par type : utilisateurs propres à chaque environnement ;
- réglages du tenant : rétention par défaut, alertes SLA, webhooks ;
- membres des groupes ;
- suppression sur la cible d'un artefact absent du bundle ;
- signature du bundle (D-PROMO-INTEGRITY).
