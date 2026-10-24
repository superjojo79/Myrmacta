# Realm Keycloak d'un tenant FlowForge (TODO §103, D-BEAUTH-REALM, D-BEAUTH-PROVISION) — appelé par
# scripts/provision-tenant.sh --realm-per-tenant. Déclaratif et sans secret : le script <tenant>.keycloak.sh généré
# l'applique, puis pose les identifiants des fournisseurs depuis son environnement.
#
# Arguments : $tenant, $base (URL publique, sans / final), $mode (both | idp-only), $flowId (UUID du flux idp-only),
# $idps (tableau JSON : "fas", "itsme"), $claims (objet JSON : noms des claims prénom, nom, e-mail par fournisseur).

def attribute($name; $display; $view; $edit):
  {name: $name, displayName: $display, validations: {length: {max: 255}},
   permissions: {view: $view, edit: $edit}, multivalued: false};

def profile:
  {attributes: [
     {name: "username", displayName: "${username}",
      validations: {length: {min: 3, max: 255}, "username-prohibited-characters": {}, "up-username-not-idn-homograph": {}},
      permissions: {view: ["admin", "user"], edit: ["admin", "user"]}, multivalued: false},
     # FAS ne garantit pas d'e-mail : un compte créé par un fournisseur ne doit pas être bloqué sur un écran de profil.
     {name: "email", displayName: "${email}", validations: {email: {}, length: {max: 255}},
      permissions: {view: ["admin", "user"], edit: ["admin", "user"]}, multivalued: false},
     attribute("firstName"; "${firstName}"; ["admin", "user"]; ["admin", "user"]),
     attribute("lastName"; "${lastName}"; ["admin", "user"]; ["admin", "user"]),
     # Réservé à l'admin : sinon un utilisateur changerait de tenant depuis sa console de compte (DEPLOIEMENT.md §5).
     attribute("tenant_id"; "Tenant FlowForge"; ["admin"]; ["admin"])],
   groups: [{name: "user-metadata", displayHeader: "User metadata",
             displayDescription: "Attributes, which refer to user metadata"}]};

def mapper($name; $mapper; $config):
  {name: $name, protocol: "openid-connect", protocolMapper: $mapper,
   config: ({"jsonType.label": "String", "id.token.claim": "true", "access.token.claim": "true",
             "userinfo.token.claim": "true"} + $config)};

def bff:
  {clientId: "mon-app", name: "FlowForge BFF", enabled: true, protocol: "openid-connect", publicClient: false,
   clientAuthenticatorType: "client-secret", standardFlowEnabled: true, directAccessGrantsEnabled: false,
   serviceAccountsEnabled: false, implicitFlowEnabled: false,
   redirectUris: ["\($base)/\($tenant)/*"], webOrigins: [$base],
   attributes: {"post.logout.redirect.uris": "\($base)/\($tenant)/*"},
   protocolMappers: [
     mapper("realm roles (ID token)"; "oidc-usermodel-realm-role-mapper";
            {"claim.name": "realm_access.roles", multivalued: "true"}),
     mapper("tenant_id"; "oidc-usermodel-attribute-mapper"; {"user.attribute": "tenant_id", "claim.name": "tenant_id"})]}
  + (if $mode == "idp-only" then {authenticationFlowBindingOverrides: {browser: $flowId}} else {} end);

def provider($alias):
  {alias: $alias,
   displayName: (if $alias == "fas" then "eID, itsme et autres (FAS)" else "itsme" end),
   providerId: "oidc", enabled: true, trustEmail: false, storeToken: false, linkOnly: false,
   firstBrokerLoginFlowAlias: "first broker login",
   # URL et validation des signatures posées par <tenant>.keycloak.sh depuis la découverte du fournisseur : Keycloak
   # refuse « validateSignature » sans URL JWKS.
   config: {clientAuthMethod: "private_key_jwt", clientAssertionSigningAlg: "RS256",
            pkceEnabled: "true", pkceMethod: "S256", syncMode: "IMPORT", defaultScope: "openid"}};

