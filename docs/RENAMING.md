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

## Ce que la PR technique changera

- Paquet Java `com.flowforge` → `be.myrmacta` (proposition).
- `groupId` Maven → `be.myrmacta` (proposition). L'`artifactId` `flowforge` n'est pas tranché ici.
- `META-INF/spring.factories`, `META-INF/services`, et les citations de chemins
  `src/main/java/com/flowforge/...` encore présentes dans la documentation.

| Ancien paquet | Nouveau paquet |
|---|---|
| _à remplir par la PR 2_ | _à remplir par la PR 2_ |

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
