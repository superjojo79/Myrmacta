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
| Utilisateurs | SPA Vue 3 embarquée (fr, en), portail externe pour les tiers, signature électronique (Yousign, DocuSign ou stub, [#191](https://github.com/superjojo79/FlowForge/issues/191), [§109](docs/decisions/109-fournisseurs-de-signature-yousign-et-docusign.md)), notifications et boîte de réception, rapports et SLA |
| IA et MCP | Serveur MCP gouverné (brouillons, provenance, publication), assistant de conception (désactivé par défaut), promotion de configuration entre environnements, pré-vérification IA des dossiers (désactivée par défaut, [#211](https://github.com/superjojo79/FlowForge/issues/211), [§106](docs/decisions/106-preverification-ia-et-types-de-pieces.md)) |
| Socle | Architecture hexagonale vérifiée par ArchUnit, OIDC en BFF, en-têtes de sécurité, antivirus ClamAV, provisioning de tenant, observabilité ECS + Micrometer, authentification belge FAS / itsme ([#210](https://github.com/superjojo79/FlowForge/issues/210), [§104](docs/decisions/104-authentification-belge-fas-et-itsme.md)), tests de mutation PIT hors `verify` ([§108](docs/decisions/108-tests-de-mutation-pit.md)) |

Depuis le 23 octobre, l'exemple riverain peut joindre le PDF à « Notifier »
([#183](https://github.com/superjojo79/FlowForge/issues/183), [§96](docs/decisions/096-piece-jointe-de-la-brique-notifier.md))
et refuser un doublon de champ ouvert
([#184](https://github.com/superjojo79/FlowForge/issues/184), [§102](docs/decisions/102-unicite-d-un-champ-de-dossier.md)).

## Prochaines étapes

État des issues ouvertes au 30 octobre 2026. Le détail et l'ordre réel restent la
[liste d'issues](https://github.com/superjojo79/FlowForge/issues).

1. **Exemple riverain, priorité 3 :** gabarits de notification ([#185](https://github.com/superjojo79/FlowForge/issues/185)),
   minuteur de parcours ([#186](https://github.com/superjojo79/FlowForge/issues/186)), assignation par groupe
   ([#190](https://github.com/superjojo79/FlowForge/issues/190)). Les variables de démarrage déclarées
   ([#187](https://github.com/superjojo79/FlowForge/issues/187), [§111](docs/decisions/111-variables-de-demarrage-declarees.md))
   sont livrées.
2. **À trancher avant code :** dépôt initial par le portail ([#188](https://github.com/superjojo79/FlowForge/issues/188)),
   relecture des champs après le démarrage ([#189](https://github.com/superjojo79/FlowForge/issues/189)),
   exposition MCP résiduelle ([§71](docs/decisions/071-exposition-mcp-de-la-couche-low-code.md)).
3. **Avant un premier client :** recette ClamAV, Keycloak et FAS réels ([#192](https://github.com/superjojo79/FlowForge/issues/192)).
   La double licence est choisie (PolyForm-Noncommercial-1.0.0 et commerciale sur demande) ; le registre [§103](docs/decisions/103-double-licence-agpl-et-licence-commerciale.md)
   reste ouvert le temps de la revue de l'avocat.
4. **Signature par carte eID et horodatage BOSA, priorité 3 :** à cadrer ([#248](https://github.com/superjojo79/FlowForge/issues/248)).
5. **Sans priorité :** eBox et digital mail room ([#213](https://github.com/superjojo79/FlowForge/issues/213) à [#217](https://github.com/superjojo79/FlowForge/issues/217)).

## Hors V1

Redis et SSE multi-pods, OpenTelemetry, génération de client OpenAPI, export BI par JDBC, scripts en bac à
sable (LC6), étapes avancées des parcours : écartés tant qu'un besoin réel ne les justifie pas (#194).
