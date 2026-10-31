# §114 — Groupes d'utilisateurs et assignation par groupe

> **Statut :** ouvert · point de premier niveau · [registre](README.md)

En cours : issue [#190](https://github.com/superjojo79/Myrmacta/issues/190), lot 2 de la Digital Mail Room
([§113](113-digital-mail-room-courrier-dossier-et-fils.md)). Ce point complète le [§92](092-assignation-des-taches-m-assigner-et-annuaire.md)
(« M'assigner », annuaire local).

**D-GROUP-SOURCE** (tranché par l'utilisateur, 2026-10-31) — les deux sources de groupes coexistent :

- **Groupes locaux :** un administrateur les crée dans Myrmacta et y ajoute des utilisateurs de l'annuaire
  (`known_user`, §92).
- **Groupes de l'IdP :** une claim du jeton OIDC, dont le nom se configure, est enregistrée à chaque connexion, comme
  les rôles (D-ASSIGN-DIR). Un groupe local peut déclarer qu'il reprend un groupe de l'IdP.
- Conséquence assumée, la même qu'au §92 : un membre venu de l'IdP n'est connu qu'après sa première connexion.
- Écarté : l'API d'administration de Keycloak ou de Graph, qui demanderait un secret de plus et couplerait Myrmacta à un
  IdP.

**D-GROUP-ASSIGN** (conception, 2026-10-31) — l'assignation à un groupe se pose **à l'exécution**, jamais par une
expression du BPMN :

- Elle vient de la brique de routage (D-MAILROOM-ROUTE) ou d'une réaffectation : `TaskService.addCandidateGroup`.
- Un `candidateGroups` littéral reste accepté par le parseur, comme aujourd'hui. Une expression y reste refusée (§57).
  D-EDIT-SCOPE ne change pas.
- Les tâches d'un groupe apparaissent dans « Tâches » pour ses membres. Un membre peut les prendre (`claim`, §92) s'il a
  l'ACL EDIT du dossier. Un non-membre ne les voit pas.

**Bornes et limites (livraison du lot 2a) :**

- **Code d'un groupe :** 2 à 64 caractères, majuscules, chiffres et `_`, commençant par une lettre.
- **Membres listés :** 500 au plus par groupe, uniquement des utilisateurs de l'annuaire. Un sujet jamais connecté est
  refusé (400), comme la garde anti-faute de frappe du §92.
- **Claim de l'IdP :** son nom se règle par `APP_SECURITY_OIDC_GROUPS_CLAIM` (`groups` par défaut). On en garde 100 valeurs
  au plus, de 256 caractères chacune ; les valeurs vides ou porteuses de caractères de contrôle sont écartées.
- **Débordement Entra ID** (au-delà de 200 groupes, la claim est remplacée par un lien Graph) : non géré, ces utilisateurs
  n'ont alors aucun groupe d'IdP.
- **Filtre de « Tâches » :** il s'applique après la pagination de Flowable, comme pour les tâches orphelines. Une page peut
  donc compter moins d'éléments que sa taille.

**Impacte :** `docs/guides/GUIDE-UTILISATEUR.md` (administration des groupes, Tâches), `docs/guides/GETTING-STARTED.md` et
`.env*.example` si la claim se configure par variable d'environnement, `openapi/openapi.yaml`, nouveaux changesets.
