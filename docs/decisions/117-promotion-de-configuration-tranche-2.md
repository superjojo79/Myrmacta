# §117 — Promotion de configuration, tranche 2 : groupes, templates, parcours, réglages de type

> **Statut :** clos · point de premier niveau · [registre](README.md)

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
- Le format est contrôlé avant les types d'artefacts. Une instance de la tranche 1 refuse un bundle de la tranche 2 par
  « unknown artifact type » : elle lit les types avant le format. Le message « unsupported bundle formatVersion » vaut à
  partir de cette version, pour les formats suivants.
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
- **Taille d'un fichier de bundle :** portée de 5 à 12 Mio, le total restant à 50 Mio. Un template actif peut peser 5 Mio de
  HTML, que l'encodage JSON (guillemets, sauts de ligne) peut doubler : à 5 Mio, un seul gros template faisait échouer
  l'export entier (revue de clôture).
- **Comparaison :** le contenu est comparé sans blancs de tête ni de fin, comme le cas d'usage l'enregistre. Il en va de même
  pour le libellé et le groupe de l'IdP d'un groupe.
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
- **Rapport et journal** (issue [#285](https://github.com/superjojo79/Myrmacta/issues/285)) : l'avertissement, séparé de la
  suite de la raison par ` | `, est repris en tête du message de la ligne du rapport, *Appliqué* comme *Inchangé*, donc dans
  le journal `config_import`. Un `apply` MCP sans plan préalable le montre ainsi aussi.
- **Canal :** le plan reçoit le canal qui l'appliquera. Par MCP, qui n'applique jamais un groupe, un groupe que seul le
  bundle apporte ne compte pas comme connu ; l'avertissement renvoie vers l'écran d'import.

**D-PROMO-MCP-TEMPLATE** (tranché par l'utilisateur, 2026-11-01) — par le canal MCP, `import_configuration` applique aussi les
**brouillons de templates**. Comme un brouillon BPMN ou DMN, ils ne s'exécutent pas avant qu'un humain les active
(D-PROMO-MCP). Groupes, parcours, workflows « réponse » et échéances restent réservés à l'écran : ils prennent effet tout de
suite ou remplacent le travail d'un concepteur.

**Export par MCP** (revue de clôture) : `export_configuration` ne renvoie **jamais** le HTML d'un template. Le template est
listé dans `omitted` avec sa clé et sa somme SHA-256, et l'agent est renvoyé vers le ZIP de l'écran. C'est la règle de
l'éditeur de templates (D-TPLED-NO-ACTIVATE : pas de corps HTML par MCP). `bundleSha256` reste celui du bundle complet.

**Hors périmètre, confirmé :**

- ACL par type : utilisateurs propres à chaque environnement ;
- réglages du tenant : rétention par défaut, alertes SLA, webhooks ;
- membres des groupes ;
- suppression sur la cible d'un artefact absent du bundle ;
- signature du bundle (D-PROMO-INTEGRITY).

**Revue de clôture** (2026-11-01, contexte indépendant) : aucun constat critique ni haut.

- **Corrigés avec un test de régression :**
  - les trois constats moyens : coût quadratique du plan, HTML des templates exporté par MCP, export cassé par un gros
    template ;
  - cinq constats bas :
    - nom de fichier d'une variante « _ » ;
    - consigne « relancez » trop large ;
    - comparaisons non normalisées ;
    - textes MCP obsolètes ;
    - empreinte des bundles de format 1 non figée.
- **Repris dans l'issue [#285](https://github.com/superjojo79/Myrmacta/issues/285), corrigé depuis (voir D-PROMO-ORPHAN) :**
  les avertissements du plan ne figuraient pas dans le rapport d'application ni dans le journal ; par MCP, un groupe du
  bundle comptait comme connu alors que ce canal ne l'applique pas.
