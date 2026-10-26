# §105 — Nom commercial Myrmacta

> **Statut :** clos · point de premier niveau · [registre](README.md)

> **Actualisation (2026-10-25).** Les identifiants techniques que cette décision laissait au nom
> de code (artifactId, jar, serveur MCP, realm et clients Keycloak, `targetNamespace` BPMN/DMN,
> en-tête de signature, dossiers et drapeau de courrier, clé de langue, job Prometheus,
> utilisateur et tag d'image) ont été renommés par la PR identifiants. Le dépôt GitHub, les
> permaliens, les changeSets Liquibase et les noms de beans Flowable ne l'ont pas été. L'énoncé
> daté n'est pas réécrit. Compatibilité et reliquats : [RENAMING.md](../RENAMING.md).

**Énoncé.** Le logiciel s'affichait sous son nom de code FlowForge (interface, documentation
vivante, contrat OpenAPI, image Docker). Le nom commercial retenu est **Myrmacta** (myrmex + acta) :
l'intelligence collective au service des dossiers et des décisions. Le dépôt GitHub n'est pas
renommé. Les identifiants techniques restent le nom de code jusqu'à une seconde PR.

**Décision (2026-10-25).**

- **D-BRAND-NAME** — le nom affiché est Myrmacta. Cela couvre les chaînes d'interface et i18n
  (français et anglais), les titres de page, les logos et favicons, la prose vivante (README,
  guides, specs), les textes juridiques qui portent le nom, le titre et la description OpenAPI,
  `spring.application.name` (bannière et journaux) et les labels OCI de l'image. Le logo A3b est
  une fourmi de profil dessinée avec des formes BPMN. Fonds clairs : encre `#1E2A3A`, corail
  `#E4572E`. Fonds sombres : encre `#F3F4F6`, corail `#FF7A52`. Le logo sombre est celui des fonds
  sombres (barre latérale). Signature française : « L'intelligence collective au service de vos
  dossiers et décisions ».
- **D-BRAND-CODE-NAME** — packages `com.flowforge`, `groupId`, `artifactId`, factories Spring,
  schémas, cookies, variables d'environnement, realms et clients Keycloak, ressources BPMN/DMN
  déployées, changelogs Liquibase et permaliens `superjojo79/FlowForge` ne changent pas ici.
  Proposition pour la PR technique : racine `be.myrmacta`, `groupId` `be.myrmacta`. Le détail des
  paquets est un tableau vide dans [RENAMING.md](../RENAMING.md), à remplir par cette PR.
- L'état d'avant renommage est le tag annoté `pre-myrmacta`
  (`54bac40b24c0bdf4f79234e1d580a143af936326`). Les documents archivés restent au commit `e91aa7b`.
  Le registre des décisions et les revues datées ne sont pas réécrits : ils décrivent le passé.

**Écarté.** Remplacement aveugle de `flowforge` (casse indifférente). Renommer le dépôt ou les
permaliens. Modifier une ressource BPMN/DMN déjà déployée, un changeSet Liquibase, ou un nom de
bean Flowable. Changer l'en-tête `X-FlowForge-Signature`, la catégorie Outlook
`FlowForge-delivered`, les dossiers `FlowForge-processed` / `FlowForge-rejected`, le drapeau
IMAP `FlowForgeDelivered`, la clé `flowforge.locale`, le `job_name` Prometheus `flowforge` ou
le nom du serveur MCP `flowforge` : ce sont des identifiants de protocole ou de stockage, pas
des libellés.

**Impacte.** [TRADEMARKS.md](../../TRADEMARKS.md), [RENAMING.md](../RENAMING.md),
[docs/specs/00-index.md](../specs/00-index.md), [docs/specs/11-frontend.md](../specs/11-frontend.md),
README, guides, interface Vue, `application.yml`, contrat `openapi/openapi.yaml`, `Dockerfile`.
