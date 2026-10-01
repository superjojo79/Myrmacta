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

### Ajouté

- **TODO §90 (D7)** — **Interface en français et en anglais** (`vue-i18n`) : tout le SPA et le portail public, avec sélecteur de langue (choix conservé dans le navigateur), langue par défaut du
  tenant (`app.i18n.default-locale` / `APP_I18N_DEFAULT_LOCALE`, exposée par `/api/auth/me`), dates et nombres selon la langue, messages de validation et de repli traduits. Les e-mails d'invitation de
  tâche externe et de signature suivent la langue du tenant (lien `?lang=en` en anglais, lien français inchangé). Non traduits (journalisé) : libellés saisis par le tenant, détail d'erreur de l'API,
  diagnostics techniques des éditeurs de template et BPMN. Garde-fous : parité fr/en typée et testée, test de couverture des templates, E2E-I18N-1.
- **TODO §89 (LC6, spike)** — Scripts en bac à sable : **non lancé** (D-SCRIPT-GO = non). Six familles de cas « algorithme complexe » prouvées exprimables avec LC2 (`ScriptQualificationSpikeTest`) ;
  conditions de réouverture écrites en analyse 45 §9.
- **TODO §88 (LC5)** — **Concepteur de parcours** : une suite d'étapes (question, tiers par portail, document, notification, connecteur, DMN, expression, champs, statuts, attente, décision avec boucles)
  compilée en **BPMN déterministe** par le même parseur et la même liste blanche, déposée en **brouillon** (provenance `PARCOURS`) à relire dans Admin → Brouillons ; jamais publiée. API `/api/parcours`
  (versionnée, aperçu, mode à blanc, suppression versionnée), écran Admin → Parcours, outils MCP `list_parcours`, `get_parcours`, `define_parcours`, `compile_parcours`. Sens unique (le BPMN généré ne se relit pas
  en parcours). Deux passes de revue adversariale (valeurs de branche, mots réservés EL, caractères XML, `true/false` cités, clés, durées, variables du moteur).
- **TODO §87 (D3c + D3d)** — Deux connecteurs d'entrée par interrogation. **E-mail entrant** (`imap-inbound`) : un message crée un dossier ou alimente celui dont la référence figure dans l'objet,
  corps et expéditeur mappés vers les champs et variables, **pièces jointes rattachées après les contrôles habituels** (types, contenu, antivirus) ; réponses automatiques et listes refusées,
  messages rangés (jamais supprimés), mot de passe ou OAuth2 (XOAUTH2, Exchange Online). **Azure Service Bus** (`azure-service-bus`) : file ou abonnement par l'API REST (SAS ou OAuth2), mapping
  JSON identique à Kafka, message illisible rendu au courtier (lettres mortes) — pour les Business Events de D365 F&O sans relais. Bornes `APP_INBOUND_*` ; `DEPLOIEMENT.md` §16.
