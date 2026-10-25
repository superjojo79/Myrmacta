# Myrmacta — Roadmap

> Vision produit en une page. Le **suivi de ce qui reste à faire** est dans les
> [issues GitHub](https://github.com/superjojo79/FlowForge/issues) (libellés `priorite-*`, `type-*`,
> `vague-*`) ; ce qui est livré est dans [CHANGELOG.md](CHANGELOG.md), les décisions dans le
> [registre des décisions](docs/decisions/README.md). Version détaillée d'avant le 23 octobre 2026
> (inventaire par catégorie, priorisation de la Vague D) :
> [ROADMAP.md au commit `e91aa7b`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/ROADMAP.md).

## Positionnement

Un moteur de dossiers métier pilotés par des workflows BPMN/DMN, pour **intégrateurs** qui livrent des
flux à leurs clients, avec une **couche low-code** pour les utilisateurs métier ([TODO §70](docs/decisions/070-positionnement-produit-d0-integrateurs-couche-low-code.md),
[analyse 31](docs/specs/31-positionnement-produit.md)). Une instance par tenant (décision n° 13).

## Livré (vagues A à D, septembre–octobre 2026)

| Domaine | Capacités |
|---|---|
| Dossiers | Types et schémas de champs (montants, groupes répétables, références externes, champs calculés et conditionnels), recherche sur champs dynamiques, éléments, relations, rétention et purge |
| Workflows | Flowable 8 BPMN + DMN, éditeurs intégrés, palette de briques métier, concepteur de parcours, langage d'expression borné (JSONata), incidents visibles et rejouables |
| Documents | Modèles Thymeleaf versionnés, éditeur visuel, PDF (Flying Saucer + OpenPDF), aperçu |
| Intégrations | Connecteurs REST, callback, webhooks, Kafka, import batch SFTP, SMTP, e-mail entrant IMAP/Graph, Azure Service Bus, stockage S3/fichiers, OAuth2 client credentials |
| Utilisateurs | SPA Vue 3 embarquée (fr, en), portail externe pour les tiers, signature électronique (socle + stub), notifications et boîte de réception, rapports et SLA |
| IA et MCP | Serveur MCP gouverné (brouillons, provenance, publication), assistant de conception (désactivé par défaut), promotion de configuration entre environnements |
| Socle | Architecture hexagonale vérifiée par ArchUnit, OIDC en BFF, en-têtes de sécurité, antivirus ClamAV, provisioning de tenant, observabilité ECS + Micrometer |

## Prochaines étapes

1. **Backlog de l'exemple « autorisation riverain »** (flux monté par configuration seule) : pièce jointe
   sur « Notifier » (#183) et unicité d'un champ de dossier (#184) en priorité 2, puis gabarits de
   notification, minuteur de parcours, variables de démarrage déclarées et assignation par groupe.
2. **Avant un premier client :** industrialiser le déploiement par tenant, choisir un fournisseur de
   signature réel (#191), recetter ClamAV et Keycloak sur environnement réel (#192).
3. **À trancher :** exposition MCP résiduelle ([TODO §71](docs/decisions/071-exposition-mcp-de-la-couche-low-code.md)),
   demande initiale par le portail (#188), relecture des champs en cours de workflow (#189).

## Hors V1

Redis et SSE multi-pods, OpenTelemetry, génération de client OpenAPI, export BI par JDBC, scripts en bac à
sable (LC6), étapes avancées des parcours : écartés tant qu'un besoin réel ne les justifie pas (#194).
