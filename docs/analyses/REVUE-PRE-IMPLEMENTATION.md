# Revue pré-implémentation — FlowForge

> Revue de la documentation d'architecture (`docs/specs/`, 12 fichiers) menée avant le lancement
> du développement, pour répondre à une question : **l'analyse est-elle implémentable en l'état ?**
>
> Ce document porte le **contexte détaillé** de chaque point ouvert — constat, extraits concernés,
> raison pour laquelle le point bloque, options envisageables et documents impactés.
> [registre des décisions](../decisions/README.md) porte l'**énoncé et le suivi** de ces mêmes points. Les deux se lisent ensemble :
> le TODO dit *quoi trancher*, la revue dit *pourquoi* et *avec quelles options*.
>
> **Suite (2026-08-25) :** revue post-implémentation alignement × sécurité — points **40–46**
> dans [docs/analyses/REVUE-ALIGNEMENT-SECURITE.md](REVUE-ALIGNEMENT-SECURITE.md). Ne pas fusionner les deux
> revues : celle-ci est figée sur l'état documentaire au commit `1d3d9eb`.

---

## 1. Périmètre et méthode

| | |
|---|---|
| Documents revus | `docs/specs/00-index.md` → `12-autorisation-conformite.md`, `README.md`, `TODO.md` |
| Volume | 12 documents thématiques, ~3 150 lignes |
| État du dépôt à la revue | commit `1d3d9eb` — aucun code applicatif, documentation seule |
| Angle de lecture | Cohérence inter-documents, complétude fonctionnelle, faisabilité des extraits de code fournis comme spécification |

Les numéros de ligne cités ci-dessous correspondent à l'état des documents au commit `1d3d9eb`.
Les extraits de code de `docs/specs/` sont traités comme de la **spécification exécutable** :
c'est le niveau de détail qu'ils affichent, et c'est ainsi qu'ils seront lus par les développeurs.

Le point le plus structurant identifié par cette revue (propagation du tenant hors requête HTTP) a
fait l'objet d'un arbitrage lors d'un échange de suivi, consigné en section 3 sous forme de décision.

---

## 2. Verdict

**L'analyse n'est pas prête à être lancée en implémentation complète, mais elle en est proche.**

Les choix technologiques sont arrêtés et argumentés, et les 12 points du registre de décisions ont
effectivement été tranchés et répercutés. Les trois points bloquants identifiés par cette revue
(propagation du tenant, résolution du tenant pour les appels non authentifiés, sécurité de la
session BFF), ainsi que les points 17 (entités JPA dans le domaine) et 27 (versions, build,
packaging frontend), ont depuis été tranchés — voir section 3. Ce qui reste à traiter relève de
deux catégories :

| Catégorie | Constat | Conséquence |
|---|---|---|
| **Contradictions internes** | Toutes tranchées (points 16, 18) | — |
| **Compléments et décisions produit** (points 19 à 26, 28 à 31) | Contrat d'API, modèle physique, tests, observabilité, conformité | Ne bloquent pas le démarrage du socle, mais doivent être planifiés explicitement |

**Recommandation :** le socle technique, la chaîne de déclenchement, les résidus et le contrôle
d'accès ressource (décision n° 20) sont tranchés. Le reste peut être traité au fil de l'eau ; le
point 19 (granularité RBAC fine) peut affiner les rôles sans remettre en cause le mécanisme ACL.

### Ce qui est solide et ne doit pas être rouvert

Ces éléments ont été vérifiés et sont considérés comme acquis — les rouvrir ferait perdre le
bénéfice du travail déjà fait :

- **Choix de stack et licences.** Java 25 LTS / Spring Boot 4.1.x / PostgreSQL / Flowable 8.0.x /
  Vue 3 sont cohérents entre eux et justifiés (décision n° 27). L'écartement d'iText 7 (AGPL) au
  profit de Flying Saucer (`flying-saucer-pdf` 10.4.x) + OpenPDF est un raisonnement de licence
  correct pour un SaaS commercial fermé.
- **Stratégie d'isolation par schéma PostgreSQL** (décision n° 1). Le choix d'isoler chaque tenant
  dans son propre schéma reste bon — seul le mécanisme de **bascule dynamique par requête** qu'il
  décrivait à l'origine devient superflu depuis la décision n° 13 (section 3) ; la stratégie
  d'isolation elle-même n'est pas remise en cause.
- **Modèle dossier / workflow.** La règle « un seul workflow actif par dossier » garantie par un
  index unique partiel (`uq_dossier_workflow_link_active`) plutôt que par une vérification
  applicative non atomique est le bon réflexe. La séparation entre `Dossier.status` (métier, propre
  au dossier) et `DossierWorkflowLink.status` (propre à l'exécution, configurable par
  `WorkflowTypeConfig`) est bien pensée et cohérente avec le principe des `fieldDefinitions`.
- **Résolution en cascade des templates** et unicité de la version `ACTIVE` par index partiel.
- **Décisions de sécurité déjà prises.** Chiffrement en enveloppe KEK/DEK délégué à un vault,
  signature HMAC-SHA256 des webhooks entrants vérifiée dans l'adaptateur web, rejet 403 en l'absence
  de claim `tenant_id` — ce dernier point change de nature avec la décision n° 13 (il devient une
  vérification d'égalité contre le tenant configuré de l'instance plutôt qu'une résolution de
  schéma) mais reste un contrôle valide et nécessaire.
- **Analyse SSE multi-instances.** Le raisonnement sur `LISTEN`/`NOTIFY` versus Redis versus sticky
  sessions reste correct dans son principe ; avec la décision n° 13 il devient un chemin d'évolution
  conditionnel (à activer seulement si un tenant est scalé sur plusieurs répliques) plutôt qu'un
  défaut de la V1.

---

## 3. Décisions actées durant la revue

### 13. Instance Spring Boot dédiée par tenant

**Problème identifié.** `TenantContext` est un bean `@RequestScope` (`02-multi-tenant.md`,
l. 44-52) et, depuis la décision n° 2, `TenantIdentifierResolver` lève
`MissingTenantContextException` lorsqu'il est vide plutôt que de retomber sur `public`
(l. 107-114). Ce durcissement est correct, mais il rend inopérants tous les composants documentés
qui s'exécutent **en dehors** d'une requête HTTP :

| Composant | Où | Pourquoi il n'a pas de requête HTTP |
|---|---|---|
| Relais outbox `@Scheduled(fixedDelay = 1000)` | `05-workflow-engine.md`, l. 76-84 | Thread du scheduler Spring |
| `ConnectorServiceTask` | `06-connecteurs.md`, l. 243-269 | `JavaDelegate` exécuté par le job executor asynchrone de Flowable |
| `WorkflowStateEventListener` | `10-visualisation-workflow.md`, l. 188-221 | Thread du moteur Flowable |
| Job Kubernetes de migration Liquibase | `02-multi-tenant.md`, l. 237 | Pod dédié, hors application |

