# FlowForge → Myrmacta

Myrmacta (myrmex + acta) est le nom commercial. FlowForge reste le nom de code du dépôt, des
paquets et de l'artefact tant que la PR technique n'est pas mergée. Le dépôt GitHub
`superjojo79/FlowForge` n'est pas renommé.

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

## Laissé exprès

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
