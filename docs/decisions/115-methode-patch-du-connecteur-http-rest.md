# §115 — Méthode PATCH du connecteur http-rest

> **Statut :** clos · point de premier niveau · [registre](README.md)

Livré (issue [#261](https://github.com/superjojo79/Myrmacta/issues/261)). Les API OData (D365 F&O, Dataverse, Graph) font
leurs mises à jour partielles en `PATCH`. Le [§112](112-orchestration-technique-requete-construite-par-le-bpmn.md) avait
accepté `DELETE` et reporté `PATCH` : `HttpURLConnection`, retenu pour l'adresse figée et la vérification TLS du nom
d'origine (D-44), refuse cette méthode.

**D-HTTP-PATCH** (mandat de l'utilisateur, 2026-10-31 : lancer #261) — `http-rest` accepte `PATCH`, avec un corps, comme
`POST` et `PUT`. `GET` et `DELETE` restent sans corps.

Le client de tous les appels qui passent par `HttpRestConnector.HttpSender` (l'appel sortant et le jeton OAuth2, y compris
ceux d'IMAP, de Graph et du bus Azure) est Apache HttpClient 5, version gérée par Spring Boot (Apache-2.0). Nouvelle
dépendance d'exécution. Écarté :

- le client HTTP du JDK avec `jdk.httpclient.allowRestrictedHeaders=host` : la vérification TLS sur une adresse IP figée
  est à réécrire ;
- `POST` et l'en-tête `X-HTTP-Method-Override` : le serveur ne l'honore pas toujours.

Garde SSRF, inchangée dans son intention (D-44) :

- `OutboundUrlGuard` choisit toujours l'adresse avant l'appel ;
- l'URL envoyée garde le nom d'hôte d'origine : le SNI et la vérification du certificat sont ceux du client, sans
  vérificateur réécrit ;
- le résolveur DNS ne renvoie que cette adresse, et seulement pour ce nom : pas de second DNS, donc pas de rebinding
  entre le contrôle et la connexion ;
- une redirection n'est pas suivie (sa cible ne repasse pas par la garde) ;
- pas de nouvelle tentative automatique, pas de cookies ;
- le délai borne la connexion et la lecture du corps ; le corps de la réponse reste borné (`maxResponseBytes`).

Les webhooks sortants (`WebhookRestAdapter`) restent sur `HttpURLConnection` : ils n'ont pas besoin de `PATCH`.

**Impacte :** `docs/specs/06-connecteurs.md`, `docs/specs/18-connecteur-http-rest-service.md`,
`docs/guides/GUIDE-UTILISATEUR.md`, `docs/guides/GUIDE-CONCEPTION-BPMN.md`,
`exemples/onboarding-collaborateur/README.md`.
