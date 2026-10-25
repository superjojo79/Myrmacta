# Authentification — OAuth2/OIDC + pattern BFF

> Le pattern BFF est intégré directement dans Spring Boot.  
> Le browser ne manipule jamais de token JWT.  
> Le provider OIDC est interchangeable via configuration.

---

### Principe

Le pattern **BFF (Backend For Frontend)** est intégré directement dans l'application Spring Boot — pas de service séparé. Spring Boot joue simultanément le rôle de BFF pour la SPA Vue et de Resource Server pour ses APIs.

```
Vue 3 (browser)
    │  cookie httpOnly (session Spring)
    ▼
Spring Boot — BFF + API        ←→   Authorization Server (OAuth2/OIDC)
    │  access_token JWT (serveur → serveur)
    ▼
  Flowable · PDF · Données
```

Le browser ne manipule **jamais** de token JWT. Il communique avec le BFF via un cookie de session `httpOnly` — inaccessible au JavaScript, protégé contre XSS.

### Pourquoi BFF plutôt que JWT direct dans la SPA

| Approche | XSS | Token exposé | Complexité |
|---|---|---|---|
| JWT en `localStorage` | ❌ Vulnérable | ❌ Oui | Faible |
| JWT en mémoire Pinia | ✅ | ✅ Non | Moyenne |
| **BFF + cookie httpOnly** | ✅ | ✅ Non | Faible |

### Provider OAuth2/OIDC — choix ouvert

L'implémentation Spring Security OAuth2 est **provider-agnostique** via la configuration `issuer-uri`. Le provider est interchangeable sans modifier le code :

| Provider | Usage typique |
|---|---|
| **Keycloak** (self-hosted) | Développement, on-premise, contrôle total |
| **Auth0** | SaaS, démarrage rapide |
| **Microsoft Entra ID** | Clients entreprise Microsoft |
| **Google** | Authentification grand public |

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:                          # ou auth0, azure, google…
            client-id: mon-app
            client-secret: ${OIDC_SECRET}
            scope: openid, profile, email
            authorization-grant-type: authorization_code
        provider:
          keycloak:
            issuer-uri: ${OIDC_ISSUER_URI}  # URL du provider — seul paramètre à changer
```

### Flux d'authentification complet

```
1. SPA appelle GET /api/auth/me au démarrage
2. Pas de session → Spring redirige vers le provider OAuth2
3. User s'authentifie sur le provider
4. Provider redirige vers /login/oauth2/code/{registrationId}
5. Spring récupère les tokens, crée une session, pose un cookie httpOnly
6. Spring redirige vers /
7. SPA rappelle GET /api/auth/me → reçoit nom, email, rôles
8. Toutes les requêtes suivantes portent le cookie automatiquement
```

### Callback OAuth2 derrière le routage par chemin

Le routage par chemin (décision n° 14, [02-multi-tenant.md](02-multi-tenant.md)) fait que l'URL de
callback varie selon le tenant (`https://example.com/tenant_a/login/oauth2/code/keycloak`,
`https://example.com/tenant_b/login/oauth2/code/keycloak`…). `server.forward-headers-strategy:
framework` (déjà configuré, voir [02-multi-tenant.md](02-multi-tenant.md)) garantit que Spring
calcule ce callback dynamiquement à partir de la requête entrante, préfixe de tenant inclus — aucune
configuration par tenant n'est nécessaire côté application.

Côté Keycloak, le client `mon-app` (partagé par tous les tenants — contrairement au client
`client_credentials`, qui lui est dédié par tenant, voir plus bas) doit accepter ces callbacks
variables. Une entrée avec caractère joker en fin d'URI couvre tous les tenants sans provisioning
supplémentaire à chaque onboarding :

```
Valid Redirect URIs:              https://example.com/*
Valid Post Logout Redirect URIs:  https://example.com/*
```

### Protection CSRF

**Problème.** Le cookie de session étant envoyé automatiquement par le navigateur — y compris sur
une requête déclenchée depuis un site tiers — Spring Security active la protection CSRF par défaut.
Sans configuration explicite, toutes les requêtes mutantes (`POST`/`PUT`/`DELETE`) de la SPA
échoueraient en 403.

**Décision retenue.** Jeton CSRF porté par un cookie lisible en JavaScript (« double submit cookie »),
qu'Axios relit et renvoie automatiquement dans un en-tête :

