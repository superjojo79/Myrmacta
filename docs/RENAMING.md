# FlowForge → Myrmacta

Myrmacta (myrmex + acta) est le nom commercial. Le dépôt GitHub `superjojo79/FlowForge`
n'est pas renommé : FlowForge en reste le nom de code. Les paquets Java sont `be.myrmacta`
et l'`artifactId` est `myrmacta` (section « Identifiants »). Les sections qui suivent
décrivent ce que chaque PR a fait, dans l'ordre ; l'état courant est la section
« Identifiants ».

Signature : « L'intelligence collective au service de vos dossiers et décisions ».

Logo A3b : fourmi de profil dessinée avec des formes BPMN.

| Fond | Encre | Corail |
|---|---|---|
| Clair | `#1E2A3A` | `#E4572E` |
| Sombre | `#F3F4F6` | `#FF7A52` |

Le logo sombre (`myrmacta-logo-dark`) s'emploie sur fond sombre. Le logo clair
(`myrmacta-logo-light`) s'emploie sur fond clair. Fichiers : `docs/brand/` et
`frontend/src/assets/brand/`. Favicon (tête corail, deux antennes, pas la fourmi entière) :
`frontend/public/` et `docs/brand/`. La fourmi entière reste le logo et l'icône de marque.

## Repère d'avant renommage

Tag annoté `pre-myrmacta` sur le commit
`54bac40b24c0bdf4f79234e1d580a143af936326` (HEAD de `main` au départ de la branche),
message « Dernier état avant renommage Myrmacta ». C'est la référence pour un lien vers
l'arbre d'avant le renommage visible. Ne pas le déplacer.

Les documents retirés de `main` (archive documentaire) restent au commit `e91aa7b`.
Les permaliens déjà figés sur `e91aa7b`, `555a692` et `dcb7e49`, ainsi que les liens
`/pull/`, continuent de porter `superjojo79/FlowForge`. On ne les réécrit pas.

## Ce que cette PR (visible) a changé

- Chaînes d'interface et catalogues i18n français / anglais, titres de page, manifeste web.
- Logos, favicon, couleurs de marque. Barre latérale sombre : logo sombre.
- Prose vivante : README, guides, specs, objectifs, exemples Markdown, consignes agents.
- Tableau de dénomination ([TRADEMARKS.md](../TRADEMARKS.md)) et les phrases des textes de
  licence qui disaient qu'aucun nom commercial n'était choisi.
- Titre et description OpenAPI, `<name>` et `<description>` Maven (pas le `groupId` ni
  l'`artifactId`), `spring.application.name`, labels OCI de l'image, libellés Keycloak
  (le realm et les `clientId` ne bougent pas), invite système de l'assistant de conception.
- Nom du ZIP de promotion téléchargé : `myrmacta-config….zip`. L'import lit le contenu, pas le nom.

## Ce que cette PR (paquets) a changé

- Paquet Java `com.flowforge` → `be.myrmacta`, récursif, sur les quatre racines de sources
  (`src/main/java`, `src/test/java`, `src/embedded/java`, `src/embedded-test/java`). Déplacement
  par `git mv`, réécriture par OpenRewrite `org.openrewrite.java.ChangePackage`.
- `groupId` Maven : `be.myrmacta`. L'`artifactId` reste `flowforge` : le jar s'appelle toujours
  `flowforge-*.jar` (Dockerfile, `scripts/check-*-in-jar.sh`, guides). Les coordonnées sont
  donc `be.myrmacta:flowforge`.
- `META-INF/spring.factories` (main et test), `META-INF/services/org.hibernate.boot.model.FunctionContributor`,
  et le fichier SPI renommé
  `META-INF/services/be.myrmacta.infrastructure.embeddeddb.EmbeddedDbLauncher`.
- Citations de chemins `src/{main,test}/java/com/flowforge/...` devenues fausses dans la
  documentation vivante (spec 45 lien relatif, specs 18 et 24, guide BPMN, spec 01, spec 12,
  décisions 032 et 048, revue d'alignement, deux fiches d'analyse). Les énoncés historiques
  qui décrivent l'ancien paquet (décision 046, décision 105, lignes de revue qui racontent
  le passage `com.monapp` → `com.flowforge`) ne sont pas réécrits.
- Classe d'entrée inchangée : `be.myrmacta.FlowForgeApplication`. Noms de beans Flowable
  `@Component` inchangés.