Le cas le plus net est `ConnectorServiceTask` : il injecte explicitement `TenantContext` et appelle
`tenantContext.getTenantId()` (l. 249 et 258). `02-multi-tenant.md` renvoyait le sujet à une note de
fin (l. 258, « Évolution future — `ScopedValue` (Java 22+) »), en reportant à plus tard un prérequis
de la V1.

**Options examinées** avant cette décision, pour mémoire :

| Option | Principe | Limite retenue contre elle |
|---|---|---|
| Paramètre explicite partout | Le `tenantId` devient un paramètre de chaque use case / port | Ne suffit pas seul : le `CurrentTenantIdentifierResolver` Hibernate a besoin d'un contexte ambiant, pas d'un paramètre de méthode métier |
| Holder `ThreadLocal` + alimentations multiples | Un `TenantContextHolder` unique, alimenté par 4 sources différentes (HTTP, Flowable, scheduler, Job) | Un oubli de source reproduit exactement le bug actuel ; ne réduit pas le nombre d'endroits où le binding peut être manqué |
| `ScopedValue` seul, sans changer la topologie | Même holder que ci-dessus, sémantique plus sûre | Traite le symptôme (comment lier le contexte) sans traiter la cause (pourquoi autant d'acteurs ont besoin de le lier) |

**Décision retenue.** Abandon du modèle « pods génériques + résolution dynamique du tenant par
requête » au profit d'un modèle où **chaque tenant est servi par sa propre instance Spring Boot**,
démarrée séparément (port distinct ou serveur distinct), configurée avec son identifiant de tenant
en dur au démarrage — une propriété de configuration lue une fois, jamais une donnée résolue par
requête. Toutes les instances partagent la **même base PostgreSQL multi-schéma** (l'isolation par
schéma, décision n° 1, ne change pas), mais chaque instance n'ouvre jamais qu'une connexion vers
son propre schéma, fixé une fois pour toutes.

Le routage vers la bonne instance (sous-domaine, port ou chemin) est délégué à une couche devant
l'application (reverse-proxy), pas à l'application elle-même. **Le mode « instance mutualisée
servant plusieurs tenants dynamiquement » est abandonné** — il n'existe plus de chemin alternatif à
documenter ni à maintenir.

Le **scaling horizontal d'un tenant donné** (plusieurs répliques pour un seul et même tenant, si sa
charge le justifie) reste un chemin d'évolution documenté, à activer au cas par cas — il ne
réintroduit alors les problématiques de session/SSE partagées et de verrouillage Flowable que dans
le pool de répliques de ce tenant, jamais entre tenants.

**Pourquoi ça résout le problème plutôt que de le déplacer.** Le tenant cesse d'être une donnée
runtime dérivée d'une entrée utilisateur et redécouverte à chaque unité de travail ; il devient une
constante de configuration au démarrage de la JVM, partagée par construction par tous les threads
du process — HTTP ou pas. Il n'y a plus rien à propager, donc plus d'endroit où l'oubli de
propagation peut se reproduire.

### Conséquences sur le mécanisme technique déjà documenté

Cette décision rend obsolète une part importante du mécanisme dynamique de `02-multi-tenant.md` —
à réviser lors de la prochaine passe sur `docs/specs/`, non encore faite à ce stade :

- Le tenant devient une propriété de configuration lue une fois au démarrage. `TenantContext`
  (`@RequestScope`), `TenantIdentifierResolver` (SPI multi-tenant Hibernate) et
  `SchemaMultiTenantConnectionProvider` n'ont plus de raison d'être : une seule connexion, un seul
  schéma, fixé par une simple configuration (`connectionInitSql` Hikari ou équivalent), sans
  bascule.
- Le rôle du `TenantResolutionFilter` change de nature : il ne sélectionne plus de schéma, il
  **vérifie une identité** — le claim `tenant_id` du token doit correspondre au tenant configuré de
  l'instance, sinon rejet (403). C'est un contrôle d'égalité contre une constante, pas une
  résolution dynamique.
- Le `TenantRegistry` n'a plus de rôle de sécurité applicative — il servait de deuxième barrière
  contre un claim JWT mal mappé avant de basculer un schéma (décision n° 1). Son seul usage
  restant possible est celui d'un annuaire d'infrastructure pour la couche de routage/provisioning
  (« quel tenant sur quelle instance »), un composant distinct, pas un service consulté à chaque
  requête applicative.
- Le relais outbox (décision n° 7 / point 16) devient nativement mono-tenant : il ne poll jamais
  que le schéma de son unique tenant, sans boucle sur un registre de tenants actifs.
- La migration Liquibase d'un tenant redevient une étape de démarrage de son unique instance dédiée
  (ne touche jamais que son propre schéma) — plus besoin d'un Job Kubernetes orchestrant plusieurs
  schémas en parallèle avant le démarrage de pods génériques (voir décision n° 10, mise à jour dans
  [registre des décisions](../decisions/README.md)).
- Le job executor Flowable ne peut plus entrer en contention avec lui-même sur un même tenant qu'au
  sein du pool de répliques de ce tenant, s'il est scalé — jamais entre tenants (voir décision
  n° 11, mise à jour dans le [registre des décisions](../decisions/README.md)).
- Toute la section « Robustesse du `search_path` face au pool de connexions » de
  `02-multi-tenant.md` (les trois risques de fuite entre tenants via le pool Hikari, et le test
  d'intégration recommandé pour les détecter) devient sans objet : un pool ne dessert jamais plus
  d'un schéma. C'est un allègement net de l'effort de test (voir point 28).
- L'usage natif du `tenantId` Flowable (`.tenantId(...)` sur les déploiements et instances de
  processus) devient redondant avec l'isolation par schéma plutôt que porteur — à conserver par
  défense en profondeur à coût nul, ou à supprimer ; à trancher lors de la réécriture de
  `05-workflow-engine.md`.

