# Changelog

Historique synthétique des changements notables du projet, **un lot par pull request mergée** —
pas un journal de commits. Format inspiré de
[Keep a Changelog](https://keepachangelog.com/fr/1.1.0/), adapté à un projet sans releases
versionnées à ce jour (voir `README.md` → Statut) : ce fichier ne connaît pas de section `[X.Y.Z]`
tant qu'aucune version n'est publiée, seulement une section **[Non publié]** qui grandit au fil
des PR.

> **Ce que ce fichier n'est pas.** Pour l'historique technique détaillé (décisions actées,
> alternatives écartées, bugs trouvés en revue, chiffres de couverture) : voir `TODO.md` et
> `FILE-ATTENTE.md` § *Récemment livré*. `CHANGELOG.md` reste volontairement court — une entrée
> tient en 2 à 5 lignes, lisible sans avoir suivi le projet.

Catégories utilisées (n'inclure que celles pertinentes à l'entrée) : **Ajouté**, **Modifié**,
**Corrigé**, **Sécurité**, **Documentation**, **Déprécié**, **Supprimé**.

Règle d'alimentation — voir `flowforge-conventions` (skill projet) : chaque pull request qui
touche du code, de la configuration ou de la documentation utilisateur/admin ajoute (ou met à
jour, si la PR est encore en cours) **une entrée** dans **[Non publié]**, sous ce format :

```markdown
- **PR #123** — Résumé en une phrase de ce qui change et pourquoi. Détail court supplémentaire
  si nécessaire (2ᵉ phrase max).
```

------

## [Non publié]

### Documentation

- **PR [#83](https://github.com/superjojo79/FlowForge/pull/83)** — Rattrapage de la documentation
  admin/utilisateur : trois écrans admin livrés mais jamais documentés (webhooks de notification,
  file de purge RGPD, observabilité), rétention par type de dossier, limite du connecteur
  `service`, canal email (config SMTP + limites connues), nouveau guide `DEPLOIEMENT.md`
  (reverse-proxy, provisioning manuel d'un tenant, gestion des utilisateurs/rôles Keycloak).
- **PR [#83](https://github.com/superjojo79/FlowForge/pull/83)** — Ajout de ce fichier
  `CHANGELOG.md` et de la règle d'alimentation associée dans le skill `flowforge-conventions`.
- **PR [#86](https://github.com/superjojo79/FlowForge/pull/86)** — Nouveau guide
  `GUIDE-CONCEPTION-BPMN.md` (racine du dépôt) : conventions de nommage, catalogue des
  `serviceTask` disponibles, référencement des champs dynamiques du dossier, instanciation d'un
  service externe, référencement des intervenants, déclenchement de la génération de documents —
  destiné à quiconque conçoit un diagramme BPMN pour FlowForge.

### Ajouté

- **PR [#111](https://github.com/superjojo79/FlowForge/pull/111)** — Le serveur MCP interne
  (`POST /api/m2m/mcp`, PR #82) couvre désormais le flux métier complet (TODO §56, 9 → 16 tools) :
  7 nouveaux tools (connecteurs, questions de tâche) et `define_dossier_type` étendu
  (`documentTemplateKeys`). Un agent M2M peut définir un connecteur, un type de dossier, une
  question de tâche et déployer un workflow sans jamais sortir du canal MCP.
- **PR #107** — Formulaires de tâche rattachés aux `userTask` BPMN (TODO §52) : un `userTask`
  peut désormais déclarer
  `flowable:formKey` (éditeur BPMN, groupe « Flowable ») pour afficher un formulaire avant
  complétion — édition des champs du dossier (`dossier-fields`) ou choix d'un document à générer
  parmi ceux associés au type de dossier (`document-generation`, nouvel écran Admin → Types).
  Nouvel endpoint `GET /api/dossier-types/{code}/document-templates` (accessible à tout porteur de
  tâche ayant VIEW sur le type, pas seulement `TENANT_ADMIN`). Corrige au passage un bug latent de
  l'éditeur BPMN : le tout premier attribut `flowable:*` posé sur un diagramme démarré vierge dans
  cet éditeur ne se sauvegardait pas (espace de noms `flowable` non déclaré).
- **PR [#102](https://github.com/superjojo79/FlowForge/pull/102)** — Nouveau connecteur générique
  `flowable-callback` (TODO §51, capability **CALLBACK**) : un process BPMN en attente sur un
  `intermediateCatchEvent`/`receiveTask` à message peut désormais être réveillé par un callback
  HTTP asynchrone (`POST /api/callbacks/{instanceId}`, même auth/idempotence que les webhooks
  existants), corrélé par une valeur de clé métier extraite du payload — indépendant du connecteur
  SERVICE ayant émis l'appel sortant initial.
- **PR [#95](https://github.com/superjojo79/FlowForge/pull/95)** — Le connecteur `http-rest` est désormais utilisable en rôle **SERVICE** en
  plus de TRIGGER (TODO §48) : un `serviceTask` BPMN ou un appel `POST
  /api/connectors/{id}/execute` (BFF/M2M) peut appeler un service REST externe (GET/POST/PUT,
  en-tête `Authorization` optionnel par instance), avec le même durcissement anti-SSRF que les
  webhooks sortants de notification.
- **PR [#87](https://github.com/superjojo79/FlowForge/pull/87)** — Écran Admin → Types permet
  désormais d'éditer le schéma des champs dynamiques (`fieldDefinitions`) d'un `DossierType`, en
  création et en édition, ainsi que la bascule `allowUnknownFields`. Une modification qui rendrait
  des dossiers existants incohérents avec le nouveau schéma est désormais refusée (409) plutôt
  qu'acceptée silencieusement.
- **PR [#83](https://github.com/superjojo79/FlowForge/pull/83)** — Service `prometheus` optionnel
  dans `docker-compose.yml` (profil dédié, `network_mode: host` pour respecter le bind loopback
  du port management) : scrape local de `/actuator/prometheus`, déjà exposé par le backend.
- **PR [#99](https://github.com/superjojo79/FlowForge/pull/99)** — 3 nouveaux types de connecteurs (TODO §50) : `scheduler` (TRIGGER, démarre
  un workflow à intervalle cron via un job de poll dédié), `smtp` (SERVICE, envoi email ad hoc
  depuis un `serviceTask`, réutilise `EmailPort`) et `document-storage` (SERVICE, lecture/écriture
  de contenu hors dossier via `DocumentStoragePort` — généralisé depuis le candidat `s3-minio`,
  aucun couplage à MinIO/S3). Catalogue de connecteurs `06-connecteurs.md`/`README.md` mis à jour.

### Modifié

- **PR [#84](https://github.com/superjojo79/FlowForge/pull/84)** — Le SQL brut des changelogs
  Liquibase (blocs `sql:` en changes et rollback) est déporté dans des fichiers `.sql` importés
  via `sqlFile:`, pour une meilleure lisibilité et un lint SQL possible. Aucun changement de
  comportement des migrations.
- **PR [#93](https://github.com/superjojo79/FlowForge/pull/93)** — La couche `application` est désormais gardée par ArchUnit comme le
  domaine (TODO §46). `ManualStartOrchestrator` dépend d'un nouveau port
  `WorkflowStartConfirmationPort` au lieu de la classe infrastructure `WorkflowStartConfirmer`
  directement ; aucun changement de comportement.
- **PR [#97](https://github.com/superjojo79/FlowForge/pull/97)** — Assainissement de dette technique (TODO §49) : `processKey`/`workflowKey`
  blank désormais rejetés au niveau domaine (`WorkflowTypeConfig`, `TriggerOutboxPayload`,
  `WorkflowStartedEvent`, `DossierType`), et `dossierId` désormais présent en MDC pendant le
  traitement de chaque item du job de purge (auparavant seul `tenantId` du batch). Aucun changement
  de comportement pour les appels existants déjà valides.

### Corrigé

- **PR [#111](https://github.com/superjojo79/FlowForge/pull/111)** — Revue de sécurité
  adversariale sur l'extension du serveur MCP (TODO §56) : le champ `authHeaderValue` du
  connecteur `http-rest` (un credential HTTP réel, déclaré `PASSWORD` dans son schéma) n'était
  reconnu par aucune heuristique de détection de secret par nom — jamais chiffré en base ni
  masqué en lecture. Corrigé en basant la détection sur le schéma déclaré du connecteur
  (`ConnectorCatalog.isPasswordField`) plutôt que sur des heuristiques de nom dupliquées 3 fois.
  Même revue : garde-fou domaine (`TenantAdminGuard`) manquant depuis PR #82 sur les use cases
  connecteurs/questions de tâche/types de workflow, comblé pour tout appelant MCP.
- **PR [#106](https://github.com/superjojo79/FlowForge/pull/106)** — 2ème revue de sécurité
  adversariale, indépendante, sur `connecteur-callback` (TODO §54) : DoS mémoire non authentifié
  (le corps de requête était intégralement bufférisé avant toute garde applicative, contournable
  par `Transfer-Encoding: chunked`), absence de protection anti-rejeu sur la signature HMAC
  (**rupture de contrat assumée pour les appelants existants** — en-tête `X-Webhook-Timestamp`
  désormais requis), aucune longueur minimale sur `webhookSecret`, échecs d'authentification
  webhook jamais journalisés. 4 écarts corrigés, 13 tests de régression ajoutés.
- **PR [#104](https://github.com/superjojo79/FlowForge/pull/104)** — `WebhookController` (TODO
  §53) : un événement webhook (TRIGGER) qui échoue complète désormais la réclamation
  d'idempotence avec le statut déterministe de l'échec au lieu de la laisser bloquée `IN_FLIGHT`
  jusqu'à 24h, ce qui empêchait tout rejeu légitime avec la même `Idempotency-Key`. Même
  correctif que la PR #103 pour `CallbackWebhookController`, appliqué au défaut latent partagé
  laissé en dette.
- **PR [#103](https://github.com/superjojo79/FlowForge/pull/103)** — Revue de sécurité
  adversariale post-merge sur le connecteur `flowable-callback` (TODO §51) : `/api/callbacks/**`
  n'était couvert par aucune règle de sécurité Spring et recevait systématiquement un 403 avant
  d'atteindre le contrôleur — la fonctionnalité était totalement inatteignable en production. 4
  autres écarts corrigés dans le même lot : garde de taille de corps absente sur ce chemin,
  idempotence non complétée sur échec de callback, ambiguïté de corrélation non journalisée,
  documentation manquante sur l'entropie requise de la valeur de corrélation.
- **PR [#86](https://github.com/superjojo79/FlowForge/pull/86)** — Les champs dynamiques d'un
  dossier (`Dossier.fields()`) sont désormais injectés automatiquement comme variable de
  processus réservée `dossierFields` à chaque démarrage de workflow (API manuelle, relais
  outbox, réconciliation). Auparavant invisibles depuis un `serviceTask` ou un template de
  document sans propagation manuelle de chaque champ.
