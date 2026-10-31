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
  - Avec `connectorRequest`, `flowable:connectorInstanceId` doit être littéral (déploiement et exécution) : la requête ne
    part jamais vers une instance choisie par une variable (revue, M3).
- **D-ORCH-BUILT-REQUEST-ONLY** (revue, M4) — appelée depuis un process, une instance `http-rest` avec `baseUrl` n'accepte que
  l'entrée construite par `connectorRequest` (`ConnectorCallContext.requestBuiltByProcess`). La variable `connectorInput`,
  posable à une complétion de tâche, ne la pilote pas : sinon n'importe quel chemin, `DELETE` compris, avec les identifiants
  de l'instance. Les appels directs (admin, M2M) et les instances sans `baseUrl` gardent le comportement d'avant.
- **D-ORCH-BASEURL** — champ `baseUrl` par instance `http-rest`.
  - Format : https public (garde SSRF D-44), sans identifiants, requête ni fragment.
  - Avec `baseUrl`, l'appel donne `path` (gabarit `{nom}`), `pathParams` (chaque valeur encodée comme un seul segment, ni
    vide ni `.` ou `..`, 20 au plus) et `query` (encodée, 50 au plus). `url` est refusée.
  - Un chemin sans `/` initial, ou avec `//`, un segment `.` ou `..` (y compris `..;x`), `?`, `#`, espace ou `\` est refusé,
    de même qu'un encodage de `/ \ . ; %` ou d'un caractère de contrôle. Une valeur de `pathParams` ne contient ni `/`, ni
    `\`, ni caractère de contrôle. L'URL finale garde le schéma, l'hôte et le port de la base, et ne dépasse pas
    8 192 caractères ; la longueur est contrôlée avant toute expression régulière (quantificateurs possessifs).
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
- **D-ORCH-EXAMPLE** — exemple « arrivée d'un collaborateur » (identifiants techniques dans des variables remises à `null`
  au début du provisioning, champs du dossier pour l'affichage seulement, groupe IAM choisi par l'IT) : SIRH, IAM et ITSM ; validation du manager (approuver,
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

**Revue indépendante (2026-10-31, sous-agent au contexte neuf).** Aucun constat Critique. Corrigés, avec un test de
régression chacun :

- **H1** — l'exemple et le guide faisaient cibler les `DELETE` et `PUT` par des champs du dossier, modifiables par toute
  personne qui a le droit EDIT : une personne RH pouvait faire supprimer le compte IAM d'un administrateur à l'abandon. Les
  identifiants sont désormais retenus dans des variables écrites par le BPMN ; le guide §4 avertit que `dossier.fields` et
  `variables` ne sont pas des données de confiance. Test : `OnboardingOrchestrationExampleIT` (champs et variables de
  démarrage forgés, champs modifiés avant l'abandon). Une protection par la plateforme est à arbitrer
  ([#263](https://github.com/superjojo79/Myrmacta/issues/263)).
- **M1** — `StackOverflowError` sur l'expression régulière du chemin à partir d'environ 1 700 caractères.
- **M2** — remontée au-dessus du chemin de base par `..;`, `%2F` ou une valeur de `pathParams` contenant `/`.
- **M3** — instance de connecteur choisie par une variable malgré `connectorRequest`.
- **M4** — une variable `connectorInput` pilotant une instance à `baseUrl` (D-ORCH-BUILT-REQUEST-ONLY).
- **B1 à B5** :
  - `//` au milieu du chemin ;
  - grands entiers écrits en notation scientifique ;
  - en-têtes : nom hors RFC 7230, CR seul dans une valeur, `"Host "` ;
  - paramètres ajoutés après un fragment ;
  - avertissement sur `variables` dans le guide.

**Seconde revue indépendante (2026-10-31, sous-agent au contexte neuf, sur `147b6cf..4be428d`).** Aucun constat Critique ou
Haut ; elle confirme H1, M1, M3 et M4. Corrigés, avec un test de régression chacun :

- **M-1** — M2 incomplet : une valeur de `pathParams` `..;x` (ou `.. `) passait. Désormais, segment écrit ou valeur : on retire
  le paramètre `;x` et les espaces, puis on refuse ce qui n'est fait que de points. `..%20`, `...` et l'UTF-8 sur-long
  (`%C0%AE`) sont aussi refusés dans un chemin écrit.
- **M-2** — dans l'exemple, le groupe IAM ajouté dépendait du champ `profilPoste`, modifiable. Le groupe est maintenant la
  réponse de l'IT (`niveauAcces` : STANDARD, DEVELOPPEURS, DIRECTION). Test : le profil passe à DIRECTION après la validation,
  le groupe reste `developpeurs`.
- **Bas** :
  - `login` remis à `null` comme les deux autres variables ;
  - frontière asynchrone sur le contrôle qui suit chaque appel (un échec en aval ne rejoue plus le `POST`) ;
  - en-têtes de cadrage et de connexion refusés ;
  - fichier `__pycache__` retiré (et ignoré) ;
  - écarts de documentation : spec 06, texte du catalogue, exemple XML du guide, README ;
  - étape « connecteur » d'un parcours vers une instance à `baseUrl` signalée dans le guide.

**Limites assumées.**
- Un champ sensible n'est jamais lisible par l'expression, donc jamais envoyé par `connectorRequest`.
- Un seul `connectorResult` : on recopie dans le dossier (ou une variable) ce qu'un appel suivant doit relire.
- Réessayer rejoue la requête : l'idempotence est l'affaire de l'application appelée.
- Sans `baseUrl`, l'en-tête d'authentification part vers l'URL fournie à l'appel (comportement d'avant, rappelé dans les
  guides).
- Sans assignation par groupe ([#190](https://github.com/superjojo79/Myrmacta/issues/190)), toute personne qui a le droit EDIT
  peut prendre une tâche de validation.
- Une étape « connecteur » d'un parcours (`ParcoursCompiler`) ne porte pas `connectorRequest` : vers une instance à `baseUrl`,
  elle échoue à l'exécution, sans contrôle à la compilation.

**Impacte :**
- `docs/guides/GUIDE-CONCEPTION-BPMN.md` §4 et §7 ;
- `docs/guides/GUIDE-UTILISATEUR.md` §2 et §4 (Admin → Connecteurs) ;
- `docs/specs/06-connecteurs.md`, `docs/specs/18-connecteur-http-rest-service.md` ;
- `docs/decisions/identifiants.md`, `ROADMAP.md`.

Aucune migration, aucune variable d'environnement, aucune dépendance.