- **TODO §86 (LC3)** — Assistant de conception IA (*Admin → Assistant*) : l'utilisateur décrit son besoin, l'assistant appelle le catalogue d'outils de **conception** dans le processus avec
  l'identité de l'utilisateur (processus et tables DMN **en brouillon** ; types, schémas de questions, connecteurs sans secret et modèles de document **proposés**, validés à blanc, que l'utilisateur relit puis applique avec ses droits), vérifie son travail et renvoie ce qui est à relire. Aucune
  donnée de dossier, jamais de publication ni de secret ; plafonds d'étapes, de tours et de jetons ; conversations conservées 30 jours, propres à leur auteur. **Désactivé par défaut** ; clé et
  modèle par configuration serveur (`APP_ASSISTANT_*`, `DEPLOIEMENT.md` §15). Migration 033.

- **TODO §85 (LC4a)** — Concepteur visuel de formulaires : aperçu en direct, sections, largeur (pleine / demi), aide contextuelle en texte brut et
  réordonnancement par glisser-déposer, pour les questions de tâche et les champs d'un type de dossier ; le portail externe applique la même disposition.
  `fieldDefinitions` reste la seule source (deux champs optionnels `helpText`, `width`, sans migration) ; outils MCP `define_dossier_type` et
  `define_task_question_schema` étendus.
- **TODO §84 (LC4b)** — Éditeur visuel de templates de documents (TipTap) : mise en forme, variables, blocs conditionnels et répétés, lignes de tableau répétées,
  saut de page ; un template qu'il ne sait pas réécrire sans perte reste en source. Outils MCP `define_document_template` (brouillon seulement) et
  `preview_document` (HTML en direct). Interpolation non échappée et fragments Thymeleaf refusés sous toute graphie (correctif de sécurité issu de la revue).

- **TODO §83 (D4)** — Promotion de configuration entre environnements : export d'un **bundle ZIP** versionné (types de dossier, schémas de questions, connecteurs sans secrets, tables DMN
  et processus BPMN déployés, configurations de workflow), **plan à blanc** par artefact (créer, modifier, inchangé, conflit, bloqué), application **idempotente** par les cas d'usage existants,
  journal des imports. BPMN et DMN arrivent en **brouillons à publier** ; un connecteur est créé **désactivé** ; secrets jamais exportés (saisis ou injectés à l'import) ; valeurs `{{paramètre}}`
  substituées par environnement. Écran *Admin → Promotion*, API `/api/admin/configuration`, outils MCP `export_configuration` et `import_configuration` (plan ; l'application ne dépose que des brouillons).
  ACL, réglages et templates hors première tranche. Migration 032. Voir `DEPLOIEMENT.md` §14.

- **TODO §82 (D6)** — Socle MCP low-code : les outils MCP `deploy_workflow`, `deploy_decision_table` et `redeploy_decision_table` écrivent désormais un **brouillon**
  (origine Agent ou Assistant) qu'un humain relit et publie depuis *Admin → Brouillons* (écart avec la version en ligne, publication de ce qui a été relu, journal des
  publications) ; nouveaux outils `list_pending_drafts` et `publish_draft` (ce dernier refusé sauf publication directe autorisée hors production). Un brouillon d'un autre
  auteur n'est jamais écrasé en silence. Chaque outil MCP est classé *conception* ou *opération* ; un outil peut s'exécuter avec l'acteur d'une session (assistant).
  **Ruptures** : les agents ne déploient plus en ligne par défaut (`APP_MCP_PUBLICATION_MODE=direct`, hors production, le rétablit) ; `APP_ENVIRONMENT` (défaut `production`) ;
  hors `local`, `M2M_TRUSTED_CLIENT_IDS` est obligatoire dès que le canal M2M est actif (risque A3-M2). Migration 031. Voir `DEPLOIEMENT.md` §13.

- **TODO §81 (D2)** — Recherche de dossiers sur des champs dynamiques : un champ peut être déclaré « cherchable » (texte, nombre, date, oui/non, choix ; jamais sensible),
  puis filtré par égalité, liste, intervalle ou préfixe depuis la liste de supervision, l'API (`f.<champ>…`) et l'outil MCP `search_dossiers`. Type de dossier obligatoire,
  cinq filtres au plus, refus explicite d'un filtre invalide. Migration 030 : `fields` passe en `jsonb` avec un index GIN (PostgreSQL ; réécriture de la table, voir `DEPLOIEMENT.md` §12).

- **TODO §80 (D1 règles)** — Champs calculés (formule), visibilité conditionnelle et obligation conditionnelle sur les types de dossier : expressions métier bornées (JSONata)
  évaluées côté serveur (un champ calculé n'est jamais saisi, un champ masqué n'est ni obligatoire ni conservé), aperçu en direct dans la SPA, endpoint `evaluate-rules` et outil MCP
  `evaluate_dossier_rules`, éditeur d'administration. Règles refusées dans les questions de tâche. Aucune migration.

- **TODO §79 (D5)** — Tâches planifiées sûres si deux pods les exécutent (déploiement progressif, répliques) : audit des 18 tâches, puis prise de l'unité de travail en base avant
  l'effet (relais d'outbox, alertes SLA, invitations du portail, déclencheurs cron, export BI, réconciliation des liens, distribution des signatures). Plus d'e-mails, de dossiers, de fichiers
  ou d'instances de process en double ; sans Redis ni verrou externe. Trois colonnes de bail (migrations 027 à 029). Guide d'exploitation : `DEPLOIEMENT.md` §11.