```java
@Bean
@Order(3)
public SecurityFilterChain bffFilterChain(HttpSecurity http) throws Exception {

    CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfRepository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(true));
    // SameSite=Lax confirmé : frontend embarqué dans static/ (décision n° 27) → même origine.

    http
        .csrf(csrf -> csrf
            .csrfTokenRepository(csrfRepository)
            .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())) // pas de XOR — la SPA lit le cookie brut
        .addFilterAfter(csrfCookieEagerLoadFilter(), BasicAuthenticationFilter.class)
        .oauth2Login(Customizer.withDefaults())
        .logout(logout -> logout
            .logoutUrl("/api/auth/logout")
            .logoutSuccessHandler(oidcLogoutSuccessHandler())
            .invalidateHttpSession(true)
            .deleteCookies("JSESSIONID"))
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
    return http.build();
}

// Depuis Spring Security 6 (toujours vrai sous Boot 4 / Security 7), le CsrfToken est rendu
// de façon paresseuse — le cookie XSRF-TOKEN n'est écrit que si quelque chose lit effectivement
// le token. Une SPA pure ne rend aucun formulaire Thymeleaf qui le lirait : ce filtre force la
// résolution sur chaque requête pour garantir que le cookie existe dès le premier appel.
private OncePerRequestFilter csrfCookieEagerLoadFilter() {
    return new OncePerRequestFilter() {
        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                FilterChain chain) throws ServletException, IOException {
            CsrfToken token = (CsrfToken) req.getAttribute(CsrfToken.class.getName());
            if (token != null) token.getToken(); // force le rendu → écriture du cookie
            chain.doFilter(req, res);
        }
    };
}
```

```yaml
server:
  servlet:
    session:
      cookie:
        same-site: lax   # cookie de session — même hypothèse que le cookie CSRF ci-dessus
        secure: true
```

Côté Vue 3, une fois ces valeurs par défaut posées, Axios lit et renvoie le cookie sans code
supplémentaire à chaque requête :

```javascript
// bootstrap Axios — une fois au démarrage de l'app
axios.defaults.withCredentials = true;
axios.defaults.xsrfCookieName  = 'XSRF-TOKEN';   // valeurs par défaut de CookieCsrfTokenRepository
axios.defaults.xsrfHeaderName  = 'X-XSRF-TOKEN';
```

**Webhooks et API machine-to-machine exemptés par construction.** Plutôt qu'une exemption
(`ignoringRequestMatchers`) ajoutée à la chaîne BFF, les webhooks ont leur propre chaîne de sécurité,
scopée par `securityMatcher` et sans session ni CSRF — sur le même principe que la chaîne
`client_credentials` (voir « Authentification machine-to-machine » plus bas) :

```java
@Bean
@Order(2)
public SecurityFilterChain webhookFilterChain(HttpSecurity http) throws Exception {
    // L'authentification (HMAC ou équivalent) est gérée par WebhookController lui-même
    // (voir 06-connecteurs.md) — cette chaîne garantit seulement l'absence de session et de CSRF.
    http.securityMatcher("/api/webhooks/**")
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(CsrfConfigurer::disable);
    return http.build();
}
```

Les trois chaînes de sécurité (machine-to-machine, webhooks, BFF) sont partitionnées par
`securityMatcher` et ordonnées explicitement (`@Order`) — une requête ne traverse jamais qu'une seule
d'entre elles.

### Déconnexion — RP-initiated logout

**Problème.** `session.invalidate()` seul ferme la session Spring mais laisse la session ouverte
côté Keycloak — la redirection de la SPA vers `/oauth2/authorization/keycloak` en cas de perte de
session reconnecterait alors l'utilisateur silencieusement, sans qu'il ait besoin de ressaisir ses
identifiants.

**Décision retenue.** `OidcClientInitiatedLogoutSuccessHandler`, qui appelle l'`end_session_endpoint`
du provider avec l'`id_token` de l'utilisateur (`id_token_hint`), fermant la session Keycloak en plus
de la session locale :

```java
@Bean
public LogoutSuccessHandler oidcLogoutSuccessHandler(ClientRegistrationRepository clients) {
    var handler = new OidcClientInitiatedLogoutSuccessHandler(clients);
    handler.setPostLogoutRedirectUri("{baseUrl}/"); // {baseUrl} respecte le préfixe /tenant_x du contexte
    return handler;
}
```

Ce handler est câblé dans la chaîne BFF ci-dessus (`.logout(...)`) ; l'`AuthController.logout()`
manuel (`session.invalidate()`) n'est plus nécessaire — la déconnexion devient déclarative, entièrement
gérée par la configuration Spring Security.

### Session partagée entre pods — non requise par défaut

