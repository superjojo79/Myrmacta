# Registre des décisions

> Généré par `scripts/check-docs.sh --write-decisions-index` à partir de l'en-tête de chaque fichier :
> ne pas modifier ce tableau à la main. Le contrôle `decisions-index` échoue s'il n'est plus à jour.

Ce registre remplace l'ancien `TODO.md` (TODO §95, D-REMED-REGISTRY). Chaque point y est un fichier
`NNN-<titre>.md` dont le numéro ne change jamais : **« TODO §N » et « décision n° N », cités par le
code et la documentation, désignent le fichier `NNN-*.md`**. Un point se clôt en changeant son statut,
jamais en le supprimant ou en le renumérotant ; un nouveau point prend le numéro suivant.

- Les points **ouverts** sont des décisions encore à trancher : ne pas en choisir une option en
  silence (`flowforge-conventions`).
- Les identifiants fins (`D-…`, `DD-…`) cités par le code sont indexés dans
  [identifiants.md](identifiants.md).
- Le journal du mandat « roadmap complète » (2026, figé) est dans [roadmap-2026/](roadmap-2026/DECISIONS.md).
- Les sections de l'ancien registre gardent leur texte d'introduction dans les fichiers `section-*.md`.

## Points

| § | Point | Statut | Section |
|---|---|---|---|
| 1 | [Validation du `tenantId` avant injection dans le `search_path`](001-validation-du-tenantid-avant-injection-dans-le.md) | clos | [Décisions actées › Sécurité](section-decisions-securite.md) |
| 2 | [Comportement si le claim `tenant_id` est absent du token OIDC](002-comportement-si-le-claim-tenant-id-est.md) | clos | [Décisions actées › Sécurité](section-decisions-securite.md) |
| 3 | [Authentification des webhooks entrants](003-authentification-des-webhooks-entrants.md) | clos | [Décisions actées › Sécurité](section-decisions-securite.md) |
| 4 | [Gestion des clés de chiffrement des secrets de connecteurs](004-gestion-des-cles-de-chiffrement-des-secrets.md) | clos | [Décisions actées › Sécurité](section-decisions-securite.md) |
| 5 | [`ConnectorInstance` — entité JPA ou objet domaine ?](005-connectorinstance-entite-jpa-ou-objet-domaine.md) | clos | [Décisions actées › Architecture](section-decisions-architecture.md) |
| 6 | [Extensibilité de `WorkflowEventPublisher`](006-extensibilite-de-workfloweventpublisher.md) | clos | [Décisions actées › Architecture](section-decisions-architecture.md) |
| 7 | [Durabilité des événements de déclenchement workflow](007-durabilite-des-evenements-de-declenchement-workflow.md) | clos | [Décisions actées › Architecture](section-decisions-architecture.md) |
| 8 | [Validation des champs dynamiques des dossiers](008-validation-des-champs-dynamiques-des-dossiers.md) | clos | [Décisions actées › Architecture](section-decisions-architecture.md) |
| 9 | [Duplication `usecase/` et `port/in/` dans le domaine dossier](009-duplication-usecase-et-port-in-dans-le.md) | clos | [Décisions actées › Architecture](section-decisions-architecture.md) |
| 10 | [Stratégie de migration Liquibase multi-tenant](010-strategie-de-migration-liquibase-multi-tenant.md) | clos | [Décisions actées › Opérationnel](section-decisions-operationnel.md) |
| 11 | [Scaling horizontal et scheduler Flowable Community](011-scaling-horizontal-et-scheduler-flowable-community.md) | clos | [Décisions actées › Opérationnel](section-decisions-operationnel.md) |
| 12 | [Fichier `architecture-technique v7.md` — référence ou archive ?](012-fichier-architecture-technique-v7-md-reference-ou.md) | clos | [Décisions actées › Opérationnel](section-decisions-operationnel.md) |
| 13 | [Modèle de déploiement multi-tenant — instance dédiée par tenant](013-modele-de-deploiement-multi-tenant-instance-dediee.md) | clos | [Décisions actées › Opérationnel](section-decisions-operationnel.md) |
| 14 | [Résolution du tenant pour les appels non authentifiés — routage par chemin + `client_credentials` par tenant](014-resolution-du-tenant-pour-les-appels-non.md) | clos | [Décisions actées › Opérationnel](section-decisions-operationnel.md) |
| 15 | [Sécurité et cycle de vie de la session BFF — CSRF, déconnexion, session par tenant](015-securite-et-cycle-de-vie-de-la.md) | clos | [Décisions actées › Opérationnel](section-decisions-operationnel.md) |
| 16 | [Outbox transactionnel et `ConnectorTriggerRuntime` — option B](016-outbox-transactionnel-et-connectortriggerruntime-option-b.md) | clos | [Décisions actées › Opérationnel](section-decisions-operationnel.md) |
| 17 | [Entités JPA dans le domaine — règle appliquée à toutes les entités](017-entites-jpa-dans-le-domaine-regle-appliquee.md) | clos | [Décisions actées › Opérationnel](section-decisions-operationnel.md) |
| 18 | [Résidus de refactoring et références orphelines](018-residus-de-refactoring-et-references-orphelines.md) | clos | [Décisions actées › Opérationnel](section-decisions-operationnel.md) |
| 19 | [Granularité des rôles RBAC](019-granularite-des-roles-rbac.md) | clos | [Points ouverts › Autorisation et conformité](section-points-autorisation-et-conformite.md) |
| 20 | [Contrôle d'accès au niveau type de dossier — `AccessContext` + `dossier_type_acl` + policy](020-controle-d-acces-au-niveau-type-de.md) | clos | [Décisions actées › Opérationnel](section-decisions-operationnel.md) |
| 21 | [Politique de rétention par `DossierType`](021-politique-de-retention-par-dossiertype.md) | clos | [Points ouverts › Autorisation et conformité](section-points-autorisation-et-conformite.md) |
| 22 | [Purge cohérente PostgreSQL / object store](022-purge-coherente-postgresql-object-store.md) | clos | [Points ouverts › Autorisation et conformité](section-points-autorisation-et-conformite.md) |
| 23 | [Classification des champs dynamiques sensibles](023-classification-des-champs-dynamiques-sensibles.md) | clos | [Points ouverts › Autorisation et conformité](section-points-autorisation-et-conformite.md) |
| 24 | [Contrat d'API — surface incomplète et format d'erreur non normalisé](024-contrat-d-api-surface-incomplete-et-format.md) | clos | [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) |
| 25 | [Modèle physique de données consolidé](025-modele-physique-de-donnees-consolide.md) | clos | [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) |
| 26 | [Concurrence sur le dossier et unicité de la référence](026-concurrence-sur-le-dossier-et-unicite-de.md) | clos | [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) |
| 27 | [Versions figées, structure du build et packaging du frontend](027-versions-figees-structure-du-build-et-packaging.md) | clos | [Décisions actées › Opérationnel](section-decisions-operationnel.md) |
| 28 | [Stratégie de test](028-strategie-de-test.md) | clos | [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) |
| 29 | [Observabilité](029-observabilite.md) | clos | [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) |
| 30 | [Durcissement des points d'entrée exposés](030-durcissement-des-points-d-entree-exposes.md) | clos | [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) |
| 31 | [Découpage en lots d'implémentation](031-decoupage-en-lots-d-implementation.md) | clos | [Points ouverts › Processus](section-points-processus.md) |
| 32 | [Retrait synchronisé des exclusions autoconfig temporaires (Security/OAuth2/Flowable)](032-retrait-synchronise-des-exclusions-autoconfig-temporaires-se.md) | clos | [Décisions actées › Sécurité](section-decisions-securite.md) |
| 33 | [Pattern outbox pour les notifications ; `WorkflowEventPublisher` = no-op en production](033-pattern-outbox-pour-les-notifications-workfloweventpublisher.md) | clos | [Décisions actées › Architecture](section-decisions-architecture.md) |
| 34 | [ACL type de dossier sur le panneau dossier de la visualisation workflow](034-acl-type-de-dossier-sur-le-panneau.md) | clos | [Décisions actées › Opérationnel](section-decisions-operationnel.md) |
| 35 | [Modélisation et déploiement des diagrammes BPMN — écart vision / implémentation](035-modelisation-et-deploiement-des-diagrammes-bpmn-ecart.md) | clos | [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) |
| 36 | [`canEditDossier` (frontend) ne reflète pas la vraie règle d'autorisation backend](036-caneditdossier-frontend-ne-reflete-pas-la-vraie.md) | clos | [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) |
| 37 | [`findClosedForRetention` sans `tenantId` explicite — rupture du pattern « ceinture et bretelles »](037-findclosedforretention-sans-tenantid-explicite-rupture-du-pa.md) | clos | [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) |
| 38 | [`PurgeOutboxPayloads.json` — échappement JSON incomplet + `DossierType.code` sans validation de format](038-purgeoutboxpayloads-json-echappement-json-incomplet-dossiert.md) | clos | [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) |
| 39 | [Construction de diagrammes BPMN dans FlowForge — écart restant après §35](039-construction-de-diagrammes-bpmn-dans-flowforge-ecart.md) | clos | [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) |
| 40 | [SSTI Thymeleaf / SpEL dans les templates stockés](040-ssti-thymeleaf-spel-dans-les-templates-stockes.md) | clos | [Points ouverts › Alignement post-implémentation et sécurité (2026-08-25)](section-points-alignement-post-implementation-et-securite.md) |
| 41 | [Isolation PostgreSQL par schéma sans GRANT par tenant](041-isolation-postgresql-par-schema-sans-grant-par.md) | clos | [Points ouverts › Alignement post-implémentation et sécurité (2026-08-25)](section-points-alignement-post-implementation-et-securite.md) |
| 42 | [Actuator `permitAll` et conflit de port 8081](042-actuator-permitall-et-conflit-de-port-8081.md) | clos | [Points ouverts › Alignement post-implémentation et sécurité (2026-08-25)](section-points-alignement-post-implementation-et-securite.md) |
| 43 | [Secrets par défaut et HMAC M2M hors profil local](043-secrets-par-defaut-et-hmac-m2m-hors.md) | clos | [Points ouverts › Alignement post-implémentation et sécurité (2026-08-25)](section-points-alignement-post-implementation-et-securite.md) |
| 44 | [SSRF webhook sortant — DNS TOCTOU](044-ssrf-webhook-sortant-dns-toctou.md) | clos | [Points ouverts › Alignement post-implémentation et sécurité (2026-08-25)](section-points-alignement-post-implementation-et-securite.md) |
| 45 | [Dérive documentaire analyse / README / code](045-derive-documentaire-analyse-readme-code.md) | clos | [Points ouverts › Alignement post-implémentation et sécurité (2026-08-25)](section-points-alignement-post-implementation-et-securite.md) |
| 46 | [Couche `application` non gardée par ArchUnit](046-couche-application-non-gardee-par-archunit.md) | clos | [Points ouverts › Alignement post-implémentation et sécurité (2026-08-25)](section-points-alignement-post-implementation-et-securite.md) |
| 47 | [Édition du schéma des champs dynamiques — écran admin `DossierType`](047-edition-du-schema-des-champs-dynamiques-ecran.md) | clos | [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) |
| 48 | [Activation du connecteur `http-rest` en rôle SERVICE](048-activation-du-connecteur-http-rest-en-role.md) | clos | [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) |
| 49 | [Assainissement de la dette technique — reliquats [`DEFERRED-FEATURES.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/DEFERRED-FEATURES.md) §1](049-assainissement-de-la-dette-technique-reliquats-deferred.md) | clos | [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) |
| 50 | [Nouveaux connecteurs hors V1 — `scheduler`, `smtp`, `document-storage`](050-nouveaux-connecteurs-hors-v1-scheduler-smtp-document.md) | clos | [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) |
| 51 | [Corrélation par clé métier pour callbacks asynchrones — connecteur `flowable-callback`](051-correlation-par-cle-metier-pour-callbacks-asynchrones.md) | clos | [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) |
| 52 | [Formulaires dynamiques rattachés aux tâches BPMN (`userTask`)](052-formulaires-dynamiques-rattaches-aux-taches-bpmn-usertask.md) | clos | [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) |
| 53 | [`WebhookController` : un événement TRIGGER échoué peut bloquer les rejeux avec la même `Idempotency-Key`](053-webhookcontroller-un-evenement-trigger-echoue-peut-bloquer.md) | clos | [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) |
| 54 | [2ème revue de sécurité adversariale sur `connecteur-callback` — 4 nouveaux écarts (F5–F8)](054-2eme-revue-de-securite-adversariale-sur-connecteur.md) | clos | [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) |
| 55 | [Nouveau kind de tâche générique — questions/choix utilisateur indépendants du dossier](055-nouveau-kind-de-tache-generique-questions-choix.md) | clos | [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) |
| 56 | [Extension du serveur MCP au flux métier complet (connecteurs, association templates, questions de tâche)](056-extension-du-serveur-mcp-au-flux-metier.md) | clos | [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) |
| 57 | [Revue adversariale 2026-09-07 — durcissement High / Medium](057-revue-adversariale-2026-09-07-durcissement-high.md) | clos | [Points ouverts › Security](section-points-security.md) |
| 58 | [Contre-revue adversariale post-merge PR #113 — H1 incomplet + 2 Medium](058-contre-revue-adversariale-post-merge-pr-113.md) | clos | [Points ouverts › Security](section-points-security.md) |
| 59 | [Formulaire admin connecteurs généré depuis `configSchema` (lot A2)](059-formulaire-admin-connecteurs-genere-depuis-configschema-lot.md) | clos | [Points ouverts › Security](section-points-security.md) |
| 60 | [Écritures métier MCP — `create_dossier` / `add_dossier_element` / `start_workflow` / `generate_document`](060-ecritures-metier-mcp-create-dossier-add-dossier.md) | clos | [Points ouverts › Security](section-points-security.md) |
| 61 | [Introduction de DMN — lever l'exclusion V1](061-introduction-de-dmn-lever-l-exclusion-v1.md) | clos | [Points ouverts › Security](section-points-security.md) |
| 62 | [Nouveaux connecteurs `batch-import` (SFTP) et `kafka` — lot B1 de la file d'attente](062-nouveaux-connecteurs-batch-import-sftp-et-kafka.md) | clos | [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) |
| 63 | [Headers HTTP globaux — CSP / HSTS / X-Frame-Options (lot A4)](063-headers-http-globaux-csp-hsts-x-frame.md) | clos | [Points ouverts › Security](section-points-security.md) |
| 64 | [Durcissement post-merge des en-têtes HTTP (revue adversariale PR #128)](064-durcissement-post-merge-des-en-tetes-http.md) | clos | [Points ouverts › Security](section-points-security.md) |
| 65 | [Enrichissement du reporting — alerting SLA, widgets, export BI](065-enrichissement-du-reporting-alerting-sla-widgets-export.md) | clos | [Points ouverts › Security](section-points-security.md) |
| 66 | [Inbox de notifications persistée — historique utilisateur](066-inbox-de-notifications-persistee-historique-utilisateur.md) | clos | [Points ouverts › Security](section-points-security.md) |
| 67 | [Outillage DMN — évaluation ad hoc, jeux de tests, historique/rollback](067-outillage-dmn-evaluation-ad-hoc-jeux-de.md) | clos | [Points ouverts › Security](section-points-security.md) |
| 68 | [Portail externe tokenisé — tâche externe par lien magique](068-portail-externe-tokenise-tache-externe-par-lien.md) | clos | [Points ouverts › Security](section-points-security.md) |
| 69 | [Surfaces UEL du BPMN hors allowlist — suite de H1-bis (High)](069-surfaces-uel-du-bpmn-hors-allowlist-suite.md) | clos | [Points ouverts › Security](section-points-security.md) |
| 70 | [Positionnement produit (D0) — intégrateurs + couche low-code](070-positionnement-produit-d0-integrateurs-couche-low-code.md) | clos | [Points ouverts › Security](section-points-security.md) |
| 71 | [Exposition MCP de la couche low-code — cadrage transverse](071-exposition-mcp-de-la-couche-low-code.md) | ouvert | [Points ouverts › Security](section-points-security.md) |
| 72 | [Signature électronique — socle générique et stub (livré)](072-signature-electronique-socle-generique-et-stub.md) | clos | [Points ouverts › Security](section-points-security.md) |
| 73 | [Palette métier dans l'éditeur BPMN (LC1) (livré)](073-palette-metier-dans-l-editeur-bpmn-lc1.md) | clos | [Points ouverts › Autorisation et conformité](section-points-autorisation-et-conformite.md) |
| 74 | [Palette métier — liste des tables DMN déployées (LC1-suite) (livré)](074-palette-metier-liste-des-tables-dmn-deployees.md) | clos | [Points ouverts › Autorisation et conformité](section-points-autorisation-et-conformite.md) |
| 75 | [Langage d'expression métier borné (LC2) (livré)](075-langage-d-expression-metier-borne-lc2.md) | clos | [Points ouverts › Autorisation et conformité](section-points-autorisation-et-conformite.md) |
| 76 | [Modèle de champs enrichi — types structurés (D1) (livré)](076-modele-de-champs-enrichi-types-structures-d1.md) | clos | [Points ouverts › Autorisation et conformité](section-points-autorisation-et-conformite.md) |
| 77 | [OAuth2 client credentials sur `http-rest` (D3a) (livré)](077-oauth2-client-credentials-sur-http-rest-d3a.md) | clos | [Points ouverts › Autorisation et conformité](section-points-autorisation-et-conformite.md) |
| 78 | [Mapping d'une réponse de connecteur → champs du dossier (D3b) (livré)](078-mapping-d-une-reponse-de-connecteur-champs.md) | clos | [Points ouverts › Autorisation et conformité](section-points-autorisation-et-conformite.md) |
| 79 | [Tâches planifiées sûres sous double exécution (D5) (livré)](079-taches-planifiees-sures-sous-double-execution-d5.md) | clos | [Points ouverts › Autorisation et conformité](section-points-autorisation-et-conformite.md) |
| 80 | [D1 règles — champs calculés, visibilité et obligation conditionnelles](080-d1-regles-champs-calcules-visibilite-et-obligation.md) | clos | [Décisions actées › Livraisons de la Vague D — mandat « roadmap complète » (§80 à §90)](section-decisions-livraisons-de-la-vague-d.md) |
| 81 | [D2 — recherche sur champs dynamiques](081-d2-recherche-sur-champs-dynamiques.md) | clos | [Décisions actées › Livraisons de la Vague D — mandat « roadmap complète » (§80 à §90)](section-decisions-livraisons-de-la-vague-d.md) |
| 82 | [D6 — socle MCP low-code (brouillons, provenance, modes, acteur)](082-d6-socle-mcp-low-code-brouillons-provenance.md) | clos | [Décisions actées › Livraisons de la Vague D — mandat « roadmap complète » (§80 à §90)](section-decisions-livraisons-de-la-vague-d.md) |
| 83 | [D4 — promotion de configuration entre environnements](083-d4-promotion-de-configuration-entre-environnements.md) | clos | [Décisions actées › Livraisons de la Vague D — mandat « roadmap complète » (§80 à §90)](section-decisions-livraisons-de-la-vague-d.md) |
| 84 | [LC4b — éditeur visuel de templates](084-lc4b-editeur-visuel-de-templates.md) | clos | [Décisions actées › Livraisons de la Vague D — mandat « roadmap complète » (§80 à §90)](section-decisions-livraisons-de-la-vague-d.md) |
| 85 | [LC4a — concepteur visuel de formulaires](085-lc4a-concepteur-visuel-de-formulaires.md) | clos | [Décisions actées › Livraisons de la Vague D — mandat « roadmap complète » (§80 à §90)](section-decisions-livraisons-de-la-vague-d.md) |
| 86 | [LC3 — assistant de conception IA](086-lc3-assistant-de-conception-ia.md) | clos | [Décisions actées › Livraisons de la Vague D — mandat « roadmap complète » (§80 à §90)](section-decisions-livraisons-de-la-vague-d.md) |
| 87 | [D3c + D3d — e-mail entrant, bus Azure](087-d3c-d3d-e-mail-entrant-bus-azure.md) | clos | [Décisions actées › Livraisons de la Vague D — mandat « roadmap complète » (§80 à §90)](section-decisions-livraisons-de-la-vague-d.md) |
| 88 | [LC5 — concepteur de parcours](088-lc5-concepteur-de-parcours.md) | clos | [Décisions actées › Livraisons de la Vague D — mandat « roadmap complète » (§80 à §90)](section-decisions-livraisons-de-la-vague-d.md) |
| 89 | [LC6 — scripts en bac à sable](089-lc6-scripts-en-bac-a-sable.md) | clos | [Décisions actées › Livraisons de la Vague D — mandat « roadmap complète » (§80 à §90)](section-decisions-livraisons-de-la-vague-d.md) |
| 90 | [D7 — internationalisation (fr, en)](090-d7-internationalisation-fr-en.md) | clos | [Décisions actées › Livraisons de la Vague D — mandat « roadmap complète » (§80 à §90)](section-decisions-livraisons-de-la-vague-d.md) |
| 91 | [PostgreSQL embarqué (zonky) pour les tests et les démos/QA (livré)](091-postgresql-embarque-zonky-pour-les-tests-et.md) | clos | [Points ouverts › Autorisation et conformité](section-points-autorisation-et-conformite.md) |
| 92 | [Assignation des tâches — « M'assigner » et annuaire local des utilisateurs](092-assignation-des-taches-m-assigner-et-annuaire.md) | clos | — |
| 93 | [Brouillons avec ressource inactive, destinataire de « Notifier » depuis le dossier, variables de démarrage](093-brouillons-avec-ressource-inactive-destinataire-de-notifier.md) | clos | — |
| 94 | [Incidents visibles et rejouables sur le dossier](094-incidents-visibles-et-rejouables-sur-le-dossier.md) | clos | — |
| 95 | [Remédiation de la revue du 22 octobre 2026](095-remediation-de-la-revue-du-22-octobre.md) | clos | — |
| 96 | [Pièce jointe de la brique « Notifier »](096-piece-jointe-de-la-brique-notifier.md) | clos | — |
| 97 | [`generatedDocumentId` réservé : pas de rattachement d'un document d'un autre dossier](097-generateddocumentid-reserve.md) | clos | — |
| 98 | [Feuille de style par défaut du rendu PDF](098-feuille-de-style-par-defaut-du-rendu-pdf.md) | clos | — |
| 99 | [Le BPMN ne peut pas recopier une valeur dans une variable réservée](099-recopie-bpmn-vers-une-variable-reservee.md) | clos | — |
| 100 | [Une table DMN ne peut pas écrire une variable réservée](100-sortie-dmn-vers-une-variable-reservee.md) | clos | — |
| 101 | [Le connecteur `document-storage` ne touche que ce qu'il a stocké pour le dossier](101-document-storage-limite-au-dossier.md) | clos | — |
| 102 | [Unicité d'un champ de dossier](102-unicite-d-un-champ-de-dossier.md) | ouvert | — |
| 103 | [Authentification belge (FAS, itsme) des agents et des tiers du portail](103-authentification-belge-fas-et-itsme.md) | ouvert | — |

## Sections de l'ancien registre

- [decisions-actees](section-decisions-actees.md)
- [decisions-architecture](section-decisions-architecture.md)
- [decisions-livraisons-de-la-vague-d](section-decisions-livraisons-de-la-vague-d.md)
- [decisions-operationnel](section-decisions-operationnel.md)
- [decisions-securite](section-decisions-securite.md)
- [points-alignement-post-implementation-et-securite](section-points-alignement-post-implementation-et-securite.md)
- [points-autorisation-et-conformite](section-points-autorisation-et-conformite.md)
- [points-bloquants-a-trancher-avant-le](section-points-bloquants-a-trancher-avant-le.md)
- [points-complements-de-conception](section-points-complements-de-conception.md)
- [points-contradictions-internes-a-lever](section-points-contradictions-internes-a-lever.md)
- [points-ouverts](section-points-ouverts.md)
- [points-processus](section-points-processus.md)
- [points-security](section-points-security.md)
