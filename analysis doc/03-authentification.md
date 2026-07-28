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

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok().build();
    }
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

## Lien avec le multi-tenant

Le claim `tenant_id` est résolu depuis le token OIDC dans le filtre
`TenantResolutionFilter` — voir [02-multi-tenant.md](02-multi-tenant.md).
