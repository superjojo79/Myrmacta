# §142 — Catalogue des connecteurs : libellés et aides traduits (FR/EN)

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (issue [#106](https://github.com/superjojo79/Myrmacta/issues/106)). Mandat cloud agent 2026-11-15.
Comportement : [spec 06](../specs/06-connecteurs.md) § « Implémentation — catalogue i18n ».

**Constat.** Les libellés et aides du catalogue sont écrits en dur dans les connecteurs Java. L'écran
Admin → Connecteurs mélange anglais et français selon le type, quelle que soit la langue de l'UI.

**D-CONNECTOR-CATALOG-I18N** — l'API et l'outil MCP rendent des **textes déjà traduits** (pas des clés) :

1. clés de message structurelles `connector.{type}.label`, `connector.{type}.field.{name}.label` et
   `.help` (les champs partagés OAuth2 / routage mail sont dupliqués sous chaque type consommateur
   dans les bundles) ;
2. résolution dans la langue de la requête (`Accept-Language`), anglais par défaut ; pour le MCP,
   langue par défaut du tenant (`app.i18n.default-locale`) ;
3. le code de champ (`name`) et les valeurs SELECT ne changent pas ;
4. le SPA envoie `Accept-Language` selon la locale vue-i18n et recharge le catalogue au changement
   de langue.

Écarté : renvoyer des clés pour que le SPA traduise — doublerait le catalogue dans vue-i18n, et le MCP
afficherait des clés brutes.

**Impacte :** `ConnectorCatalogLocalizer`, `ConnectorDefinitionController`,
`ListConnectorDefinitionsTool`, `frontend/src/api/http.ts`, `ConnectorsAdminView`, bundles
`i18n/connector-catalog*.properties`, spec 06, GUIDE-UTILISATEUR (écran Connecteurs).
