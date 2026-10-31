# §112 — Orchestration technique : requête construite par le BPMN, `http-rest` « service seul » avec URL de base

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] livré (issue [#260](https://github.com/superjojo79/Myrmacta/issues/260)). Nouveau cas d'usage : coordonner plusieurs
applications REST depuis un workflow, avec des validations humaines (formulaires de tâche) qui orientent la suite. Exemple :
[exemples/onboarding-collaborateur](../../exemples/onboarding-collaborateur/README.md).

**Constat.**
- Le BPMN ne pouvait pas construire une requête. `connectorInput` ne venait que d'une variable posée au démarrage ou à la
  complétion d'une tâche, c'est-à-dire par un utilisateur.
- `http-rest` exigeait une URL absolue à chaque appel. En mode `STATIC_HEADER`, l'en-tête d'authentification partait donc
  vers toute URL publique fournie par cette variable.
- Une instance utilisée seulement en sortie devait déclarer un webhook entrant (secret, `processKey`, `dossierTypeCode`),
  qui restait ouvert.

Décisions tranchées par l'utilisateur le 2026-10-31 :

- **D-ORCH-REQUEST** — attribut **littéral** `flowable:connectorRequest` sur la brique « Appeler un connecteur ». C'est une
  expression du langage borné de LC2 ([§75](075-langage-d-expression-metier-borne-lc2.md)), avec la même racine
  (`BusinessExpressionContext` : champs déclarés non sensibles, `connectorResult` et `json`, variables scalaires, `now`).
  - Elle produit un objet, passé tel quel au connecteur. Elle prime sur la variable `connectorInput` et n'est jamais lue
    depuis une variable.
  - Validée au déploiement (parseur, 2 000 caractères, paramètre multiligne) et revalidée à l'exécution.
  - Un résultat qui n'est pas un objet fait échouer l'activité.
  - Écartés : gabarits `{dossierField:x}` sans structure ni calcul ; statu quo.
  - Catalogue des briques en version 6. Aucun nouvel outil MCP : `list_bpmn_building_blocks` et `deploy_workflow` le portent.
- **D-ORCH-BASEURL** — champ `baseUrl` par instance `http-rest`.
  - Format : https public (garde SSRF D-44), sans identifiants, requête ni fragment.
  - Avec `baseUrl`, l'appel donne `path` (gabarit `{nom}`), `pathParams` (chaque valeur encodée comme un seul segment, ni
    vide ni `.` ou `..`, 20 au plus) et `query` (encodée, 50 au plus). `url` est refusée.
  - Un chemin sans `/` initial, ou avec `//`, `..` (y compris `%2e`), `?`, `#`, espace ou `\` est refusé. L'URL finale garde
    le schéma, l'hôte et le port de la base, et ne dépasse pas 8 192 caractères.
  - Sans `baseUrl`, rien ne change.
- **D-ORCH-SERVICE-ONLY** — champ `webhookEnabled` (case cochée par défaut).
  - Faux : les champs du webhook ne sont plus exigés. Ils deviennent facultatifs dans le schéma ; le serveur les exige quand
    le webhook est actif.
  - `WebhookController` répond alors 404 avant toute authentification ; le SPA n'affiche pas d'URL d'entrée.
  - La capacité reste une propriété du type (D-CAPABILITY-SCOPE, [§48](048-activation-du-connecteur-http-rest-en-role.md)) :
    ce drapeau ne coupe que l'entrée.
- **D-ORCH-DELETE** — méthode `DELETE`, sans corps comme `GET`. **PATCH reporté** (issue
  [#261](https://github.com/superjojo79/Myrmacta/issues/261)) : `HttpURLConnection`, retenu pour l'IP figée et la vérification
  TLS du nom d'origine, refuse cette méthode. Le changement de client HTTP mérite son propre cycle.
- **D-ORCH-EXAMPLE** — exemple « arrivée d'un collaborateur » : SIRH, IAM et ITSM ; validation du manager (approuver,
  corriger, refuser) ; validation des accès par l'IT pour les profils sensibles ; incident technique (réessayer, abandonner) ;
  compensation par `DELETE`.
  - Les appels sont asynchrones : une panne réseau devient un incident rejouable. Un statut HTTP en erreur est routé par le
    processus.
  - `OnboardingOrchestrationExampleIT` le rejoue sur les fichiers livrés, par la chaîne HTTP et la sécurité, contre un faux
    serveur local.

**Correctif trouvé en chemin.** `OutboundUrlGuard.PinnedEndpoint.requestUrl()` reconstruisait l'URL avec le constructeur
multi-arguments de `URI`, qui prend des composants décodés : `%2F` redevenait `/` et `%26` `&`. Une valeur encodée pouvait
donc sortir de son segment ou de son paramètre, pour `http-rest` comme pour les webhooks sortants. L'URL garde désormais les
composants bruts (test de régression `OutboundUrlGuardTest.resolveSafe_keepsPercentEncodingOnPinnedUrl`).

**Limites assumées.**
- Un champ sensible n'est jamais lisible par l'expression, donc jamais envoyé par `connectorRequest`.
- Un seul `connectorResult` : on recopie dans le dossier (ou une variable) ce qu'un appel suivant doit relire.
- Réessayer rejoue la requête : l'idempotence est l'affaire de l'application appelée.

**Impacte :**
- `docs/guides/GUIDE-CONCEPTION-BPMN.md` §4 et §7 ;
- `docs/guides/GUIDE-UTILISATEUR.md` §2 et §4 (Admin → Connecteurs) ;
- `docs/specs/06-connecteurs.md`, `docs/specs/18-connecteur-http-rest-service.md` ;
- `docs/decisions/identifiants.md`, `ROADMAP.md`.

Aucune migration, aucune variable d'environnement, aucune dépendance.