**Documents révisés en conséquence :** `02-multi-tenant.md` (quasi intégralement), `README.md`
(modèle de déploiement), `05-workflow-engine.md` (relais outbox, scaling Flowable), `06-connecteurs.md`
(remplacement de `TenantContext` par `TenantProperties` dans `ConnectorServiceTask` et
`ConnectorExecutionController`), `04-dossiers.md` et `08-templates-documents.md` (même remplacement),
`09-notifications.md` (SSE repositionné en chemin d'évolution conditionnel, plus un défaut).

---

### 14. Résolution du tenant pour les appels non authentifiés — routage par chemin + `client_credentials` par tenant

**Problème identifié.** `06-connecteurs.md` exposait le point d'entrée des webhooks entrants ainsi
(l. 369-390 avant révision) :

```java
@PostMapping("/{instanceId}")
public ResponseEntity<Void> receive(@PathVariable UUID instanceId,
        HttpServletRequest request, @RequestBody byte[] rawBody) {

    ConnectorInstanceData instance = instances.findById(instanceId).orElseThrow();
    ...
}
```

Trois problèmes s'enchaînaient :

1. **Le filtre rejetait l'appel avant d'y arriver.** L'ancien `TenantResolutionFilter` renvoyait 403
   dès que le claim `tenant_id` était absent. Un système tiers qui poste un webhook ne présente
   évidemment aucun token OIDC.
2. **La résolution était circulaire.** Même en exemptant la route du filtre, `instances.findById(...)`
   est une requête JPA : elle a besoin du `search_path`, donc du tenant, pour lire la ligne qui
   contient précisément le tenant.
3. **La signature utilisée n'était pas celle du port** (`findByIdAndTenantId(...)`).

Le même trou existait pour l'appel machine-to-machine `/api/connectors/{instanceId}/execute`,
associé au rôle `API_SERVICE`, sans flux `client_credentials` décrit.

**Effet de la décision n° 13 sur le problème.** Le modèle « instance dédiée par tenant » simplifiait
déjà une partie du problème : l'hôte, le port ou le chemin d'un webhook peuvent suffire à router la
requête vers l'instance du bon tenant **au niveau du reverse-proxy**, avant même qu'elle n'atteigne
l'application. La résolution circulaire disparaît par construction, mais le mécanisme de routage
restait à choisir, ainsi que le flux d'authentification machine-to-machine, question indépendante du
modèle de déploiement.

**Options examinées pour le routage :**

| Option | Principe | Avantages | Coûts / risques |
|---|---|---|---|
| Sous-domaine (`tenant_a.example.com`) | Vhost par tenant | URLs propres, pratique standard SaaS | Gestion de certificat (wildcard ou émission automatisée par tenant) à mettre en place |
| Port dédié | `webhooks.example.com:900N` par tenant | Pas de DNS/certificat supplémentaire | Peu praticable pour des systèmes tiers dont le pare-feu n'autorise souvent que 80/443 ; ports à allouer et suivre |
| **Chemin (retenu)** — `example.com/{tenantId}/...` | Un préfixe d'URL par tenant, sur un hostname unique | Un seul certificat, un seul hostname, config de reverse-proxy simple | Expose l'identifiant de tenant dans l'URL (mineur — déjà nécessaire de toute façon pour le routage, quel que soit le mécanisme) |

**Décision retenue.** Routage par **chemin**, avec une configuration de reverse-proxy **statique**
(Apache `mod_proxy`/`mod_rewrite`, un bloc `ProxyPass`/`ProxyPassReverse` par tenant ajouté au
provisioning) plutôt qu'un annuaire consulté dynamiquement à chaque requête. Chaque instance sert
nativement sous ce même préfixe via `server.servlet.context-path: /${app.tenant.id}` — un mécanisme
standard de Spring Boot, sans logique applicative dédiée au multi-tenant. Le même mécanisme sert
uniformément le trafic humain (SPA, redirection OAuth2), les webhooks et l'API machine-to-machine.

Pour l'API machine-to-machine, un flux OAuth2 **`client_credentials`** est ajouté : un client
Keycloak dédié par tenant, provisionné avec le reste de l'onboarding, dont un protocol mapper attache
un claim `tenant_id` fixe à tout token émis. Une chaîne de sécurité Spring distincte (`securityMatcher`
sur les endpoints machine-to-machine), en *resource server* JWT, applique la **même vérification
d'identité que le flux humain** — le `tenant_id` du token doit correspondre au tenant configuré de
l'instance (`TenantProperties`), sinon rejet.

**Effets de bord positifs.** Le comportement pour un tenant en `MIGRATING_ERROR` se résout par
construction : une instance dont la migration échoue au démarrage (décision n° 13) ne devient jamais
disponible, donc n'est jamais atteinte par le routage — pas de logique 503 dédiée à écrire côté
application. `findByIdAndTenantId(...)` dans `WebhookController` redevient une défense en profondeur
plutôt qu'une nécessité, puisque le schéma seul garantit déjà l'isolement une fois la requête routée.

**Documents révisés en conséquence :** [02-multi-tenant.md](../specs/02-multi-tenant.md)
(section « Routage vers l'instance »), [03-authentification.md](../specs/03-authentification.md)
(section « Authentification machine-to-machine »), [06-connecteurs.md](../specs/06-connecteurs.md)
(`WebhookController` corrigé, note sur `ConnectorExecutionController`),
[12-autorisation-conformite.md](../specs/12-autorisation-conformite.md) (renvoi vers le flux
`client_credentials`).

### 15. Sécurité et cycle de vie de la session BFF — CSRF, déconnexion, session par tenant

**Problème identifié.** Le pattern BFF avec cookie de session `httpOnly` était bien justifié face au
XSS, mais son corollaire et ses conséquences opérationnelles n'étaient pas traités — aucune
occurrence de « CSRF », « SameSite » ou « Spring Session » dans le dépôt.

**a) CSRF.** Un cookie de session est envoyé automatiquement par le navigateur, y compris sur une
requête déclenchée depuis un site tiers : c'est exactement le vecteur que le BFF réintroduit en
échange de l'immunité au vol de token. Ce n'est pas seulement un risque théorique, c'est aussi un
blocage fonctionnel immédiat : Spring Security active la protection CSRF par défaut, donc en
l'absence de configuration explicite **tous les `POST`/`PUT`/`DELETE` de la SPA échoueraient en
403**.

**b) Déconnexion.** Le `logout` documenté se limitait à `session.invalidate()`. La session côté
fournisseur OIDC restait ouverte, donc la redirection de la SPA en cas de perte de session
reconnectait l'utilisateur silencieusement : la déconnexion était visuellement effective mais
fonctionnellement nulle.

**c) Session partagée entre répliques.** Déjà réglé par la décision n° 13 — une session HTTP en
mémoire suffit par défaut avec une instance dédiée par tenant ; le sujet ne redevient pertinent que
si un tenant précis est scalé sur plusieurs répliques.

**Décision retenue pour (a) — protection CSRF.** Jeton porté par un cookie lisible en JavaScript
(`CookieCsrfTokenRepository.withHttpOnlyFalse()`, pattern double-submit), avec
`.setCookieCustomizer(...)` pour poser `SameSite=Lax`. L'hypothèse « même origine » est **confirmée**
par la décision n° 27 (frontend embarqué dans `static/` de chaque instance Spring Boot) — pas de
CORS, pas de `SameSite=None`. Point d'implémentation important, introduit par Spring Security 6 et
toujours présent sous Spring Boot 4 / Security 7 : le `CsrfToken` est résolu de façon paresseuse —
le cookie n'est effectivement écrit que si quelque chose lit le token, ce qu'une SPA pure (sans
formulaire Thymeleaf) ne fait jamais spontanément ; un filtre dédié force cette résolution sur
chaque requête.

