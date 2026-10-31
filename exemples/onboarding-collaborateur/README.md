# Exemple — arrivée d'un collaborateur (orchestration technique)

Cas d'usage **orchestration technique** : un workflow Myrmacta coordonne plusieurs applications REST, et des validations
humaines (formulaires de tâche) décident de la suite. Le flux est entièrement **configuré, sans développement**
([registre §112](../../docs/decisions/112-orchestration-technique-requete-construite-par-le-bpmn.md)) :

1. les RH encodent l'arrivée (dossier `ONBOARDING_COLLABORATEUR`) ;
2. le manager valide, renvoie la fiche pour correction ou refuse ;
3. Myrmacta crée la fiche dans le **SIRH**, puis le compte dans l'**IAM** avec le matricule rendu par le SIRH ;
4. pour un profil sensible (développeur, direction), l'**IT valide les accès** : « étendu » ajoute le compte à un groupe IAM ;
5. Myrmacta commande le poste de travail dans l'**ITSM**, puis clôture le dossier ;
6. si une application répond en erreur, une personne choisit de **réessayer** ou d'**abandonner**. L'abandon supprime ce qui
   a déjà été créé (compensation par `DELETE`).

Chaque requête est écrite dans le BPMN (`flowable:connectorRequest`, expression JSONata bornée) à partir des champs du dossier.
Chaque réponse utile est recopiée dans le dossier : la personne qui valide voit les identifiants créés et le dernier
incident sur la fiche.

## Contenu

| Fichier | Rôle |
|---|---|
| `config/type-dossier.json` | Type `ONBOARDING_COLLABORATEUR` : champs saisis par les RH, champs « Provisioning » remplis par le workflow |
| `config/questions.json` | Les 3 questionnaires : validation du manager, validation des accès, incident technique |
| `config/workflow-config.json` | Statuts du workflow et statuts de dossier autorisés |
| `config/connecteurs.json` | Les 3 connecteurs `http-rest` « service seul » (SIRH et ITSM à en-tête statique, IAM en OAuth2) |
| `config/exemple-dossier.json` | Jeu de données de recette |
| `onboarding-collaborateur.bpmn20.xml` | Processus déployable, généré |
| `generer-bpmn.py` | Génère le BPMN (sémantique et mise en page) ; à modifier plutôt que le XML |

## Déroulé

```mermaid
flowchart LR
  D([Arrivée encodée]) --> V[Valider l'arrivée<br/>manager]
  V -- CORRIGER --> C[Corriger la fiche<br/>RH] --> V
  V -- REFUSER --> R([Refusé])
  V -- APPROUVER --> S[POST SIRH /employees] --> I[POST IAM /users]
  I --> P{Profil sensible ?}
  P -- oui --> A[Valider les accès<br/>IT]
  A -- ETENDU --> G[PUT IAM /users/id/groups/groupe] --> T
  A -- STANDARD --> T
  P -- non --> T[POST ITSM /tickets] --> F([Provisionné])
  S & I & G & T -. HTTP hors 2xx .-> X[Traiter l'incident]
  X -- REESSAYER --> S & I & G & T
  X -- ABANDONNER --> K[DELETE IAM puis SIRH] --> AN([Annulé])
```

Pour chaque appel, le motif est le même :

1. « Appeler un connecteur » en **asynchrone**, avec la requête construite dans le BPMN ;
2. « Évaluer une expression » : `connectorResult.status >= 200 and connectorResult.status < 300` ;
3. une passerelle : en cas de succès, « Enregistrer des valeurs dans le dossier » (ex.
   `matriculeSirh = connectorResult.json.id`), sinon `etapeEnEchec` puis l'incident.

L'appel suivant relit `dossier.fields.matriculeSirh` : c'est ainsi qu'un identifiant passe d'une application à l'autre.

## Les requêtes

| Étape | Requête produite par `connectorRequest` | Réponse recopiée |
|---|---|---|
| SIRH | `POST /employees`, corps : identité, fonction, département, date d'arrivée, manager, référence du dossier | `matriculeSirh = json.id` |
| IAM | `POST /users`, corps : login calculé, nom, `employeeNumber = matriculeSirh`, compte désactivé | `identifiantIam = json.id`, `loginIam = json.username` |
| Groupe | `PUT /users/{id}/groups/{groupe}`, `id = identifiantIam`, groupe `developpeurs` ou `direction` | `accesEtendus = true` |
| ITSM | `POST /tickets`, corps : profil, titre, échéance, `requestedFor = loginIam`, référence du dossier | `ticketMateriel = json.number` |
| Compensation | `DELETE /users/{id}` puis `DELETE /employees/{id}`, seulement si l'identifiant existe | `journalCompensation` |

