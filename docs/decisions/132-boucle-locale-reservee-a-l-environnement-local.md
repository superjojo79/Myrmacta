# §132 — Boucle locale réservée à l'environnement local

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (issue [#73](https://github.com/superjojo79/Myrmacta/issues/73), constat de la revue du lot 1 de #56).
Comportement : [spec 09](../specs/09-notifications.md) § « Implémentation — boucle locale » ;
[spec 49](../specs/49-digital-mail-room.md) §2 ; [GUIDE-UTILISATEUR](../guides/GUIDE-UTILISATEUR.md)
(connecteur `imap-inbound`).

**Constat.** `DevModeEnvironmentPostProcessor` refuse le profil `test` seulement quand
`APP_ENVIRONMENT=production` ([§95](095-remediation-de-la-revue-du-22-octobre.md), D-REMED-DEVMODE) :
la suite démarre aussi en `staging` (`McpDirectPublicationIntegrationTest`, profil `test` toujours actif
via `src/test/resources/application.properties`). `NotificationConfig` ouvrait alors `allowLocalHttp`
dès que le profil était `test` ou `local`. Un staging lancé avec le profil `test` laissait un
administrateur de tenant viser `http://localhost` (webhook, A13) et une boîte IMAP sur `localhost`,
n'importe quel port, en clair (`PLAIN`) — sondage de ports et connexions vers les services du conteneur,
répétés à chaque relève (D-IMAP-LOCAL-LOOPBACK, [§128](128-capsules-video-du-site.md)).

**D-LOOPBACK-LOCAL-ENV** — la boucle locale (`OutboundUrlGuard`, donc webhook `http` et
`isLocalLoopback` de l'IMAP) n'est permise que si les deux conditions sont vraies :

1. `app.environment` (`APP_ENVIRONMENT`), espaces retirés et casse ignorée, vaut `local` (absent =
   `production`, comme le post-processeur et la gouvernance MCP) ;
2. un profil actif est `test` ou `local` (casse et espaces ignorés).

Hors de là, rien ne change : `https` vers un hôte public, IMAP en SSL ou STARTTLS. Pas d'élargissement :
`APP_ENVIRONMENT=local` sans profil `test` ni `local` n'ouvre pas la boucle. Il n'existe pas de valeur
`ci` (`app.environment` reste `local | staging | production`) : la suite et la CI posent déjà
`app.environment=local`.

**Écarté.** Refuser le profil `test` dès que `APP_ENVIRONMENT` n'est ni `local` ni `ci`. Cela casse
`McpDirectPublicationIntegrationTest` et tout test qui exerce `staging` : le profil `test` est celui de
la suite, pas un environnement déployé. Le relâchement des secrets et des en-têtes sur ce profil en
`staging` reste celui de D-REMED-DEVMODE ; seule la boucle locale en sort.

**Impacte :** `OutboundUrlGuard`, `NotificationConfig`, `StubPublicDnsTestConfiguration`,
`ImapInboundConnector` (textes), specs 04, 09, 47 et 49, guides DEPLOIEMENT, GETTING-STARTED et
GUIDE-UTILISATEUR, `.env.example`, `.env.local.example`, identifiant `D-IMAP-LOCAL-LOOPBACK` (resserré).