Plutôt qu'une exemption (`ignoringRequestMatchers`) de la route webhook au sein de la chaîne BFF,
les webhooks et l'API machine-to-machine reçoivent chacun leur propre chaîne de sécurité
(`securityMatcher`, sans session ni CSRF) — trois chaînes ordonnées (`@Order`), jamais traversées
simultanément par une même requête, plutôt qu'une seule chaîne truffée d'exceptions.

**Décision retenue pour (b) — déconnexion.** `OidcClientInitiatedLogoutSuccessHandler`, qui appelle
l'`end_session_endpoint` du fournisseur avec l'`id_token_hint` de l'utilisateur, fermant la session
Keycloak en plus de la session locale.

**Effet de bord découvert en spécifiant (b).** Le routage par chemin de la décision n° 14 fait
varier l'URL de callback OAuth2 (`/{tenantId}/login/oauth2/code/keycloak`) et de post-logout par
tenant. Plutôt qu'un provisioning Keycloak par tenant pour le client d'authentification humaine
(contrairement au client `client_credentials`, dédié par tenant pour porter son propre claim), une
entrée à caractère joker (`https://example.com/*`) dans les `Valid Redirect URIs` /
`Valid Post Logout Redirect URIs` du client partagé couvre tous les tenants sans provisioning
supplémentaire à l'onboarding — combinée à `server.forward-headers-strategy: framework` (décision
n° 14), qui garantit que Spring calcule ces URLs dynamiquement, préfixe de tenant inclus.

**Documents révisés en conséquence :** [03-authentification.md](../specs/03-authentification.md)
(nouvelles sections « Callback OAuth2 derrière le routage par chemin », « Protection CSRF » et
« Déconnexion — RP-initiated logout » ; suppression de la méthode `logout()` manuelle),
[11-frontend.md](../specs/11-frontend.md) (renvoi vers la configuration Axios).

---

### 17. La règle « le domaine est pur Java » — appliquée à toutes les entités

**Problème identifié.** `01-architecture-hexagonale.md` énonçait la règle sans ambiguïté (aucune
annotation JPA dans `com.monapp.domain`, entités JPA distinctes des entités domaine), mais la
décision n° 5 ne l'avait appliquée qu'à `ConnectorInstance` (→ `ConnectorInstanceData` /
`ConnectorInstanceJpaEntity`). Sept autres classes restaient `@Entity` tout en étant déclarées dans
le domaine et exposées dans les signatures de ports :

| Classe | Document | Annotations avant révision |
|---|---|---|
| `Dossier` | `04-dossiers.md` | `@Entity`, `@Convert`, `@Column`, `@Enumerated` |
| `DossierType` | `04-dossiers.md` | idem |
| `DossierElement` | `04-dossiers.md` | idem |
| `DossierRelation` | `04-dossiers.md` | `@Entity`, `@Enumerated` |
| `DossierWorkflowLink` | `04-dossiers.md` | `@Entity` |
| `WorkflowTypeConfig` | `05-workflow-engine.md` | `@Entity`, `@Convert`, `@Column` |
| `DocumentTemplate` | `08-templates-documents.md` | `@Entity`, `@Table`, `@Column` |

Le symptôme le plus concret : `GetDossierWorkflowHistoryUseCase.getHistory(...)` renvoyait une
entité de persistance directement comme corps de la réponse de
`GET /api/dossiers/{dossierId}/workflows`.

**Options examinées.**

| Option | Principe | Coûts / risques |
|---|---|---|
| A — Appliquer la règle strictement | Modèle domaine pur + `*JpaEntity` + mappers pour les 7 entités | Volume de mapping à écrire et maintenir |
| B — Assumer les entités JPA comme modèle unique | Amender `01`, revenir sur la décision n° 5 | Le domaine dépend de `jakarta.persistence` ; le schéma de base contraint le modèle métier |
| C — Hybride | Modèle pur pour les agrégats à comportement, JPA direct pour la configuration | Frontière à documenter précisément, sinon dérive progressive — le risque même qui a produit ce point |

**Décision retenue — option A, sans exception.** `01` énonce la règle de façon absolue ; introduire
une frontière C non universelle risquait de reproduire exactement le type de dérive qui a produit ce
point (une règle énoncée une fois, appliquée à géométrie variable). Un examen plus poussé a réduit
l'argument de volume contre A : la plupart de ces classes n'ont aujourd'hui aucun comportement
documenté — de simples porteurs de données — ce qui permet d'utiliser un objet immuable Lombok
plutôt qu'une classe complète à mapper à la main.

**`record` écarté à tort dans un premier temps, puis retenu après correction du raisonnement.** Un
`record` Java coûte aussi peu à écrire qu'une entité JPA, ce qui aurait encore réduit l'argument de
volume. Il a d'abord été écarté au motif qu'il génère des accesseurs sans préfixe
(`allowUnknownFields()`), incompatibles avec le style d'accesseur déjà utilisé dans les exemples de
code existants (`type.isAllowUnknownFields()`, `type.getFieldDefinitions()` dans `04-dossiers.md`).

Ce raisonnement traitait la documentation comme du code réel déjà compilé, avec des appelants figés
qu'il faudrait préserver — alors que le projet est encore en phase d'analyse, sans aucune
implémentation. Les extraits illustratifs de `docs/specs/` ne sont pas des points d'appel figés :
ils s'éditent au même titre que le reste de cette documentation, sans coût de rupture de
compatibilité réel. Une fois cette contrainte artificielle levée, `record` redevient le choix le
mieux justifié : fonctionnalité native du JDK, sans dépendance à un processeur d'annotations pour le
modèle domaine ; immuabilité garantie par le langage plutôt que par convention ; cohérent avec
l'appétit déjà montré pour Java 21 moderne (`sealed interface` de la décision n° 6) ; et déjà
utilisé ailleurs dans ces mêmes documents (`WorkflowDiagramView` dans
[10-visualisation-workflow.md](../specs/10-visualisation-workflow.md)) — ce n'est pas une
convention étrangère au projet, seulement pas encore appliquée aux agrégats.

`@Builder` (Lombok) reste utilisé au-dessus des `record` pour la construction des objets à de
nombreux champs — un `record` conserve son constructeur canonique quoi qu'il arrive, `@Builder`
n'ajoute qu'une syntaxe de construction alternative sans renoncer aux accesseurs natifs ni à
l'immuabilité. Une transition d'état (`Dossier.withStatus(...)`) s'implémente comme une méthode
d'instance ordinaire retournant une nouvelle instance via le constructeur canonique. Les entités JPA
elles-mêmes ne peuvent de toute façon pas être des `record` (Hibernate a besoin d'un constructeur
sans argument et de champs mutables).