Les chemins sont relatifs à la `baseUrl` de chaque connecteur. Les valeurs de `pathParams` sont encodées : un identifiant qui
contiendrait `/` ou `..` ne peut pas sortir de son segment. Changer d'environnement (recette, production) revient à changer
la `baseUrl` des connecteurs, pas le BPMN.

## Choix retenus

| Question | Choix |
|---|---|
| Appels | Asynchrones : la validation humaine qui précède rend la main tout de suite. Une panne réseau ou un délai dépassé crée un incident rejouable (bandeau sur la fiche, rejeu par un administrateur) |
| Statut HTTP en erreur | N'échoue pas l'activité : il est noté dans `dernierIncident` (étape, statut, début de la réponse) et soumis à une personne |
| Réessayer | Rejoue l'appel en échec, pas les précédents (`etapeEnEchec`). Un `POST` qui a pu aboutir malgré une réponse en erreur se protège côté application (clé d'idempotence, recherche préalable) |
| Abandonner | Supprime le compte IAM puis la fiche SIRH s'ils existent ; le ticket ITSM n'est jamais créé avant l'abandon (il est la dernière étape) |
| Validation IT | Seulement pour les profils `DEVELOPPEUR` et `DIRECTION` (expression sur le dossier) |
| Champs sensibles | Aucun dans cet exemple : un champ sensible n'est jamais lisible par l'expression, donc jamais envoyé |
| Connecteurs | « Service seul » (`webhookEnabled = false`) : aucun webhook entrant n'est ouvert sur ces instances |

Statuts du workflow : `EN_VALIDATION` (initial), `EN_PROVISIONING`, `EN_INCIDENT`, puis `PROVISIONNE` (issue
`COMPLETED`), `REFUSE` ou `ANNULE` (issue `CANCELLED`).

## Mise en service

1. **Créer les trois connecteurs** dans Admin → Connecteurs à partir de `config/connecteurs.json` : type `http-rest`,
   `webhookEnabled` décoché, la `baseUrl` réelle de chaque application, puis le secret (en-tête statique ou OAuth2). Noter
   l'identifiant de chacun.
2. **Générer le BPMN** avec ces identifiants :
   `python generer-bpmn.py --sirh <uuid> --iam <uuid> --itsm <uuid>`.
   Le fichier livré contient des identifiants de remplacement (`00000000-0000-4000-8000-00000000000N`), refusés au
   déploiement tant qu'ils ne désignent pas un connecteur actif.
3. **Créer le type de dossier**, les **trois questionnaires** (Admin → Questions de tâche) et la **configuration du workflow**,
   par l'interface ou par les outils MCP (`define_dossier_type`, `define_task_question_schema`, puis `deploy_workflow` avec
   `config/workflow-config.json`). Un agent peut aussi proposer les connecteurs (`define_connector_instance`), jamais leurs
   secrets.
4. **Déployer** `onboarding-collaborateur.bpmn20.xml` (éditeur BPMN, ou brouillon MCP puis publication par un humain).
5. **Accorder l'ACL** `EDIT` du type aux RH, aux managers et à l'IT (Admin → Types de dossier → ACL).

L'assignation des tâches à un groupe (manager, IT) n'existe pas encore
([#190](https://github.com/superjojo79/Myrmacta/issues/190)) : chaque tâche se prend avec « M'assigner ».

## Recette

`OnboardingOrchestrationExampleIT` rejoue l'exemple sur ces fichiers, par les API de l'application (sécurité comprise),
contre un faux SIRH, un faux IAM et un faux ITSM locaux :

| Cas | Parcours | Contrôlé |
|---|---|---|
| O1 | Corriger, puis approuver ; profil développeur, accès étendus | Aucun appel avant l'approbation ; 4 appels dans l'ordre ; matricule SIRH transmis à l'IAM ; en-tête propre à chaque application ; champs « Provisioning » remplis ; dossier clôturé |
| O2 | IAM en 503 puis réessayer ; ITSM en 500 puis abandonner | Incident noté au dossier ; second appel IAM réussi ; `DELETE` IAM puis SIRH, sans corps ; journal de compensation |
| O3 | Refuser | Aucun appel |
| O4 | Webhook d'un connecteur « service seul » | `404` |

```bash
./mvnw test -Dtest=OnboardingOrchestrationExampleIT -Dskip.frontend=true
```

## Ce que l'exemple ne montre pas

- `PATCH` (mise à jour partielle OData, D365 F&O, Dataverse) : pas encore disponible
  ([#261](https://github.com/superjojo79/Myrmacta/issues/261)).
- Un délai sur une validation (minuteur en bordure) : voir l'exemple
  [autorisation riverain](../autorisation-riverain/README.md).
- Une réponse asynchrone d'une application (callback corrélé) : connecteur `flowable-callback`,
  [docs/specs/21-correlation-callback-async.md](../../docs/specs/21-correlation-callback-async.md).