- **TODO §78 (D3b)** — Nouvelle brique BPMN « Enregistrer des valeurs dans le dossier » : des expressions métier bornées (JSONata) tirent des valeurs du dossier, du résultat d'un
  connecteur (`connectorResult.json` : corps lu comme JSON) et des variables, et les écrivent **en un seul patch** dans des champs déclarés du dossier (validation de type, version,
  tout ou rien ; écriture de champ sensible auditée). Éditeur : paramètres multilignes en zone de texte. `connectorResult` devient une variable réservée.

- **TODO §77 (D3a)** — Connecteur `http-rest` : authentification OAuth2 `client_credentials` (Microsoft Entra ID : D365 F&O, Dataverse, Graph). Jeton obtenu,
  mis en cache par pod et renouvelé automatiquement ; secret client ou certificat (`private_key_jwt`) chiffrés au repos ; liste d'hôtes autorisés obligatoire pour que
  le jeton ne puisse pas partir ailleurs ; un `401` provoque un renouvellement et un seul rejeu. Sans `authMode`, comportement inchangé.

- **TODO §76 (D1)** — Champs dynamiques structurés : `MONEY` (montant décimal exact + devise ISO 4217, jamais arrondi en silence),
  `REPEATABLE_GROUP` (lignes typées, un niveau, 0–500) et `EXTERNAL_REF` (système + identifiant). Validation avec chemin d'erreur par ligne,
  valeurs normalisées, export CSV en colonnes stables, saisie et lecture dans la SPA, éditeur d'administration. Aucune migration ; types
  structurés non encore proposés dans les questions de tâche.

- **TODO §75 (LC2)** — Langage d'expression métier borné (JSONata, hors JUEL) : brique « Évaluer une expression » qui calcule
  une condition ou une valeur sur le dossier (champs non sensibles), le résultat d'un connecteur et les variables, et la range dans
  une variable comparée par une passerelle. Essai à blanc par `POST /api/expressions/{validate,evaluate}` et outils MCP
  `validate_expression` / `evaluate_expression`. Bornes `app.expression.*` ; regex, `$eval` et fonctions non déterministes refusés.

- **TODO §74 (LC1-suite)** — Le panneau de la brique « Décision DMN » propose désormais la liste des tables de décision
  déployées (clé et dernière version) au lieu d'une saisie libre. Servie par
  `GET /api/bpmn-building-blocks/references/dmnTables` (`TENANT_ADMIN` ou `WORKFLOW_ADMIN`). Aucune migration.

- **[PR #151](https://github.com/superjojo79/FlowForge/pull/151) — TODO §73 (LC1)** — Palette métier dans l'éditeur BPMN : onze briques (document, connecteur,
  notification, dossier enfant, statuts, DMN, signature, question, tâche externe) posables depuis la
  palette, champs typés alimentés par l'API dans le panneau, avertissements non bloquants. Le
  catalogue est servi par `GET /api/bpmn-building-blocks` et l'outil MCP `list_bpmn_building_blocks` ;
  six delegates lisent désormais aussi des attributs `flowable:<nom>` littéraux (renseigné, l'attribut
  prime sur une variable de même nom ; sans attribut, comportement inchangé), validés au déploiement (forme puis existence des ressources, `dryRun` MCP compris).
  L'éditeur charge enfin les feuilles de style `bpmn-js`. Aucune migration, aucun changement pour un
  process existant.