# A2, A5 : prénom, nom et e-mail seulement. Ni NRN ni tenant_id ne sont posés sur le compte d'un agent.
def providerMappers($alias):
  [["prénom", "firstName", $claims[$alias].given], ["nom", "lastName", $claims[$alias].family],
   ["e-mail", "email", $claims[$alias].email]]
  | map({name: .[0], identityProviderAlias: $alias, identityProviderMapper: "oidc-user-attribute-idp-mapper",
         config: {claim: .[2], "user.attribute": .[1], syncMode: "IMPORT"}});

def execution($authenticator; $requirement; $priority):
  {authenticator: $authenticator, requirement: $requirement, priority: $priority,
   autheticatorFlow: false, userSetupAllowed: false};
def subflow($alias; $requirement; $priority):
  {flowAlias: $alias, requirement: $requirement, priority: $priority, autheticatorFlow: true, userSetupAllowed: false};
def flow($alias; $description; $executions):
  {alias: $alias, description: $description, providerId: "basic-flow", topLevel: false, builtIn: false,
   authenticationExecutions: $executions};

# D-BEAUTH-IDP-ONLY : les fournisseurs pour tous ; le mot de passe et l'OTP pour les seuls comptes « secours ».
def idpOnlyFlows:
  [flow("browser-fournisseurs"; "FlowForge : fournisseurs d'identité, mot de passe réservé aux comptes de secours"; [
     execution("auth-cookie"; "ALTERNATIVE"; 10),
     execution("identity-provider-redirector"; "ALTERNATIVE"; 20),
     subflow("browser-fournisseurs formulaire"; "ALTERNATIVE"; 30)]) + {id: $flowId, topLevel: true},
   flow("browser-fournisseurs formulaire"; "Identifiant, puis refus ou secours"; [
     execution("auth-username-form"; "REQUIRED"; 10),
     subflow("browser-fournisseurs refus"; "CONDITIONAL"; 20),
     subflow("browser-fournisseurs secours"; "CONDITIONAL"; 30)]),
   flow("browser-fournisseurs refus"; "Compte ordinaire : le mot de passe est refusé"; [
     execution("conditional-user-role"; "REQUIRED"; 10) + {authenticatorConfig: "pas-secours"},
     execution("deny-access-authenticator"; "REQUIRED"; 20)]),
   flow("browser-fournisseurs secours"; "Compte de secours : mot de passe et OTP"; [
     execution("conditional-user-role"; "REQUIRED"; 10) + {authenticatorConfig: "secours"},
     execution("auth-password-form"; "REQUIRED"; 20),
     execution("auth-otp-form"; "REQUIRED"; 30)])];

{
  realm: "flowforge-\($tenant)",
  displayName: "FlowForge — \($tenant)",
  enabled: true,
  sslRequired: "external",
  registrationAllowed: false,
  resetPasswordAllowed: ($mode != "idp-only"),
  bruteForceProtected: true,
  clients: [bff],
  roles: {realm: ([{name: "TENANT_ADMIN"}, {name: "WORKFLOW_ADMIN"}, {name: "DOSSIER_MANAGER"}, {name: "DOSSIER_VIEWER"}]
                  + (if $mode == "idp-only" then [{name: "secours", description: "Compte de secours : mot de passe et OTP en mode fournisseurs seuls"}] else [] end))},
  identityProviders: [$idps[] | provider(.)],
  identityProviderMappers: [$idps[] | providerMappers(.)[]],
  components: {"org.keycloak.userprofile.UserProfileProvider": [
    {providerId: "declarative-user-profile", subComponents: {}, config: {"kc.user.profile.config": [profile | tojson]}}]}
}
+ (if $mode == "idp-only" then
     {authenticationFlows: idpOnlyFlows,
      authenticatorConfig: [{alias: "pas-secours", config: {condUserRole: "secours", negate: "true"}},
                            {alias: "secours", config: {condUserRole: "secours", negate: "false"}}]}
   else {} end)
