# §101 — Le connecteur `document-storage` ne touche que ce qu'il a stocké pour le dossier

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] corrigé (issue [#205](https://github.com/superjojo79/FlowForge/issues/205)). Constat L4 de la revue adversariale du §97.

**Constat.** `DocumentStorageConnector` exécutait `RETRIEVE`, `EXISTS` et `DELETE` sur toute référence de stockage du
tenant reçue dans `connectorInput`. Or une complétion de tâche peut poser `connectorInput`. Le titulaire d'une tâche placée
avant « Appeler un connecteur » `document-storage` pouvait donc lire (contenu rendu dans `connectorResult`) ou effacer :
- une pièce d'un **autre dossier** (`PRODUCED_DOC`, pièce déposée) ;
- un contenu que le connecteur avait stocké pour un autre dossier.

Reproduit sur le moteur par `DocumentStorageCrossDossierIT`. Seule atténuation jusque-là : les références sont aléatoires
et jamais exposées par les vues.

**D-STORAGE-REF-DOSSIER** ⚠ — chaque objet écrit par le connecteur est enregistré (table `connector_stored_object`,
changeSet `036`) avec le dossier pour lequel il l'a été. La règle dépend de l'origine de l'appel (`ConnectorCallContext`) :

- **Depuis un process** (`connectorServiceTask`), le dossier est celui de la variable plateforme `dossierId`. Cette
  variable ne peut être forgée ni par une complétion, ni par le BPMN (§99), ni par une table DMN (§100).
  - `STORE` enregistre l'objet pour ce dossier.
  - `RETRIEVE`, `EXISTS` et `DELETE` refusent tout objet qui n'a pas été enregistré pour ce même dossier, sans toucher au
    stockage. Le refus fait échouer la tâche de service, donc la complétion qui y mène.
  - `DELETE` retire aussi l'entrée du registre.
  - Un process sans `dossierId` valide est refusé, pour toutes les opérations.
- **En appel direct** (`POST /api/connectors/{id}/execute` en `TENANT_ADMIN`, ou la route M2M en `API_SERVICE`), la
  portée reste le tenant, comme avant. `STORE` enregistre l'objet sans dossier : un process ne peut donc pas le lire.

Choix faits sans arbitrage :
- Piste 1 de l'issue, réduite à sa seconde moitié : le connecteur ne lit pas les pièces du dossier courant. Son rôle
  documenté est le contenu **non lié** aux éléments du dossier ; les pièces ont leurs propres routes, avec leurs contrôles
  d'accès. Rendre une pièce lisible ou effaçable ici créerait un second chemin sans ces contrôles, et un `DELETE` laisserait
  un élément sans contenu.
- La portée est le dossier, pas l'instance de process : un sous-processus (`callActivity`) qui reçoit `dossierId` à
  l'identique, ou un autre workflow du même dossier, relit ce que le connecteur a stocké pour ce dossier.
- Piste 2 écartée : réserver `connectorInput` casserait tous les connecteurs pilotés par la complétion (`http-rest`, `smtp`).
- Les appels directs gardent la portée du tenant. Ces principaux ont déjà des droits sur tout le tenant. Les restreindre
  casserait les intégrations M2M existantes, et cela sort du constat, qui porte sur la complétion de tâche.

**Tests.**
- `DocumentStorageCrossDossierIT`, sur le moteur, avec une complétion qui pose `connectorInput` :
  - lecture et effacement d'une pièce d'un autre dossier, refusés : l'objet reste en place et `connectorResult` n'est pas
    posé ;
  - lecture et test d'existence d'un contenu stocké par le connecteur pour un autre dossier, refusés ;
  - stockage, relecture et effacement pour le même dossier, toujours permis.

  Les trois scénarios d'attaque étaient rouges avant le correctif.
- `DocumentStorageConnectorTest` : enregistrement au `STORE` (avec et sans dossier), refus en process pour chaque opération,
  refus sans dossier, appel direct inchangé.
- `ConnectorServiceTaskTest` : passage du dossier de l'exécution ; un `dossierId` qui n'est pas un UUID donne « sans
  dossier ».

**Revue adversariale (2026-10-24).** Aucun contournement trouvé.
- `dossierId` est la seule entrée de la règle. Il est réservé à la complétion (`assertNoReservedKeys`) et au signal
  (`FlowableSignalConnector`, §93 I7). Il ne peut pas non plus être écrit par le BPMN (§99, sauf recopie à l'identique) ni
  par une table DMN (§100).
- `STORE` crée toujours une nouvelle référence (`DocumentStoragePort.store`). Il ne peut pas enregistrer pour son dossier
  une référence existante.
- `connectorInstanceId` peut encore venir d'une variable quand le BPMN ne fixe pas l'attribut (§73 I1, choix antérieur).
  Une complétion qui redirige l'appel vers une instance `document-storage` reste soumise à la même règle.
- Seuls les appels directs et `connectorServiceTask` exécutent un connecteur SERVICE. Aucun outil MCP ne le fait.
- Cohérence : l'entrée du registre suit la transaction du process, le stockage non. Une transaction annulée après un `STORE`
  laisse un objet orphelin, comme avant (D-STORAGE-REF-LIFECYCLE). Annulée après un `DELETE`, elle laisse une entrée sans
  objet : un `RETRIEVE` échoue alors comme pour un objet absent.

**Avant de déployer.** Les objets stockés par le connecteur avant ce changement ne sont pas au registre. Depuis un process,
leur lecture, leur test d'existence ou leur effacement sont désormais refusés (incident sur la tâche de service), car on ne
sait pas à quel dossier ils appartenaient. Avant la mise à jour, chercher les process en cours qui relisent un objet stocké
plus tôt : les laisser finir, ou les relancer après la mise à jour. Aucun BPMN livré, exemple ou e2e n'utilise ce connecteur.

**Impacte :** `docs/specs/20-connecteurs-hors-v1.md` (§4), `docs/guides/GUIDE-CONCEPTION-BPMN.md` (variables de
`connectorServiceTask`), `docs/guides/GUIDE-UTILISATEUR.md` (catalogue), §97 (constat L4). Migration `036` (nouvelle table,
aucune donnée reprise). Aucune route ni DTO modifiés ; golden OpenAPI inchangé.