| Ancien | Nouveau |
|---|---|
| `com.flowforge` (paquet, récursif) | `be.myrmacta` |
| `src/main/java/com/flowforge` | `src/main/java/be/myrmacta` |
| `src/test/java/com/flowforge` | `src/test/java/be/myrmacta` |
| `src/embedded/java/com/flowforge` | `src/embedded/java/be/myrmacta` |
| `src/embedded-test/java/com/flowforge` | `src/embedded-test/java/be/myrmacta` |
| `groupId` `com.flowforge` | `groupId` `be.myrmacta` |
| `artifactId` `flowforge` | `flowforge` (inchangé) |
| `META-INF/services/com.flowforge.infrastructure.embeddeddb.EmbeddedDbLauncher` | `META-INF/services/be.myrmacta.infrastructure.embeddeddb.EmbeddedDbLauncher` |

### Clés `flowforge.*`

Aucune propriété Spring `flowforge.*` : les `@ConfigurationProperties` sont sous `app.*`
(`app.tenant`, `app.workflow`, `app.connector`, etc.). Rien à migrer dans `application*.yml`.

Candidates laissées pour une PR ultérieure (identifiants opérationnels, pas des clés
`flowforge.*`, et hors périmètre de cette PR) : `artifactId` et nom de jar, nom MCP
`spring.ai.mcp.server.name`, clé navigateur `flowforge.locale`, `job_name` Prometheus,
utilisateur et tag d'image, noms de base et d'identifiants par défaut, `targetNamespace`
BPMN `http://flowforge/...`, auteur Liquibase `flowforge`. La liste tenue de ce qui reste
exprès est la section suivante.

## Laissé exprès par la PR paquets

La liste qui suit décrit l'état **après la PR paquets** (#227). La section « Identifiants »
plus bas dit ce que la PR suivante en a fait.

- Permaliens `github.com/superjojo79/FlowForge`, y compris les SHA `e91aa7b`, `555a692`,
  `dcb7e49`, et les liens `/pull/`.
- Registre `docs/decisions/` (sauf la décision n° 105) et revues `docs/analyses/` : ce sont
  des faits passés. `CHANGELOG.md` aussi.
- Ressources BPMN/DMN, dont le commentaire de
  `src/main/resources/decisions/grille-eligibilite-client.dmn`, et le nom du fichier
  `docs/specs/FlowForge - Maquettes UI.html`.
- Changements Liquibase, schémas, cookies (`JSESSIONID`, `XSRF-TOKEN`),
  `app.portal.public-base-url`, noms de variables d'environnement, identifiants Keycloak
  (`flowforge`, `mon-app`, `flowforge-m2m-…`), clés de stockage.
- Nom du serveur MCP `flowforge`, en-tête `X-FlowForge-Signature`, drapeau IMAP
  `FlowForgeDelivered`, catégorie Outlook `FlowForge-delivered`, dossiers
  `FlowForge-processed` / `FlowForge-rejected` (guides compris : ce sont les défauts
  écrits par le code), clé de stockage navigateur `flowforge.locale`,
  `job_name` Prometheus `flowforge`, utilisateur et chemins d'image `flowforge`
  (`/usr/share/doc/flowforge`, tag `flowforge:demo`).
- Jeux de diapositives : aucun n'est modifié.
- Pas de remplacement insensible à la casse de `flowforge`.

## Identifiants

La PR identifiants reprend ce que la PR paquets avait laissé. La recette rejouable est
[`scripts/rename-flowforge-identifiers.py`](../scripts/rename-flowforge-identifiers.py)
(`--dry-run`, `--apply`, `--check`). Les lignes de compatibilité portent le marqueur
`flowforge-legacy` : le script ne les réécrit pas.

Commits séparés : plan, `git mv` des fichiers, remplacement mécanique du contenu,
puis compatibilité. Aucun changeSet Liquibase n'est modifié. Liquibase reste 4.33.0.