**Convention de nommage.** Le modèle domaine garde son nom métier naturel (`Dossier`,
`DossierType`…) ; l'entité de persistance reçoit le suffixe `JpaEntity` — le principe déjà illustré
par `WorkflowInstance`/`WorkflowJpaEntity`. `ConnectorInstanceData`/`ConnectorInstanceJpaEntity`
reste une exception de nommage historique de la décision n° 5 (son nom n'est pas retouché, ce serait
du churn sans bénéfice), mais sa **forme** de déclaration a été alignée sur le `record` — la laisser
seule diverger aurait recréé exactement le type d'exception isolée que cette décision vise à
éliminer.

**Effets de bord positifs.** Le risque concret de fuite d'un proxy Hibernate en réponse REST
(`GetDossierWorkflowHistoryUseCase`) disparaît par construction, puisque `DossierWorkflowLink` n'est
plus une entité JPA — sans danger technique à être sérialisé tel quel. Le découplage plus général du
contrat d'API vis-à-vis du modèle domaine reste néanmoins du ressort du point 24, pas de cette
décision : rien n'empêche qu'un renommage de champ domaine change silencieusement la réponse HTTP
tant qu'un DTO dédié n'est pas introduit.

**Corollaire acté dans le même mouvement.** La règle de dépendance du domaine — et la convention
`usecase`/`Impl` de la décision n° 9, qui avait déjà dérivé une fois sous la seule garde de la revue
de code — est désormais vérifiée par un test **ArchUnit** exécuté en CI.