- **[PR #149](https://github.com/superjojo79/FlowForge/pull/149) — TODO §72** — Signature électronique générique : un bloc BPMN `${signatureRequestTask}` demande la
  signature d'un document du dossier à des signataires externes (lien par e-mail, sans compte) ou
  internes (« Mes signatures »), en mode parallèle ou séquentiel, requis ou facultatifs ; le process
  reprend sur le message `signatureCompleted` avec `signatureOutcome`. Panneau « Signatures » dans le
  détail du dossier (annuler, renvoyer), trois outils MCP de lecture/annulation, connecteur `signature`
  pour un futur fournisseur. Seul fournisseur livré : `stub` (simulation sans valeur juridique).
  Désactivé par défaut (`APP_SIGNATURE_PROVIDER`).
- **PR [#138](https://github.com/superjojo79/FlowForge/pull/138) — TODO §68** — Portail externe : un tiers sans compte
  répond à une tâche BPMN qui lui est destinée (formulaire + pièces) via un lien personnel reçu
  par e-mail, valable 7 jours, révocable et renvoyable par l'instructeur depuis le détail du
  dossier. Désactivé par défaut (`APP_PORTAL_ENABLED`, `APP_PORTAL_PUBLIC_BASE_URL`) ; la purge
  de dossier supprime désormais aussi les réponses aux questions de tâche.
- **PR [#134](https://github.com/superjojo79/FlowForge/pull/134)** — Outillage DMN (TODO §67) :
  évaluation d'une table déployée, jeux de tests JSON et CSV (100 cas maximum), historique des
  versions et redéploiement (« Redéployer la version N », avec confirmation dans l'éditeur).
  Disponible dans l'éditeur `/admin/dmn-editor`, l'API REST et quatre outils MCP.
  Le redéploiement refuse un XML dont l'identifiant ne correspond pas à la clé demandée,
  un jeu CSV de plus de 100 cas est rejeté dès la lecture, et le payload MCP de
  `test_decision` est borné par la taille maximale d'upload.
- **PR [#135](https://github.com/superjojo79/FlowForge/pull/135)** — Inbox de notifications
  persistée : la cloche conserve l'historique par utilisateur (lu/non-lu, badge calculé côté
  serveur, « Charger plus ») et rattrape les notifications reçues hors connexion ou pendant une
  reconnexion SSE. Purge automatique par rétention, nouveaux producteurs (réassignation, tâche
  complétée, document généré).
- **PR [#132](https://github.com/superjojo79/FlowForge/pull/132)** — Alertes de franchissement SLA
  (e-mail, webhook, navigateur, une fois par palier), compteurs dead-letter et tâches ouvertes
  sans assigné sur `/rapports`, et export BI planifié (CSV par type et CSV SLA, rétention,
  masquage des champs sensibles). L'horloge de scan n'écrase plus les destinataires, un PUT
  partiel conserve la configuration, et le CSV BI omet aussi les colonnes sensibles.
  Réglages opt-in dans Admin → Types. Guide utilisateur mis à jour.
- **TODO §62** — Deux nouveaux connecteurs (lot B1) : `batch-import` (import périodique de
  fichiers CSV/Excel via SFTP, un dossier créé par ligne) et `kafka` (déclenchement par message
  reçu sur un topic). Nouveau type de champ de configuration `SECRET_TEXTAREA` (clé privée SFTP
  multi-lignes) masqué en lecture et chiffré au repos comme les mots de passe. Service Kafka
  optionnel disponible en local (`docker compose --profile kafka up -d`).
- **TODO §61** — Introduction de DMN : le moteur DMN Flowable est activé et un `serviceTask
  flowable:type="dmn"` peut invoquer une table de décision (clé portée par un champ d'extension
  `decisionTableReferenceKey`) — pas le `businessRuleTask` standard, câblé sur Drools et toujours
  rejeté. Éditeur intégré `dmn-js` (`/admin/dmn-editor`, `TENANT_ADMIN`/`WORKFLOW_ADMIN`), même
  principe que l'éditeur BPMN (brouillon persisté, déploiement à chaud avec confirmation). MCP :
  `deploy_decision_table`, `list_decision_tables`, `get_decision_table`. Scénario illustratif
  livré (`grille-eligibilite-client.dmn` + process de démonstration), déployés au démarrage comme
  les process `-smoke` existants.

### Modifié

- **PR [#117](https://github.com/superjojo79/FlowForge/pull/117)** — L’écran Admin → Connecteurs
  génère un formulaire champ par champ depuis `configSchema` au lieu d’un JSON brut (lot A2).
  Les types sans config d’instance (`smtp`, `document-storage`) n’affichent plus de zone de texte.
  Après création, la liste affiche l’URL webhook (`http-rest`) et l’URL callback (`flowable-callback`)
  avec copie presse-papiers.

### Sécurité

- **PR [#138](https://github.com/superjojo79/FlowForge/pull/138) — revue adversariale du portail (TODO §68)** — Une erreur de routage
  (mauvaise méthode, URL inconnue, `Content-Type` refusé, chemin rejeté par le pare-feu HTTP) sur
  une route publique (portail, webhooks) renvoyait une redirection vers la connexion Keycloak et
  créait une session anonyme, identifiant dans l'URL : elle renvoie désormais son statut (404,
  405, 415, 400), sans session ; les sessions ne passent plus que par cookie. La limite de débit
  du portail et des rapports CSP ne pouvait plus être contournée par un en-tête `Forwarded` forgé
  (l'adresse du client ne vient d'en-têtes transférés que si le pair est le proxy de confiance) ;
  sa table est bornée même sous une rafale de clients distincts, IPv6 regroupé par /64. Une
  requête mal formée sur le portail ne produit plus d'erreur 500 journalisée. **Action de
  déploiement** : le reverse-proxy doit aussi supprimer `Forwarded` et `X-Forwarded-Prefix`
  (DEPLOIEMENT.md §3).
- **PR [#136](https://github.com/superjojo79/FlowForge/pull/136) — TODO §69** — Un BPMN déployé pouvait faire exécuter une
  méthode d'un bean Spring via le nom, le `formKey`, l'échéance d'une tâche, un timer ou un
  `callActivity` (surfaces évaluées par Flowable, hors de l'allowlist H1-bis). Le backend refuse
  désormais toute expression `${…}` sauf `delegateExpression` allowlistée et condition de flux
  simple, ainsi que les conditions écrites en script (`language`) ; prouvé sur le vrai moteur
  avant correctif.

- **PR [#131](https://github.com/superjojo79/FlowForge/pull/131)** — Scan antivirus des pièces
  jointes de dossier (lot C) : nouvel adaptateur `ClamAvAttachmentScanAdapter` (protocole natif
  `INSTREAM` clamd en TCP, zéro dépendance tierce) derrière le port `AttachmentScanPort` existant
  (non modifié), sélectionné par `app.dossier.attachment.scan.type` (`noop` par défaut, `clamav`
  opt-in). Fail-open si clamd indisponible/timeout (décision produit assumée), watchdog d'E/S
  bornant l'écriture réseau (pas seulement la lecture) suite à une revue adversariale post-Phase 9
  ayant trouvé un DoS par épuisement de threads. Service `clamav` optionnel dans
  `docker-compose.yml` (profil `clamav`, local/démo).

- **PR [#130](https://github.com/superjojo79/FlowForge/pull/130)** — Durcissement post-merge du
  lot A4 (revue adversariale de la PR #128) : périmètre de confiance HSTS restreint à une
  allowlist de CIDR configurable, démarrage refusé si `APP_SECURITY_HEADERS_ENABLED=false` hors
  local/test, et endpoint anonyme rate-limité de collecte des rapports CSP
  (`POST /api/security/csp-reports`).

- **PR [#128](https://github.com/superjojo79/FlowForge/pull/128)** — En-têtes HTTP applicatifs
  (CSP enforce SPA, HSTS 1 an hors local/test si pair TCP loopback/RFC1918,
  `X-Frame-Options`, `nosniff`, `Referrer-Policy`) sur les 4 chaînes Spring Security.
  Kill switch `APP_SECURITY_HEADERS_ENABLED` (défaut `true`). Bind Docker `127.0.0.1`
  ; le reverse-proxy doit écraser les `X-Forwarded-*` clients.

- **TODO §61** — Flowable n'implémente pas FEEL pour DMN malgré le standard : les cellules de
  table de décision sont évaluées en JUEL, exposant la même classe de vulnérabilité que H1-bis
  (TODO §57/§58, résolution d'un identifiant non qualifié contre un bean Spring). Vérifié par
  décompilation du moteur réel et prouvé par exécution (`DmnJuelSecuritySmokeIT`), pas seulement
  par lecture statique — `FlowableDmnParserAdapter` applique une allowlist stricte sur chaque
  cellule au déploiement.
- **TODO §61 (revue adversariale)** — l'allowlist ci-dessus ne validait pas
  `<input><inputExpression>`, une surface JUEL identique aux cellules de règle : un subagent
  indépendant a prouvé sur le vrai moteur qu'une table par ailleurs allowlist-safe pouvait
  invoquer une méthode arbitraire sur un bean Spring vivant via l'inputExpression
  (`connectorServiceTask.getClass().getName()`). Corrigé : l'inputExpression est désormais
  restreinte à un identifiant nu strict, même discipline que les identifiants de
  `conditionExpression` BPMN (TODO §57 H1).

- **PR [#113](https://github.com/superjojo79/FlowForge/pull/113)** — Revue adversariale TODO §57 :
  allowlist BPMN, complete/runtime schemas bornés à l’assignee, OIDC sans `API_SERVICE`,
  HMAC `timestamp.Idempotency-Key.body` (rupture de contrat), SSRF Host/CGNAT, caps HTTP/storage,
  ACL generate PDF, CSP preview. Les partenaires HMAC doivent inclure `Idempotency-Key` dans la signature.

### Documentation

- **PR [#145](https://github.com/superjojo79/FlowForge/pull/145)** — Ordonnancement de la Vague D :
  lots parallélisables, contraintes d'ordre et quatre vagues dans `FILE-ATTENTE.md` et `ROADMAP.md` ;
  `README.md` (statut, positionnement) et `DEPLOIEMENT.md` (provisioning livré) alignés.
- **PR [#144](https://github.com/superjojo79/FlowForge/pull/144)** — Positionnement tranché (TODO §70) :
  produit pour intégrateurs avec une couche low-code, découpée en six sous-lots. Exposition MCP de
  cette couche cadrée (TODO §71, quatre décisions actées) et analyses initiales de tous les lots de
  la Vague D (`analysis doc/` 31 à 46). Aucun changement de code.
- **PR [#143](https://github.com/superjojo79/FlowForge/pull/143)** — `ROADMAP.md` enrichie d'une
  Vague D issue d'une analyse critique fonctionnelle : décision de positionnement à trancher (D0)
  et sept lots non cadrés (champs enrichis, recherche, intégrations entreprise, promotion de
  configuration, jobs multi-pods, garde-fous MCP, i18n), chacun recoupé sur le code.
- **PR [#138](https://github.com/superjojo79/FlowForge/pull/138)** — Rattrapage documentaire après les merges #131/#132/#135
  (TODO, file d'attente, roadmap v1.2), scission du lot « portail externe & signature » en deux
  lots distincts et cadrage du portail externe tokenisé (requirements, revue, design). Pas de
  code applicatif.
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

- **PR [#118](https://github.com/superjojo79/FlowForge/pull/118)** — Le serveur MCP interne
  peut désormais opérer un flux métier (TODO §60) : 4 tools d’écriture
  (`create_dossier`, `add_dossier_element`, `start_workflow`, `generate_document`),
  16 → 20 tools. Pas d’upload binaire ni de PDF Base64 dans le canal MCP.
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