| Catégorie | Devenu |
|---|---|
| `artifactId`, jar `myrmacta-*.jar`, scripts `check-*-in-jar.sh` | `myrmacta` |
| Utilisateur d'image, `/usr/share/doc/myrmacta`, tag `myrmacta:demo` | `myrmacta` |
| `FlowForgeApplication` | `MyrmactaApplication` |
| Serveur MCP, préfixe de métriques, `job_name` Prometheus | `myrmacta` |
| Défauts locaux (base, rôle, bucket, MinIO, realm et clients Keycloak) | `myrmacta` |
| `targetNamespace` / `namespace` `http://flowforge/...` | `http://myrmacta/...` |
| En-tête de webhook sortant | `X-Myrmacta-Signature` (l'ancien est encore émis) |
| Dossiers, drapeau IMAP, catégorie Outlook | nouveaux noms en écriture, anciens reconnus en lecture |
| Clé navigateur | `myrmacta.locale` (l'ancienne est migrée) |
| Skill | `myrmacta-conventions` |
| Maquette | `docs/specs/Myrmacta - Maquettes UI.html` |
| Export realm | `docker/keycloak/import/myrmacta-realm.json` |

Aucun `id` BPMN ou DMN du dépôt ne contenait `flowforge`. La clé de process vient de
`process/@id` (`FlowableBpmnParserAdapter`), la clé de décision de `decision/@id`
(`FlowableDmnParserAdapter`), pas du namespace. Les noms de beans Flowable ne changent pas.
Il n'y a pas de tableau de bord Grafana dans le dépôt : seul le `job_name` Prometheus
`myrmacta` est versionné. Les règles de scrape externes qui filtraient `flowforge` ou
`flowforge_*` doivent être mises à jour.

### Compatibilité

- **Webhook.** `WebhookRestAdapter` envoie `X-Myrmacta-Signature` et `X-FlowForge-Signature`
  avec la même valeur `sha256=…`. `acceptsSignatureHeader` reconnaît les deux. L'en-tête
  entrant des connecteurs (`X-Signature-256`) n'a pas changé.
- **Courrier.** Écriture : dossiers `Myrmacta-processed` / `Myrmacta-rejected`, drapeau
  `MyrmactaDelivered`, catégorie `Myrmacta-delivered`. Lecture : `FlowForgeDelivered` et
  `FlowForge-delivered` comptent comme déjà remis. Une configuration de connecteur déjà
  enregistrée (noms de dossiers) est une donnée : elle n'est pas migrée.
- **Langue.** `flowforge.locale` est lue une fois, recopiée dans `myrmacta.locale`, puis retirée.
- **Variables.** `FLOWFORGE_TEST_PG_URL`, `_USER`, `_PASSWORD`, `FLOWFORGE_IMAGE`,
  `FLOWFORGE_BASE_URL` et `FLOWFORGE_PORTAL_JWKS_URL` sont lues si la variable `MYRMACTA_*`
  correspondante est absente. `TEST_PG_*` reste accepté pour les tests.
- **Base embarquée.** Si `~/.myrmacta/embedded-db` n'existe pas et que
  `~/.flowforge/embedded-db` existe, ce répertoire et la base `flowforge` qu'il contient
  restent utilisés. Un répertoire explicite (`app.embedded-db.data-dir` ou
  `APP_EMBEDDED_DB_DATA_DIR`) garde le nom de base `myrmacta`, même s'il pointe vers
  l'ancien chemin. Le rôle applicatif créé ensuite est `myrmacta_tenant_dev`.
- **Mot de passe.** Hors profils `local` et `test`, `myrmacta` et `flowforge` sont refusés.
- **Volume Docker déjà créé.** `POSTGRES_DB` / le rôle ne sont pas renommés par un
  redémarrage. Fixer `DB_NAME=flowforge`, `POSTGRES_USER=flowforge`,
  `DB_USERNAME=flowforge_tenant_dev`, `DB_PASSWORD=flowforge` et l'URL JDBC vers la base
  `flowforge`. Keycloak n'a pas réimporté un realm déjà présent : laisser
  `OIDC_ISSUER_URI` sur `realms/flowforge`, ou supprimer le volume `keycloak_data` pour
  importer `myrmacta`.
- **Tenant déjà provisionné.** `scripts/provision-tenant.sh` crée désormais le rôle
  `myrmacta_<tenant>` et le realm `myrmacta-<tenant>`. Ne pas le relancer sur un tenant
  existant pour « renommer » : le rôle et le realm d'origine restent la référence.
  `MYRMACTA_IMAGE` remplace `FLOWFORGE_IMAGE` (l'ancien nom est encore lu).

### Reliquats volontaires

`python3 scripts/rename-flowforge-identifiers.py --check` échoue si un fichier vivant
contient `flowforge` hors de cette liste.

- URL `github.com/superjojo79/FlowForge`, y compris les permaliens `e91aa7b`, `555a692`,
  `dcb7e49` et les liens `/pull/`. Le dépôt n'est pas renommé : le faire ou non reste à
  décider par Benoit.
- Tag annoté `pre-myrmacta`.
- `TRADEMARKS.md` : le nom de code du dépôt, et `flowforge.be` (société tierce, pas nous).
- `CHANGELOG.md`.
- `docs/decisions/` et `docs/analyses/` : historique daté. Notes d'actualisation seulement
  sur les décisions 046, 104 et 105, et sur les phrases qui étaient aux lignes 27 et 89 de
  `docs/analyses/REVUE-ALIGNEMENT-SECURITE.md`. L'index `identifiants.md` suit le code pour
  `D-BRAND-NAME` et `D-BEAUTH-REALM`.
- Auteur `flowforge` des changeSets Liquibase existants (`src/main/resources/db/changelog/`).
  Aucun changeSet n'a été édité. Les prochains changeSets utilisent l'auteur `myrmacta`.
  La référence `src/test/resources/db/changelog/checksums-baseline.csv` garde cet auteur :
  c'est la clé du checksum, pas un identifiant à renommer. Le script de renommage l'ignore.
- Commentaire de `src/main/resources/decisions/grille-eligibilite-client.dmn` (il ne contient
  pas le mot). Le `namespace` du fichier est `http://myrmacta/...`.
- Lignes marquées `flowforge-legacy` (en-tête, drapeau, catégorie, clé de langue, variables,
  mot de passe refusé, répertoire et base embarqués).
- Slug `docs/decisions/039-construction-de-diagrammes-bpmn-dans-flowforge-ecart.md` : identité
  du point, pas renuméroté. `rg` ne le voit que si le contenu contient le mot.
- Aucun jeu de diapositives dans l'arbre.
- Ce fichier.

### Rebase d'une branche parallèle

Au départ de cette PR, `git fetch origin` puis `git branch -r --sort=-committerdate` :
aucune pull request ouverte, et aucune branche distante n'avait `1dac9ab` (squash de la PR
#227) pour ancêtre avec des commits absents de `main` (`b38f666e`). Les branches non
contenues dans `main` ont un merge-base antérieur à ce squash (restes de PR déjà
squash-mergées, ou travail plus ancien). `origin/ccr-1b2a2fd0-r71qj0` (issue #211) était
alors contenue dans `main` (jusqu'à la PR #231). On n'y a pas touché.

Rebase final sur `origin/main` `ffcd6b5c` (PR #233, lot A de la pré-vérification, même
ligne `ccr-1b2a2fd0-r71qj0`). Un conflit : `AnthropicChatModels.java`. Le lot A passe le
User-Agent en paramètre ; la valeur de l'assistant reste `myrmacta-assistant`, le reste du
lot est conservé. Après ce rebase, cette branche distante n'a plus de commit absent de
`main`. Un lot suivant, non poussé, reste le point de conflit probable.

Un lot poussé ensuite depuis ce tip, ou toute branche rebasée sur `main` après cette PR,
rencontre des conflits là où elle édite les mêmes fichiers. Zones les plus exposées :
`application.yml`, `docker-compose.yml`, `Dockerfile`, `pom.xml`, les connecteurs IMAP et
Graph, `frontend/src/i18n/`, les guides, les ressources BPMN/DMN, le skill de conventions.

Procédure, sur **la branche concernée** (pas depuis ici) :

1. `git fetch origin && git rebase origin/main`
2. Pour un fichier déplacé (`FlowForgeApplication`, `flowforge-realm.json`, la maquette,
   `flowforge-conventions`) : garder le nouveau chemin, reporter le changement métier dessus.
3. Pour un conflit de contenu : garder le travail de `main` sur les identifiants, puis
   réappliquer le changement métier.
4. `python3 scripts/rename-flowforge-identifiers.py --apply` réécrit les `flowforge` encore
   vivants introduits par la branche. Il ne touche pas aux URL du dépôt, aux changeSets
   Liquibase, au registre, ni aux lignes `flowforge-legacy`.
5. `python3 scripts/rename-flowforge-identifiers.py --check` doit rester vert.
6. Ne pas modifier un changeSet Liquibase existant. Un changeSet nouveau a l'auteur `myrmacta`.