**Documents révisés en conséquence :** [01-architecture-hexagonale.md](../specs/01-architecture-hexagonale.md)
(règle généralisée, convention de nommage, section ArchUnit),
[04-dossiers.md](../specs/04-dossiers.md) (5 entités converties en `record`, exemple
d'adaptateur complet sur `Dossier`), [05-workflow-engine.md](../specs/05-workflow-engine.md)
(`WorkflowTypeConfig`), [06-connecteurs.md](../specs/06-connecteurs.md) (`ConnectorInstanceData`
alignée sur le `record`, tous ses points d'appel mis à jour),
[08-templates-documents.md](../specs/08-templates-documents.md) (`DocumentTemplate`),
[10-visualisation-workflow.md](../specs/10-visualisation-workflow.md) (note sur la réponse
d'API désormais sans risque technique).

---

### 27. Versions figées, structure du build et packaging du frontend

**Problème identifié.** Les versions de stack n'étaient pas épinglées (`Flowable 7.x.x`,
`Spring Boot 3.x`, « Java 21 »), et les deux seules versions figées (`flying-saucer-pdf-openpdf
9.9.4`, `jsoup 1.17.2`) dataient. Aucun `pom.xml` ni structure de projet n'était défini. Trois
choix structurants restaient ouverts : les versions exactes ; mono-module vs multi-module Maven ;
packaging du frontend (embarqué vs hébergement séparé), ce dernier conditionnant CORS / `SameSite` /
CSRF de la décision n° 15.

**Décision retenue — trois orientations validées ensemble.**

**1. Versions de stack.**

| Composant | Version retenue |
|---|---|
| JDK | Java **25** LTS |
| Framework | Spring Boot **4.1.x** (Spring Framework 7, Spring Security 7) |
| Workflow | Flowable **8.0.x** |
| PDF | `org.xhtmlrenderer:flying-saucer-pdf` **10.4.x** (remplace `flying-saucer-pdf-openpdf`, abandonné) |
| HTML → XHTML | jsoup **1.22.x** |
| Auth server | Keycloak **26.7.x** |

Points de vigilance actés dès cette décision : Jackson 3 est le défaut de Flowable 8 (couche de
compatibilité Jackson 2 encore disponible via `flowable.variable-json-mapper=jackson2` si un
composant tiers bloque) ; l'ordre d'initialisation Liquibase / Flowable sous Boot 4 doit être
vérifié dès le premier démarrage d'intégration — bug potentiel connu à surveiller.

**2. Build mono-module Maven.** Un seul module applicatif. Depuis la décision n° 17, la règle
« domaine pur » est déjà vérifiée par ArchUnit en CI ; un module `domain` séparé n'apporterait
qu'une garantie redondante au niveau du build, au prix d'une découpe figée trop tôt pour une phase
d'analyse qui n'a encore aucun code.

**3. Frontend embarqué.** Le build Vue 3 (Vite) est copié dans `src/main/resources/static/` et
servi par chaque instance Spring Boot dédiée (décision n° 13). Conséquences :
- même origine API / SPA → confirme `SameSite=Lax` sans configuration CORS (décision n° 15) ;
- chaque instance embarque le même artefact front (N copies identiques) — acceptable au regard de
  la simplicité opérationnelle et du déploiement par tenant déjà retenu ;
- le reverse-proxy Apache (décision n° 14) route `/{tenantId}/...` vers l'instance, assets statiques
  inclus.

**Documents révisés en conséquence :** [00-index.md](../specs/00-index.md),
[05-workflow-engine.md](../specs/05-workflow-engine.md),
[07-generation-documents.md](../specs/07-generation-documents.md),
[03-authentification.md](../specs/03-authentification.md) (`SameSite` confirmé),
[11-frontend.md](../specs/11-frontend.md) (packaging embarqué),
[01-architecture-hexagonale.md](../specs/01-architecture-hexagonale.md) (note mono-module),
[README.md](../../README.md).

---

### 16. Outbox transactionnel et `ConnectorTriggerRuntime` — option B

**Problème identifié.** La décision n° 7 (`05-workflow-engine.md`) posait un outbox transactionnel
relayé par un scheduler, tandis que `06-connecteurs.md` et `04-dossiers.md` décrivaient un
`ConnectorTriggerRuntime` entièrement synchrone (créer dossier → réserver lien → démarrer Flowable
→ confirmer) sans aucune outbox. Un développeur suivant `06` n'avait aucune durabilité ; un autre
suivant `05` ne savait pas quoi faire à l'arrivée du relais. Le pseudo-code du relais annulait en
outre sa propre garantie (`markProcessed` même après échec de publication, hors transaction).

**Options examinées :**

| Option | Principe | Verdict |
|---|---|---|
| A — Tout synchrone | Abandonner l'outbox ; ACK webhook après `confirmStarted` | Écartée — rouvre la décision n° 7 ; batch long bloque HTTP |
| **B — Outbox après dossier** | Créer/résoudre le dossier + écrire l'outbox atomiquement ; le relais démarre Flowable | **Retenue** |
| C — Outbox du payload brut | ACK immédiat sur le brut ; normalisation + dossier + Flowable dans le relais | Écartée — ACK avant validation métier ; poison messages systématiques |
| D — Hybride par source | Synchrone pour webhooks, outbox pour Kafka/scheduler | Écartée — deux chemins à maintenir |

**Décision retenue — option B.**

**Phase producteur** (`ConnectorTriggerRuntime.onIncomingEvent`, une transaction) :
1. `TriggerConnector.onEvent(...)` normalise le payload brut en `List<WorkflowTriggerEvent>` ;
2. pour chaque événement : créer le dossier **ou** résoudre `existingDossierReference` ;
3. écrire `outbox_event` contenant le `WorkflowTriggerEvent` normalisé + `dossierId` +
   `connectorInstanceId` ;
4. ACK au webhook. **Aucun** démarrage Flowable dans cette phase.

**Phase relais** (`@Scheduled`, une transaction par événement) :
1. sélectionner les `PENDING` (avec `FOR UPDATE SKIP LOCKED` seulement si multi-répliques) ;
2. réserver le `DossierWorkflowLink` — ou **reprendre** un lien `RESERVED` déjà créé au rejeu
   (idempotence : jamais recréer le dossier) ;
3. démarrer l'instance Flowable + `confirmStarted` ;
4. `markProcessed` **uniquement si les étapes 2–3 ont réussi, dans la même transaction**.

Échec → incrément `attempts` + `lastError` ; au-delà d'un plafond → statut `DEAD` (dead-letter).
`WorkflowAlreadyRunningException` (autre workflow actif sur le dossier) → dead-letter immédiatement
(le dossier existe déjà, l'ACK a été donné). Purge des `PROCESSED` au-delà d'une rétention
configurable.

**Compromis accepté :** si `onEvent(...)` échoue avant l'écriture outbox, la transformation n'est
pas rejouable — le webhook reçoit l'erreur et l'appelant retry. C'est le prix de l'ACK après
validation métier / création de dossier.

**Documents révisés en conséquence :** [05-workflow-engine.md](../specs/05-workflow-engine.md)
(outbox + relais corrigés), [06-connecteurs.md](../specs/06-connecteurs.md)
(`ConnectorTriggerRuntime` scindé producteur / consommateur),
[04-dossiers.md](../specs/04-dossiers.md) (création batch alignée).

---

### 18. Résidus de refactoring et références orphelines

**Problème identifié.** Après les décisions n° 14–17, trois écarts ponctuels restaient dans les
extraits de spécification (a/b/d/g déjà corrigés au passage) :

| # | Écart |
|---|---|
| c | `instance.getWebhookAuthType()` sur un `ConnectorInstanceData` qui n'expose que `config` |
| e | `DocumentGenerationService` / `DocumentStorageService` absents de l'hexagone (`GenerateDocumentUseCase` / `DocumentStoragePort`) |
| f | `12` affirme `updatedBy` sur `Dossier`/`DossierElement` — champ absent de `Dossier` ; `DossierElement` utilise `addedBy`/`addedAt` |

**Décision retenue — c1 + e1 + f1.**

- **c1 :** lecture depuis `config` —
  `WebhookAuthType.valueOf((String) instance.config().get("webhookAuthType"))` — cohérent avec le
  `ConfigField` SELECT déjà documenté ; pas de champ de premier niveau.
- **e1 :** `DocumentGenerationTask` injecte `GenerateDocumentUseCase` et `DocumentStoragePort`.
- **f1 :** `updatedBy` ajouté sur `Dossier` (record, JPA, mappers, `withStatus`) ; `12` corrigé pour
  `DossierElement` (`addedBy` / `addedAt`).

**Documents révisés en conséquence :** [06-connecteurs.md](../specs/06-connecteurs.md),
[08-templates-documents.md](../specs/08-templates-documents.md),
[04-dossiers.md](../specs/04-dossiers.md),
[12-autorisation-conformite.md](../specs/12-autorisation-conformite.md).

---

### 20. Contrôle d'accès au niveau type de dossier — `AccessContext` + `dossier_type_acl` + policy

**Problème identifié.** Le contrôle de rôle ne protège pas contre l'accès aux dossiers hors
périmètre. `12` esquissait un `DossierAccessPort` sans distinguer humain / système, ni matérialiser
le périmètre.

**Décision retenue — A2 + B2 + C1**, avec **amendement de B2** : l'ACL porte sur le
**`DossierType`**, pas sur l'instance de dossier.

**`AccessContext` (A2) — inchangé :** `User` (ACL) · `System` (contournement moteur/outbox) ·
`Service` (refus dossier).

**Table `dossier_type_acl` (B2 amendée) :**

| Colonne | Rôle |
|---|---|
| `tenant_id` | Tenant |
| `dossier_type_code` | Type concerné (pas un `dossier_id`) |
| `subject_user_id` | Sujet OIDC |
| `permission` | `VIEW` \| `EDIT` (`EDIT` ⇒ `VIEW`) |
| `granted_by` / `granted_at` | Audit |

Unicité `(tenant_id, dossier_type_code, subject_user_id)`. Un grant donne accès à **toutes** les
instances du type. Administration : `GrantDossierTypeAccessUseCase` /
`RevokeDossierTypeAccessUseCase` (admins). Création humaine de dossier exige `EDIT` sur le type ;
liste filtrée aux types `VIEW`. Pas de grant à la création d'instance ni à l'assignation Flowable.
`TENANT_ADMIN` / `WORKFLOW_ADMIN` contournent. Couvre la restriction « rôle × types » du point 19.

**Abandonné :** table `dossier_acl` par instance (première formulation de B2).

**`DossierAccessPolicy` (C1)** — évalue sur `dossierTypeCode` via `DossierTypeAclRepository`.
Refus → `AccessDeniedException` → 403.

**Documents révisés :** [12-autorisation-conformite.md](../specs/12-autorisation-conformite.md),
[04-dossiers.md](../specs/04-dossiers.md).

---

## 4. Contradictions internes à lever

### 16. Outbox transactionnel et `ConnectorTriggerRuntime` — deux séquences incompatibles

**Décidé** — voir section 3 (option B : outbox après création/résolution du dossier ; relais démarre
Flowable).

### 18. Résidus de refactoring et références orphelines

**Décidé** — voir section 3 (c1 + e1 + f1).

---

## 5. Décisions produit et conformité en suspens

Ces cinq points sont ceux que `12-autorisation-conformite.md` liste lui-même comme « à trancher »
dans sa section « Synthèse des décisions à trancher ». Le document prévoyait leur fusion dans le
registre : c'est fait, ils deviennent les points 19 à 23 de [registre des décisions](../decisions/README.md).

### 19. Granularité des rôles RBAC

Les cinq rôles de référence (`TENANT_ADMIN`, `WORKFLOW_ADMIN`, `DOSSIER_MANAGER`, `DOSSIER_VIEWER`,
`API_SERVICE`) sont présentés comme un point de départ à valider avec les premiers cas d'usage réels.
Le mapping `realm_access.roles` → autorités Spring Security (préfixe `ROLE_`, converter dédié)
reste à spécifier. La restriction à un sous-ensemble de `DossierType` est déjà couverte par
l'ACL de type (décision n° 20).

### 20. Contrôle d'accès au niveau type de dossier

**Décidé** — voir section 3 (A2 `AccessContext` + B2 `dossier_type_acl` + C1 `DossierAccessPolicy` ; ACL au niveau type).

### 21. Politique de rétention par `DossierType`

Durée de conservation configurable par tenant et par type, action à expiration (archivage puis purge),
et articulation avec les statuts `CLOTURE` / `ARCHIVE` existants (`04-dossiers.md`, l. 276-288).

### 22. Purge cohérente PostgreSQL / MinIO

Un `DossierElement` de type `ATTACHMENT` ou `PRODUCED_DOC` a une ligne en base et un objet dans MinIO
(`04-dossiers.md`, l. 153). Le droit à l'effacement et la suppression d'un tenant (`DROP SCHEMA`,
`12`, l. 72-74) imposent une purge symétrique des deux stores. À trancher : transaction applicative
best-effort, job de réconciliation, ou suppression logique suivie d'un balayage. Avec la décision
n° 13, la suppression d'un tenant devient aussi une opération de déploiement (décommissionner son
instance dédiée) en plus de l'opération base de données — à documenter conjointement.

### 23. Classification des champs sensibles

Un `FieldDefinition` peut décrire une donnée personnelle sans le signaler. Décider si le flag
`sensitive: boolean` entre en V1, et ce qu'il conditionne (masquage à l'affichage, exclusion des
exports, exclusion des logs et des payloads de webhooks sortants).

---

## 6. Compléments de conception

### 24. Contrat d'API — surface incomplète, pas de format pivot

Les endpoints sont dispersés dans des tableaux au fil des documents, sans schémas de requête ni de
réponse. Deux manques distincts :

**La surface fonctionnelle est incomplète.** Pour un produit dont le dossier est l'entité centrale,
aucun endpoint de création, de recherche, de liste ou de mise à jour de dossier n'est documenté —
seul `POST /api/dossiers/{dossierId}/workflows` existe (`04`, l. 495). Il en va de même pour
l'administration des `DossierType`, des `ConnectorInstance` et des templates, alors que l'UI est
censée générer ses formulaires depuis leurs schémas. Les listes impliquent par ailleurs pagination,
tri et filtres, non abordés.

**Le format d'erreur n'est pas normalisé.** La documentation produit déjà des exceptions métier avec
des sémantiques HTTP précises : `DossierFieldValidationException` en 400 avec détail par champ
(`04`, l. 402), `WorkflowAlreadyRunningException` (`04`, l. 217), `WebhookAuthenticationException`
(`06`, l. 363), `TemplateNotFoundException` (`08`, l. 111), 403 pour un claim absent ou ne
correspondant pas au tenant de l'instance (`02`, l. 76, mis à jour par la décision n° 13), 503 pour
un tenant en migration (`02`, l. 242). Il manque le format pivot — `ProblemDetail` (RFC 7807, natif
Spring 6) est le candidat naturel — et le `@RestControllerAdvice` qui l'applique.

À décider également : génération OpenAPI (springdoc) et son statut de contrat vis-à-vis du frontend.

### 25. Modèle physique de données consolidé

Une dizaine de tables métier, deux index uniques partiels critiques (`04`, l. 210 ; `08`, l. 63), la
table `outbox_event`, la table `dossier_type_acl` (décision n° 20), le stockage des DEK chiffrées (`06`,
l. 217) et les tables `ACT_*` de Flowable
coexistent dans le schéma de chaque tenant. Aucun document ne consolide cette vue.

**Mise à jour depuis la décision n° 13.** Le `TenantRegistry` et la table des tenants qu'il
reflétait n'ont plus le rôle de sécurité applicative qu'ils avaient dans le modèle abandonné (voir
section 3) ; leur rôle éventuel se limite désormais à un annuaire d'infrastructure pour la couche de
routage/provisioning (quelle instance sert quel tenant, sur quel hôte/port). `platform.
tenant_migration_status` (décision n° 10) reste pertinent en version simplifiée : chaque instance
n'y écrit plus que sa propre ligne, sans coordination inter-schéma.

Livrables attendus : un schéma consolidé (ERD ou DDL) par tenant, et les changelogs Liquibase
correspondants, exécutés au démarrage de chaque instance dédiée.

### 26. Concurrence sur le dossier et unicité de la référence

- **Écritures concurrentes.** Un `ServiceTask` et un utilisateur peuvent modifier le même `Dossier`
  simultanément ; aucun verrouillage optimiste (`@Version`) n'est prévu, ni de stratégie de
  résolution de conflit.
- **Unicité de `reference`.** Aucune contrainte d'unicité n'est documentée, et le `nextSequence(...)`
  de `SequentialReferenceGenerator` (`04`, l. 330) n'est pas spécifié. Une séquence PostgreSQL (par
  tenant et par type de dossier) garantit l'absence de collision, y compris si un tenant est scalé
  sur plusieurs répliques (décision n° 13) — à créer dynamiquement à l'onboarding, ce qui rejoint le
  point 25.
