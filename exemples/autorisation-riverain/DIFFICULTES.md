# Difficultés rencontrées — flux « autorisation riverain »

Contrainte de départ : tout faire **par configuration**, sans développement dans Myrmacta. Chaque point
ci-dessous décrit l'obstacle, la façon dont il a été contourné et ce qui reste imparfait. Les points
sont classés par impact sur le besoin exprimé.

Configuration produite le 2026-10-13 sur le tenant `tenant_dev`, via le serveur MCP Myrmacta.

---

## 1. « Une seule demande par plaque » ne peut pas être imposée

**Levé (issue #184, TODO §102).** `plaqueNormalisee` est déclarée `unique`. Une seconde demande ouverte
dont la plaque se normalise vers la même clé est refusée à la création (erreur `UNIQUE`, référence du
dossier détenteur). L'autorisation tacite ne peut plus accorder un doublon qui n'a pas pu être créé.
La case « J'ai vérifié qu'aucune autre demande n'existe pour ce véhicule » est retirée du questionnaire
d'instruction. La clôture du premier dossier libère la clé. Le texte ci-dessous décrit la situation d'origine.

**Constat.** Un champ de type de dossier ne pouvait pas être déclaré unique : seule l'unicité des *noms* de
champs était contrôlée (`DossierType.requireUniqueFieldNames`). Aucune brique BPMN ne permettait non plus de
chercher un dossier existant depuis le workflow. Les expressions ne voient que le dossier courant.

**Contournement.**
- Champ calculé et cherchable `plaqueNormalisee` = pays + plaque en majuscules, sans espaces, tirets,
  points ni barres. Par exemple, `1-abc 123` en Belgique donne `BE-1ABC123`. Vérifié avec
  `evaluate_dossier_rules`.
- Dans le formulaire d'instruction, une case obligatoire « J'ai vérifié qu'aucune autre demande n'existe
  pour ce véhicule », avec la marche à suivre (recherche sur ce champ, refus motivé si doublon).

**Ce qui reste imparfait.**
- Rien n'empêche de *créer* un second dossier pour la même plaque.
- Le contrôle est humain : la case cochée est une déclaration, pas une vérification.
- **L'autorisation tacite** (délai de 30 jours dépassé) se déclenche sans que personne n'ait vérifié
  les doublons. Un doublon jamais instruit est donc autorisé automatiquement.

**Évolution proposée (développement).** Soit un réglage `unique: true` sur `FieldDefinition`, contrôlé
à la création et à la modification ; soit une brique « Rechercher des dossiers » qui alimente une
passerelle.

## 2. Le document ne peut pas être joint à l'e-mail

**Constat.** La brique « Notifier » (`NotificationDispatchTask`) n'accepte ni pièce jointe ni référence
à un document. Le connecteur `smtp` (envoi ponctuel) non plus. Le portail externe ne montre au tiers
que les pièces qu'il a lui-même déposées : il ne peut pas servir à lui remettre le PDF.

**Contournement.**
- Le PDF de décision est généré puis **joint au dossier**.
- Un e-mail **informatif** part automatiquement (accordée ou refusée).
- Une tâche humaine « Transmettre le document de décision » demande à l'agent de télécharger le PDF et
  de l'envoyer lui-même. Il indique le mode (e-mail avec pièce jointe, courrier, remise en main propre)
  et la date de transmission.

**Ce qui reste imparfait.** L'envoi du document reste manuel. Le corps de l'e-mail est un **texte
fixe**, sans référence du dossier, nom, plaque ni motif (voir point 4).

**Évolution proposée.** Ajouter à la brique « Notifier » un paramètre de pièce jointe, par exemple
`notificationAttachment="variable:generatedDocumentId"`.

**Levé (issue #183, TODO §96).** La brique « Notifier » accepte
`flowable:notificationAttachment="variable:generatedDocumentId"` (ou `dossierField:<champ>`) et un nom de
fichier facultatif `flowable:notificationAttachmentName`. Le document doit être un PDF **produit et joint au
dossier courant** (brique « Joindre le document » en amont). L'exemple envoie désormais le PDF avec l'e-mail
de décision ; la tâche de transmission manuelle est supprimée. Le connecteur `smtp` reste sans pièce jointe
(D-NOTIF-ATTACH-SMTP-CONNECTOR).

## 3. Le destinataire de l'e-mail ne peut pas venir du dossier

> **Levé le 2026-10-16 (PR #174, TODO §93).** `flowable:notificationTo="dossierField:email"` lit l'adresse dans le
> dossier au moment de l'envoi, et l'écran « Démarrer un workflow » sait transmettre des variables. L'exemple utilise
> désormais `dossierField:email` : la variable `notificationTo`, la passerelle `emailConnu` et le mapping
> `variables.notificationTo` du connecteur ont disparu. Les briques e-mail sont asynchrones, pour qu'une adresse mal
> saisie crée un incident au lieu de bloquer l'agent. Le texte ci-dessous décrit la situation d'origine.

**Constat.** La brique « Notifier » n'a pas de source `dossierField:` pour le destinataire (contrairement
à la tâche externe et à la signature). L'adresse est soit un littéral, soit la variable de process
`notificationTo`. Et cette variable ne peut pas être calculée dans le workflow : « Évaluer une
expression » refuse d'écrire dans une variable qui porte le nom d'un paramètre de brique
(`BlockAttributeRules.MANAGED_ATTRIBUTES`, une protection voulue).

**Contournement.** `notificationTo` est passée **au démarrage** du workflow :
- par le connecteur du portail (mapping `variables.notificationTo = $.demandeur.email`, voir
  `config/connecteur-portail.exemple.json`) ;
- par l'API ou l'outil MCP `start_workflow` (champ `variables`).

Le workflow calcule d'abord `emailConnu`. Sans adresse, les e-mails sont simplement sautés :
aucun incident, et la transmission manuelle (point 2) reste prévue.

**Ce qui reste imparfait.** L'écran « Démarrer workflow » de la fiche dossier
(`StartWorkflowDialog.vue`) ne transmet **aucune variable**. Une demande encodée par un agent dans
l'interface ne déclenche donc **aucun e-mail automatique**. L'adresse est saisie deux fois (champ
`email` du dossier et variable `notificationTo`).

## 4. Les textes envoyés au demandeur sont fixes

**Constat.** Tout texte destiné au demandeur est littéral dans le BPMN : objet et corps des e-mails,
consignes (`documentation`) de la tâche portail. Le portail n'affiche aucune variable de process (contrat
`PortalTaskResponse`, volontairement minimal). La motivation saisie par l'agent pour une demande
d'informations complémentaires **ne peut donc pas parvenir au demandeur automatiquement**.

**Contournement.** Messages génériques (« un gestionnaire vous précisera les éléments attendus »).
La motivation reste visible par l'agent, qui contacte le demandeur.

**Évolution proposée.** Gabarits d'e-mail (le moteur Thymeleaf restreint des documents existe déjà),
ou affichage sur le portail d'une variable désignée par le concepteur.

## 5. Le moteur des modèles de document est beaucoup plus strict que documenté

**Constat** (vérifié avec `preview_document`) :
- une variable absente, une propriété absente **ou une valeur nulle** fait **échouer** la génération
  (`Unknown template variable` / `Unknown template property`). Le guide `docs/guides/GUIDE-CONCEPTION-BPMN.md` §3
  affirme au contraire qu'un champ absent « se résout silencieusement à une chaîne vide » ;
- pas d'opérateur de comparaison (`==`), pas de `or`/`and`, pas de ternaire, pas d'accès `map['x']`
  ni `map.get('x')`, pas de `containsKey`. Seuls sont acceptés les chemins de propriétés, les
  littéraux et les méthodes de `#strings`, `#dates`, `#numbers` et `#lists`
  (`SafeThymeleafExpressionSupport`) ;
- l'objet `dossier` (référence, libellé) n'existe pas dans le contexte, seulement `dossierFields`.

**Contournement.**
- Les conditions utilisent `#strings.equals(issueDecision, 'REFUSEE')`. Une branche `th:if` fausse
  n'est pas évaluée, ce qui a été vérifié.
- Toute valeur facultative est calculée **avant** la génération par une tâche « Évaluer une expression »
  avec une valeur par défaut : `docTelephone`, `docPieces`, `docComplements`, `docMotivation`,
  plus `referenceDossier` (lu dans `dossier.reference`) et `dateDecision`. Cela fait six tâches
  techniques dans le diagramme.
- Le modèle du véhicule et la date de début souhaitée sont devenus **obligatoires** pour pouvoir être
  imprimés directement.
- Le téléphone **n'est pas marqué sensible** : un champ sensible est invisible des expressions, donc
  impossible à mettre en valeur par défaut. Il ne pourrait pas être imprimé.

**Évolution proposée.** Corriger le guide, ou rendre le moteur tolérant (valeur vide, opérateur `?:`).

**Mise à jour du 2026-10-16.** Le guide est corrigé (PR #168) : `docs/guides/GUIDE-CONCEPTION-BPMN.md` §3 indique
désormais qu'une valeur absente ou nulle fait échouer la génération, et décrit la grammaire acceptée.
Le moteur reste strict : le contournement ci-dessus reste nécessaire.

## 6. Le délai de 30 jours empêche d'utiliser l'éditeur de parcours

**Constat.** Le parcours (définition pas à pas compilée en BPMN) ne propose pas de minuteur en bordure
d'une tâche, seulement une attente (`WAIT`) séquentielle. Or le besoin est une course : « l'agent
décide **ou** 30 jours passent ».

**Contournement.** BPMN écrit à la main avec deux minuteurs interrompants (`boundaryEvent` + `P30D`) :
l'un sur l'instruction (autorisation tacite), l'autre sur l'attente des compléments (refus faute de
réponse). Le diagramme (BPMNDI) est produit par `generer-bpmn.py` pour rester lisible dans l'éditeur.

**Choix et limites.**
- 30 jours **calendaires** : Myrmacta n'a pas de calendrier de jours ouvrables.
- Le délai d'instruction **repart pour 30 jours complets** à chaque retour du demandeur. Une suspension
  avec reprise du temps restant demanderait un calcul de date et un minuteur en `timeDate`, alors que
  les minuteurs n'acceptent que des littéraux (aucune expression `${…}` autorisée).
- Pour une recette, modifier `DELAI` dans `generer-bpmn.py` (par exemple `PT5M`) et régénérer.

## 7. Le portail est désactivé sur l'instance et ne sait pas recevoir une demande

**Constat.**
- `APP_PORTAL_ENABLED=false` dans `.env` : tout déploiement contenant une tâche externe est refusé
  (« BPMN external portal task requires the portal to be enabled »).
- Le portail Myrmacta ne sert qu'à **répondre à une tâche** via un lien personnel : il n'offre pas de
  formulaire public de dépôt de demande.

**Contournement.**
- Deux variantes du BPMN :
  - `autorisation-riverain.bpmn20.xml` (cible) : le demandeur complète sa demande sur le portail ;
  - `autorisation-riverain-sans-portail.bpmn20.xml` (déployable aujourd'hui) : un e-mail prévient le
    demandeur et l'agent encode les compléments reçus (questionnaire
    `complement-recu-autorisation-riverain`).
- Le « portail » de dépôt est un **site externe** qui appelle Myrmacta par le connecteur `http-rest`
  (déclencheur webhook signé HMAC). La configuration est fournie dans
  `config/connecteur-portail.exemple.json` mais **n'a pas été créée** : son secret doit être choisi et
  partagé avec l'équipe du portail.

**Non vérifié.** La variante avec portail n'a pas pu être validée par le serveur, puisque la validation
s'arrête sur le portail désactivé.

## 8. Le modèle de document doit être activé avant même l'enregistrement du workflow

> **Levé le 2026-10-16 (PR #174, TODO §93).** Un brouillon proposé par MCP accepte un modèle encore en brouillon et le
> signale dans `inactiveReferences` ; seule la publication exige le modèle actif. L'ordre de mise en service du
> `README.md` en tient compte. Le texte ci-dessous décrit la situation d'origine.

**Constat.** Un modèle créé par MCP reste en **brouillon** : son activation est un geste humain dans
l'administration. Or la validation du BPMN, au déploiement **comme à l'enregistrement d'un brouillon**,
exige un modèle **actif** (« flowable:documentType … references a resource that does not exist »).

**Conséquence.** Le workflow n'est **pas encore enregistré** sur l'instance. À part ce point, la
variante sans portail a été validée par le serveur. L'ordre des étapes est donné dans `README.md`.

## 9. Pas d'assignation des tâches par groupe

**Constat.** Myrmacta refuse volontairement `assignee` et `candidateGroups` dans le BPMN
(décision D-EDIT-SCOPE). Les tâches « Instruire la demande », « Encoder les compléments reçus » et
« Transmettre le document de décision » apparaissent **non assignées**.

**Contournement.** L'assignation se fait dans l'écran des tâches (action « Réassigner »). Des
observateurs peuvent être déclarés sur le workflow (`observerUserIds`) pour être notifiés.

**Mise à jour du 2026-10-16.** En partie levé par la PR #171. Un éditeur du dossier voit désormais
les tâches libres et peut les prendre (« M'assigner »). La réassignation propose une liste
d'utilisateurs connus au lieu d'un identifiant à saisir. L'assignation **par groupe** dans le BPMN
reste refusée (D-EDIT-SCOPE inchangée).

## 10. Autres points relevés

- **`dossierFields` est un instantané pris au démarrage.** Les compléments fournis en cours de route ne
  sont pas relus depuis le dossier : ils passent par la variable `reponseComplement`. Une pièce ajoutée
  au dossier pendant l'instruction n'est pas reflétée dans la ligne « Pièces justificatives » du PDF.
- **Champs `FILE` dans les expressions** : `docPieces` suppose qu'un champ fichier renseigné est visible
  dans `dossier.fields`. Ce n'est pas vérifié en exécution réelle.
- **Statuts finaux posés en fin de flux.** `AUTORISEE` et `REFUSEE` (issue `COMPLETED`) ne sont posés
  qu'après la transmission du document, pour ne pas marquer l'exécution terminée pendant que des
  tâches restent à faire. Pendant la génération et la transmission, le statut affiché reste
  `EN_INSTRUCTION`.
- **Aucun test de bout en bout** n'a encore été fait : le workflow n'a jamais été publié sur l'instance.
  Les expressions, la formule, le formulaire de dossier et le rendu du modèle ont été vérifiés
  séparément avec les outils de validation à blanc.

## 11. Un e-mail asynchrone en échec est invisible dans l'interface (recette du 2026-10-18)

> **Levé le 2026-10-20 (PR #176, TODO §94).** Un job en échec apparaît désormais sur la fiche dossier (bandeau « Étapes en
> échec », étape marquée dans le diagramme) et dans **Admin → Incidents** ; un administrateur le **rejoue** une fois la
> cause corrigée. Vérifié sur ce cas (R6) : adresse corrigée, rejeu, e-mail reçu. Le texte ci-dessous décrit la situation
> d'origine.

**Constat.** Avec une adresse mal saisie (`Camille Moreau <camille@…>`), la brique « Notifier » asynchrone échoue
comme prévu : trois tentatives, puis job en *dead letter*, message « notificationTo source dossierField:email is not a
single e-mail address », adresse jamais journalisée. Mais la fiche dossier affiche toujours le workflow « en cours »,
et le diagramme ne marque aucune étape : `failedIds` ne retient que les activités terminées par une erreur BPMN, et
l'étape en attente du job n'apparaît pas non plus parmi les étapes actives
(`FlowableWorkflowVisualizationAdapter.getDiagram`). Seul le compteur global de *dead letters* (observabilité) bouge.

**Conséquence.** Le guide recommande l'asynchrone pour transformer l'échec en incident ; l'incident existe, mais
personne ne le voit sur le dossier, et rien dans l'interface ne permet de le rejouer après correction de l'adresse.

**Évolution proposée (développement).** Marquer en erreur, dans le diagramme, les activités qui ont un job en *dead
letter* ou en échec, afficher l'incident sur la fiche dossier, et offrir une action « Rejouer » aux administrateurs.
