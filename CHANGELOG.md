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

- **PR [#87](https://github.com/superjojo79/FlowForge/pull/87)** — Écran Admin → Types permet
  désormais d'éditer le schéma des champs dynamiques (`fieldDefinitions`) d'un `DossierType`, en
  création et en édition, ainsi que la bascule `allowUnknownFields`. Une modification qui rendrait
  des dossiers existants incohérents avec le nouveau schéma est désormais refusée (409) plutôt
  qu'acceptée silencieusement.
- **PR [#83](https://github.com/superjojo79/FlowForge/pull/83)** — Service `prometheus` optionnel
  dans `docker-compose.yml` (profil dédié, `network_mode: host` pour respecter le bind loopback
  du port management) : scrape local de `/actuator/prometheus`, déjà exposé par le backend.

### Modifié

- **PR [#84](https://github.com/superjojo79/FlowForge/pull/84)** — Le SQL brut des changelogs
  Liquibase (blocs `sql:` en changes et rollback) est déporté dans des fichiers `.sql` importés
  via `sqlFile:`, pour une meilleure lisibilité et un lint SQL possible. Aucun changement de
  comportement des migrations.

### Modifié

- **PR (à ouvrir)** — La couche `application` est désormais gardée par ArchUnit comme le
  domaine (TODO §46). `ManualStartOrchestrator` dépend d'un nouveau port
  `WorkflowStartConfirmationPort` au lieu de la classe infrastructure `WorkflowStartConfirmer`
  directement ; aucun changement de comportement.

### Corrigé

- **PR [#86](https://github.com/superjojo79/FlowForge/pull/86)** — Les champs dynamiques d'un
  dossier (`Dossier.fields()`) sont désormais injectés automatiquement comme variable de
  processus réservée `dossierFields` à chaque démarrage de workflow (API manuelle, relais
  outbox, réconciliation). Auparavant invisibles depuis un `serviceTask` ou un template de
  document sans propagation manuelle de chaque champ.