Le sujet ne concerne que le cas où un tenant est scalé sur plusieurs répliques (décision n° 13,
[02-multi-tenant.md](02-multi-tenant.md#modèle-de-déploiement--une-instance-dédiée-par-tenant)) : une
session HTTP en mémoire ne survit pas au routage vers une autre réplique du même tenant. Ce n'est pas
un prérequis de la V1 — à réexaminer (Spring Session JDBC/Redis ou affinité de réplique) uniquement
si un tenant précis est scalé.

### Dépendances Maven

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

### Endpoint BFF exposé à la SPA

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal OidcUser user) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(Map.of(
            "name",  user.getFullName(),
            "email", user.getEmail(),
            "roles", user.getAuthorities()
        ));
    }

    // Pas de méthode /logout manuelle — la déconnexion est déclarative, voir
    // « Déconnexion — RP-initiated logout » ci-dessus.
}
```

### Initialisation côté Vue 3 (Pinia)

```javascript
// stores/auth.js
export const useAuthStore = defineStore('auth', {
    state: () => ({ user: null, authenticated: false }),
    actions: {
        async init() {
            try {
                const { data } = await axios.get('/api/auth/me',
                    { withCredentials: true });
                this.user = data;
                this.authenticated = true;
            } catch {
                window.location.href = '/oauth2/authorization/keycloak';
            }
        }
    }
});
```

---

## Authentification machine-to-machine — `client_credentials`

Le rôle `API_SERVICE` (voir [12-autorisation-conformite.md](12-autorisation-conformite.md)) permet
d'appeler certains endpoints — typiquement `/api/connectors/{id}/execute` (voir
[06-connecteurs.md](06-connecteurs.md)) — sans session utilisateur ni cookie BFF. Ces appels portent
un token JWT obtenu par un flux OAuth2 `client_credentials`, distinct du flux `authorization_code`
utilisé par la SPA.

**Un client `client_credentials` dédié par tenant** est provisionné côté Keycloak en même temps que
le reste de l'onboarding (schéma, instance, routage — voir
[02-multi-tenant.md](02-multi-tenant.md)), avec un protocol mapper attachant un claim `tenant_id`
fixe (celui de ce tenant) à tout token qu'il émet. Un rôle client mappé sur `API_SERVICE` porte
l'autorisation.

**Une chaîne de sécurité distincte**, scopée aux seuls endpoints machine-to-machine, valide ces
tokens comme un *resource server* JWT ordinaire — sans jamais passer par la session ni le cookie du
BFF. Elle prend le rang `@Order(1)`, avant les chaînes webhook et BFF présentées dans « Protection
CSRF » ci-dessus :

```java
@Bean
@Order(1)
public SecurityFilterChain m2mFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/api/connectors/*/execute")
        .authorizeHttpRequests(auth -> auth
            .anyRequest().hasAuthority("SCOPE_api_service"))
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(CsrfConfigurer::disable); // pas de cookie de session sur cette chaîne — CSRF sans objet
    return http.build();
}
```

**La même vérification d'identité que le flux humain s'applique**, réutilisant `TenantProperties` :
le claim `tenant_id` du token `client_credentials` doit correspondre au tenant configuré de cette
instance, sinon rejet — exactement la règle du `TenantResolutionFilter` (voir
[02-multi-tenant.md](02-multi-tenant.md)), appliquée ici via un `JwtAuthenticationConverter`
personnalisé plutôt qu'un filtre de session.

```java
@Component
@RequiredArgsConstructor
public class TenantClaimJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private final TenantProperties tenantProperties;
    private final JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String claimedTenantId = jwt.getClaimAsString("tenant_id");
        if (!tenantProperties.id().equals(claimedTenantId)) {
            throw new InvalidBearerTokenException("tenant_id claim ne correspond pas à cette instance");
        }
        return new JwtAuthenticationToken(jwt, authorities.convert(jwt));
    }
}
```

---

## Lien avec le multi-tenant

Le claim `tenant_id` est résolu depuis le token OIDC dans le filtre
`TenantResolutionFilter` — voir [02-multi-tenant.md](02-multi-tenant.md). Le routage HTTP vers cette
instance (chemin `/{tenantId}/...`, reverse-proxy statique, `server.forward-headers-strategy` pour le
calcul correct des `redirect_uri` OAuth2 derrière le proxy) est également documenté dans
[02-multi-tenant.md](02-multi-tenant.md).

## Implémentation — en-têtes HTTP de défense (lot A4, PR #128, TODO §64)

*Synthèse ajoutée lors de la remédiation (TODO §95) : la fonctionnalité n'était décrite que dans ses documents de
travail ([requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-20-feature-headers-http-globaux.md),
[implementation](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-09-20-feature-headers-http-globaux.md)).*

- **Portée :** les quatre chaînes de `SecurityConfig` (actuator, M2M, webhooks/callbacks, BFF) reçoivent le même
  paquet d'en-têtes par un customizer par chaîne (`SecurityHeadersCustomizer`), pas par un filtre global qui
  raterait le port actuator.
- **Contenu :** `Content-Security-Policy` (constante unique `CONTENT_SECURITY_POLICY`, partagée par le code et les
  tests, compatible bpmn-js, dmn-js et PrimeVue), `X-Frame-Options`, `X-Content-Type-Options: nosniff` et
  `Referrer-Policy`. Le preview HTML de document garde sa CSP propre (`default-src 'none'; sandbox`, TODO §57).
- **HSTS :** `max-age=31536000; includeSubDomains`, sans `preload`, hors profils `local`/`test`, et seulement si la
  requête est HTTPS **et** si le pair TCP immédiat est un proxy de confiance
  (`APP_SECURITY_HEADERS_TRUSTED_PROXY_CIDRS`, défaut loopback + RFC1918).
- **Rapports CSP :** collecte anonyme sur `/api/security/csp-reports`, champs en liste blanche, journalisés
  tronqués, réponse toujours 204 (D-CSP-REPORTING), débit borné par pair TCP.
- **Interrupteur :** `APP_SECURITY_HEADERS_ENABLED=false` coupe le paquet applicatif (les défauts Spring restent) ;
  hors profils `local`/`test`, le démarrage est refusé (`SecurityHeadersEnvironmentPostProcessor`, TODO §64).
- **Tests :** `SecurityHeadersCustomizerTest`, `SecurityHeadersApiTest`, `SecurityHeadersHstsApiTest`,
  `FrontendCspLiteralConsistencyTest` ; Playwright `E2E-HDR-1/2`.

## Implémentation — fournisseurs d'identité belges (TODO §104, issue #210)

- **Agents.** FAS (BOSA : eID, itsme, eIDAS) et itsme en direct sont des fournisseurs d'identité OIDC du realm Keycloak du
  tenant (identity brokering) : le BFF voit toujours un ID token du realm, avec `tenant_id` et `realm_access.roles`.
  Assertion `private_key_jwt` signée avec la clé du realm, PKCE. Les mappers des fournisseurs ne posent que le prénom, le
  nom et l'e-mail : jamais le NRN (A5), jamais le `tenant_id` (D-BEAUTH-FIRST-LOGIN).
- **Première connexion.** Flux `first broker login` standard : compte créé sans `tenant_id` ni rôle (refusé jusqu'à
  validation par un admin), ou rattachement à un compte existant après ré-authentification (même `sub`, mêmes droits).
- **Mot de passe** (D-BEAUTH-PASSWORD, D-BEAUTH-IDP-ONLY). `both` (défaut) : formulaire de mot de passe et boutons des
  fournisseurs. `idp-only` : flux `browser-fournisseurs` attaché au seul client BFF (`authenticationFlowBindingOverrides`) ;
  le formulaire d'identifiant ne mène au mot de passe et à l'OTP que pour un compte de rôle `secours`, les autres sont
  refusés (`Deny Access`). La console d'administration du realm garde le flux standard.
- **Annuaire (A6, défaut corrigé).** `KnownUserRecordingSuccessHandler` appliquait l'enregistrement dans l'annuaire avant
  toute vérification du `tenant_id` ; il partage désormais la règle de `TenantResolutionFilter` (`TenantMembership`) :
  pas d'enregistrement, session fermée et 403 pour un utilisateur qui n'appartient pas au tenant.
- **Provisionnement** (D-BEAUTH-PROVISION) : `<tenant>.realm.json` (gabarit `scripts/tenant-realm.jq`) et
  `<tenant>.keycloak.sh` ; recette sur un vrai Keycloak par `scripts/keycloak-realm-smoke.sh` (D-BEAUTH-KC-SMOKE).
- **Tiers du portail** : voir [26-portail-externe.md §5](26-portail-externe.md#5-authentification-forte-du-tiers-todo-103).
- **Tests** : `KnownUserRecordingSuccessHandlerTest`, `TenantResolutionFilterTest`, `ProvisionTenantRealmScriptTest`, et la
  recette Keycloak (16 vérifications).

