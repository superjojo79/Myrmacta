# §111 — Variables de démarrage déclarées dans le BPMN

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] livré (issue [#187](https://github.com/superjojo79/Myrmacta/issues/187)). Le dialogue « Démarrer un workflow »
accepte des variables en saisie libre depuis le [§93](093-brouillons-avec-ressource-inactive-destinataire-de-notifier.md).
Il manquait une déclaration (nom, libellé, obligatoire) affichée comme un formulaire.

**D-START-DECLARED** (tranché par l'utilisateur, 2026-10-29) — la déclaration vit dans le BPMN déployé, sur le
processus, pas dans une table à part ni dans le seul concepteur de parcours :

```xml
<process id="autorisationRiverain" isExecutable="true">
  <extensionElements>
    <flowable:startVariable name="motif" label="Motif de la demande" required="true"/>
  </extensionElements>
  …
</process>
```

- Élément vide, attributs `name`, `label`, `required` (`true` ou `false`). Au plus 30. Le nom suit
  `[A-Za-z_][A-Za-z0-9_]*` (64 caractères), n'est pas une variable réservée, et n'est pas répété. Le libellé
  fait au plus 200 caractères, sans caractère de contrôle. Ailleurs qu'au processus, l'élément est refusé au
  parseur (brouillon, déploiement, MCP).
- `GET /api/workflow-start-variables?processDefinitionKey=` renvoie ces déclarations de la dernière version
  déployée, dans l'ordre du XML, pour le processus dont l'identifiant est cette clé (un fichier à plusieurs
  processus ne retient pas le premier pool). Tableau vide si le processus n'en déclare pas ou n'est pas
  déployé. Tout
  utilisateur authentifié peut les lire : ce sont les libellés du diagramme, pas un secret.
- Sans déclaration, la saisie libre du [§93](093-brouillons-avec-ressource-inactive-destinataire-de-notifier.md)
  reste. Avec déclaration, le dialogue n'envoie que ces noms. Le serveur, sur le démarrage manuel
  (`ManualStartOrchestrator`, donc l'API, le MCP `start_workflow` y compris `dryRun`, et le dossier enfant)
  et sur le relais d'outbox, n'accepte que ces noms, en texte (4 000 caractères, sauts de ligne permis).
  Une obligatoire absente ou blanche est refusée. Une facultative blanche n'est pas posée. Le message nomme
  la variable, jamais la valeur. L'outbox marque l'événement mort, comme pour une variable réservée.
- La réconciliation d'un lien `RESERVED` orphelin ne dispose pas des variables d'origine : elle ne revérifie
  pas la déclaration.
- L'éditeur BPMN conserve l'élément à l'enregistrement. Il n'a pas encore de champ pour le créer : on l'écrit
  dans le XML, puis on déploie.

**Revue adversariale (2026-10-29, sous-agent au contexte neuf, une passe).** Aucun constat Critique, Haut
ou Moyen. Les démarrages manuel, MCP, dossier enfant et outbox passent par `StartVariableDeclarations.bind` ;
le catalogue est authentifié et filtré par le tenant de l'instance ; les messages ne contiennent pas la valeur.
Quatre constats Bas, non corrigés :

- la réconciliation d'un lien `RESERVED` ne revérifie pas la déclaration (déjà le choix ci-dessus) ;
- le `dryRun` MCP ne rejoue pas l'allowlist `documentType`, qui ne s'applique qu'au démarrage effectif ;
- un connecteur qui ajoute `inboundAttachmentsRejected` fait mourir l'événement d'outbox si le processus
  déclare ses variables et n'a pas ce nom ;
- chaque lecture du catalogue revalide le XML déployé (plafond multipart 25 Mo), pour un utilisateur déjà
  authentifié.

**Hors de cette décision.** Types autres que le texte, valeur par défaut, minuteur ou gabarit de notification.

**Impacte :** `docs/specs/05-workflow-engine.md`, `docs/guides/GUIDE-UTILISATEUR.md` §3,
`docs/guides/GUIDE-CONCEPTION-BPMN.md` §2, `openapi/openapi.yaml`. Aucune migration, aucune variable
d'environnement.