- **Bug dans l'extrait.** `String.format("%s-%s-%tY-%06d", …, Instant.now(), seq)` échoue à
  l'exécution : la conversion `%tY` interroge le champ `YEAR`, que `Instant` ne supporte pas. Au-delà
  de la correction, cela pose la vraie question : **quel fuseau horaire fait référence** pour le
  millésime d'une numérotation annuelle, et que se passe-t-il au passage d'année.

### 27. Versions figées, structure du build et packaging du frontend

**Décidé** — voir section 3. Java 25 LTS · Spring Boot 4.1.x · Flowable 8.0.x ·
`flying-saucer-pdf` 10.4.x · jsoup 1.22.x · Keycloak 26.7.x ; build mono-module Maven ; frontend
embarqué dans `static/` de chaque instance (confirme `SameSite=Lax` de la décision n° 15).

### 28. Stratégie de test

La documentation nomme déjà des tests indispensables, sans définir ni outillage, ni seuils, ni
exécution en CI :

- test de charge de dimensionnement des verrous Flowable (`05`, l. 246) ;
- migration Liquibase par tenant, au démarrage de chaque instance dédiée.

**Mise à jour depuis la décision n° 13.** Le test d'intégration d'étanchéité du `search_path`
explicitement demandé par `02-multi-tenant.md` (l. 194 — ouvrir/fermer des connexions pour deux
tenants en boucle serrée et vérifier l'absence de fuite) devient sans objet : une instance ne dessert
jamais plus d'un schéma, donc le scénario qu'il visait à couvrir ne peut plus se produire. C'est un
allègement net de l'effort de test, à ne pas reconduire par réflexe lors de la réécriture de `02`.

À compléter par : **ArchUnit** pour rendre exécutables la règle de dépendance et la convention
`port/in` / `usecase` de la décision n° 9 ; tests du relais outbox (rejeu, idempotence, crash entre
publication et marquage) ; tests de la résolution en cascade des templates ; tests de la règle de
concurrence « un seul workflow actif » en parallèle réel. Enfin, décider de la chaîne CI et des
seuils de couverture.

### 29. Observabilité

Aucun document ne traite le sujet. Besoins minimaux compte tenu de l'architecture :

- **Logs structurés** avec `dossierId` et `processInstanceId` en MDC — le `tenantId`, devenu une
  constante de configuration par instance (décision n° 13), peut être injecté une fois au démarrage
  plutôt que résolu par requête, ce qui simplifie ce point par rapport au modèle mutualisé initial.
- **Métriques** (Micrometer) : âge du plus ancien événement non traité de l'outbox — c'est
  l'indicateur de santé n° 1 du déclenchement ; jobs Flowable en échec ; durée des rendus PDF, dont
  `07` donne des ordres de grandeur (l. 99-107) sans moyen de les vérifier en production ; webhooks
  sortants en dead-letter (`09`, l. 52-55) ; nombre d'`SseEmitter` actifs.
- **Traçabilité des accès en lecture**, laissée ouverte par `12` (l. 79-81) et potentiellement exigée
  par les clients assurance et secteur public.

### 30. Durcissement des points d'entrée exposés

- **Rate limiting** sur `/api/webhooks/**`, endpoint public capable de créer des dossiers en batch.
- **Taille du corps de requête** : `@RequestBody byte[] rawBody` (`06`, l. 380) charge intégralement
  la requête en mémoire, sans borne — la lecture du corps brut est nécessaire à la vérification HMAC,
  mais elle doit être plafonnée.
- **Pièces jointes** : taille maximale, types autorisés, analyse antivirale. `12` traite la
  sensibilité des données mais pas le risque du fichier malveillant.
- **SSRF** : le connecteur `http-rest` en rôle SERVICE et les webhooks sortants appellent des URL
  fournies par le tenant. Sans allowlist ou blocage des plages privées, un tenant peut faire émettre
  au serveur des requêtes vers les services internes du cluster ou vers l'IMDS du cloud provider.
- **Fuite de secrets** : les champs `PASSWORD` déchiffrés transitent dans `ConnectorInstanceData.config`
  (`06`, l. 179) et peuvent se retrouver dans un log de debug ou une réponse d'API d'administration.

### 31. Découpage en lots d'implémentation

Il n'existe aucun plan de développement : ni lots, ni séquencement, ni critères d'acceptation. La
documentation décrit une cible, pas un chemin. Le support de ce découpage reste à choisir
(`docs/ai/planning` n'est pas utilisé à ce stade).

---

## 7. Autres écarts mineurs

Sans valeur de point à trancher, mais à corriger lorsque le document concerné sera repris :

- `09-notifications.md`, l. 70 : `emitters.put(user.getSubject(), emitter)` n'autorise qu'un seul
  flux SSE par utilisateur — le second onglet écrase le premier silencieusement.
- `10-visualisation-workflow.md`, l. 119-122 : `reassignTask(...)` reçoit un `tenantId` qu'il n'utilise
  pas. À supprimer du contrat ou à exploiter pour une vérification explicite.
- `10-visualisation-workflow.md`, l. 90-91 : les activités en erreur sont détectées par
  `deleteReason.startsWith("error")` — heuristique fragile sur une chaîne libre de Flowable.
- `07-generation-documents.md`, l. 170-173 : `DocumentStoragePort.store(byte[])` ne transporte ni
  tenant, ni nom, ni type MIME, ce qui rend le cloisonnement du stockage impossible à exprimer.
- `09-notifications.md`, l. 29-37 : `switch` sur `NotificationChannel` sans `default` ni scellement,
  alors que la décision n° 6 a retenu le scellement précisément pour cette garantie.

---

## 8. Correspondance revue ↔ registre

| Point | Sujet | Nature | Documents impactés |
|---|---|---|---|
| 13 | Modèle de déploiement multi-tenant — instance dédiée | **Décidé et répercuté** (section 3) | `02`, `05`, `06`, `09`, `10`, `11`, `README` |
| 14 | Tenant des appels non authentifiés — routage par chemin + `client_credentials` | **Décidé et répercuté** (section 3) | `02`, `03`, `06`, `12` |
| 15 | Session BFF — CSRF, logout, session par tenant | **Décidé et répercuté** (section 3) | `03`, `11` |
| 16 | Outbox vs. runtime synchrone | **Décidé et répercuté** (section 3) — option B | `04`, `05`, `06` |
| 17 | Entités JPA dans le domaine — règle appliquée partout | **Décidé et répercuté** (section 3) | `01`, `04`, `05`, `08`, `10` |
| 18 | Résidus et références orphelines | **Décidé et répercuté** (section 3) — c1+e1+f1 | `04`, `06`, `08`, `12` |
| 19 | Granularité RBAC | Produit | `12` |
| 20 | Contrôle accès par type de dossier | **Décidé et répercuté** (section 3) — A2+B2(type)+C1 | `04`, `12` |
| 21 | Rétention par `DossierType` | Conformité | `04`, `12` |
| 22 | Purge PostgreSQL / MinIO | Conformité | `07`, `12` |
| 23 | Champs sensibles | Conformité | `04`, `12` |
| 24 | Contrat d'API | Complément | tous |
| 25 | Modèle physique consolidé | Complément | `02`, `04`, `05`, `06`, `08` |
| 26 | Concurrence et référence dossier | Complément | `04` |
| 27 | Versions, build, packaging front | **Décidé et répercuté** (section 3) | `00`, `01`, `03`, `05`, `07`, `11`, `README` |
| 28 | Stratégie de test | Complément | `01`, `02`, `05` |
| 29 | Observabilité | Complément | — |
| 30 | Durcissement des points d'entrée | Complément | `06`, `09`, `12` |
| 31 | Découpage en lots | Processus | — |

---

## 9. Séquencement recommandé

| Étape | Points | Justification |
|---|---|---|
| **Avant le premier commit applicatif** | — | Socle technique tranché (décision n° 27). Chaîne de déclenchement tranchée (décision n° 16). |
| **Avant le premier trigger de bout en bout** | 25, 26 | Modèle physique / concurrence référence — nécessaires dès que la chaîne « webhook → dossier → workflow » est câblée. |
| **Avant le premier use case de lecture** | 19 | Contrôle ressource tranché (décision n° 20). Affiner la granularité des rôles (point 19) avant d'exposer la surface REST humaine. |
| **Au fil du développement** | 24, 28, 29, 30 | À intégrer dès les premiers endpoints plutôt qu'en fin de parcours, en particulier les tests ArchUnit qui perdent leur intérêt s'ils arrivent après la dérive. |
| **Avant la mise en production** | 21, 22, 23 | Conformité — sans impact sur la structure du code si les points 24 et 25 les ont anticipés. |
| **En parallèle** | 31 | Le découpage en lots peut être produit dès maintenant — les points bloquants du socle sont tranchés. |
