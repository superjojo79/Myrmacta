# §104 — Authentification belge (FAS, itsme) des agents et des tiers du portail

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] livré (issue [#210](https://github.com/superjojo79/FlowForge/issues/210)) : lots A (Keycloak, agents), B (portail,
serveur), C (page du portail) et D (documentation, revue adversariale). Reste la recette contre les vrais FAS et itsme,
qui dépend des contrats : suivie dans l'issue [#192](https://github.com/superjojo79/FlowForge/issues/192).

**Constat.** FlowForge ne proposait ni eID, ni itsme, ni CSAM ; le realm Keycloak n'avait aucun fournisseur d'identité.
Les communes l'exigent en premier (marché de Schaerbeek, échéance 05/11/2026). Les agents se connectent au BFF par OIDC
(Keycloak) ; les tiers du portail n'ont qu'un lien magique (§68).

**Décisions tranchées par l'utilisateur (phases 1 à 3, 2026-10-24).**

- **D-BEAUTH-AUDIENCE** — agents et tiers du portail dans la même feature. Écarté : agents seuls ; citoyens qui déposent
  une demande (#188).
- **D-BEAUTH-PROVIDERS** — FAS (BOSA : eID, itsme, eIDAS) et itsme en direct, activables par tenant. Écarté : FAS seul ;
  fournisseur OIDC générique sans gabarit.
- **D-BEAUTH-REALM** — un realm Keycloak par tenant (`flowforge-<tenant>`), modèle recommandé en production : clés,
  page de connexion et comptes propres. Activé par `--realm-per-tenant` ; sans l'option, l'instance reste sur le realm
  partagé, et aucune instance existante n'est migrée d'office. Écarté : realm partagé avec un alias de
  fournisseur par tenant (clés communes à toutes les communes).
- **D-BEAUTH-FIRST-LOGIN** — première connexion par un fournisseur : compte créé **sans `tenant_id` ni rôle**, refusé
  par FlowForge (403) jusqu'à validation par un admin ; rattachement à un compte existant par ré-authentification (même
  `sub`). Écarté : compte avec `tenant_id` sans rôle (un inconnu entrait, sans droit) ; pré-enregistrement par NRN
  (extension Keycloak) ; compte désactivé.
- **D-BEAUTH-PASSWORD** — connexion par mot de passe réglable par tenant : `both` (défaut) ou `idp-only`. Sans
  `--realm-per-tenant`, rien ne change.
- **D-BEAUTH-IDP-ONLY** — en `idp-only`, flux dédié au client BFF : fournisseurs pour tous, mot de passe et OTP pour les
  seuls comptes de rôle `secours` (qui administrent FlowForge si FAS est en panne). Écarté : comptes sans mot de passe
  (simple convention) ; redirection automatique (incompatible avec deux fournisseurs) ; aucun mot de passe vers
  FlowForge. Écart accepté : l'identifiant saisi révèle si le compte est un compte de secours.
- **D-BEAUTH-PROVISION** — `provision-tenant.sh --realm-per-tenant` écrit `<tenant>.realm.json` (déclaratif, sans
  secret) et `<tenant>.keycloak.sh` (idempotent : création, ou import partiel qui conserve l'existant ; secrets et URL
  des fournisseurs depuis l'environnement). Écarté : `kcadm` seul ; keycloak-config-cli.
- **D-BEAUTH-KC-SMOKE** — preuve sur un vrai Keycloak : `scripts/keycloak-realm-smoke.sh` (distribution zip, sans
  Docker), plus des tests de structure du JSON. Écarté : IT Testcontainers (jamais exécuté sans Docker, #219).
- **D-BEAUTH-VALIDATION** — critère de fin : tests et démo contre un fournisseur simulé (`fas-simulateur`) ; recette contre
  les vrais FAS et itsme dans #192.

**Décisions du portail (tranchées par l'utilisateur, phases 1 à 3).**

- **D-BEAUTH-PORTAL-CLIENT** — l'instance est elle-même cliente OIDC de FAS et d'itsme pour le portail, sans Keycloak :
  aucun compte n'est créé pour un tiers ; clé `private_key_jwt` de l'instance publiée en JWKS. Conséquence : deux clients
  à enregistrer par fournisseur et par tenant (Keycloak pour les agents, l'instance pour le portail). Écarté : realm
  « portail » avec utilisateurs transitoires (fonction expérimentale de Keycloak) ; realm « portail » à comptes purgés.
- **D-BEAUTH-PREVIEW** — avant l'authentification, le porteur du lien ne voit que le nom de la tâche et la référence du
  dossier ; dépôt de pièce et soumission refusés. Écarté : écran totalement neutre ; contexte visible.
- **D-BEAUTH-NRN-FIELD** — la tâche désigne un champ d'identité `STRING` sensible (`flowable:portalIdentityField`) :
  vide, le NRN vérifié y est écrit à la soumission ; rempli, il est attendu et un écart donne un refus neutre. Champ
  inutilisable à l'émission : lien `NOT_SENT` (`INVALID_IDENTITY_FIELD`). Écarté : NRN seulement tracé ; NRN conservé
  chiffré hors du dossier ; deux réglages séparés.
- **D-BEAUTH-LOA** — niveaux eIDAS substantiel ou élevé ; une identité sans NRN est refusée si un NRN est attendu.
  Écarté : élevé seulement ; niveau réglable par tâche.
- **D-BEAUTH-ACTIVATION** — `app.portal.strong-auth.enabled`, désactivé par défaut ; un BPMN qui exige l'authentification
  forte est refusé au déploiement s'il est éteint. Écarté : actif dès qu'un fournisseur existe.
- **D-BEAUTH-STORE** — tables `portal_identity_attempt` et `portal_link_identity` (changeSet `038`), supprimées avec le
  lien. Écarté : jetons signés côté navigateur ; colonnes de `portal_link`.
- **D-BEAUTH-TTL** — identité vérifiée valable 60 minutes, réglable, plafond 4 heures. Écarté : 30 minutes ; durée du
  lien.
- **D-BEAUTH-PROVIDER-CHOICE** — un bouton par fournisseur configuré, dans l'ordre de l'instance. Écarté : choix par
  tâche ; un seul fournisseur.
- **D-BEAUTH-PROOF** (sans arbitrage) — au retour du fournisseur, une preuve d'identité (256 bits, hachée en base) est
  remise dans le fragment d'URL et remplace ensuite le jeton (`X-Portal-Identity`) : aucun stockage navigateur
  (D-PORTAL-CSRF-SESSION), et une identité vérifiée sur un appareil ne sert pas à un autre porteur du lien. Écarté :
  `sessionStorage`.

**Défaut existant corrigé (A6).** `KnownUserRecordingSuccessHandler` inscrivait l'utilisateur dans l'annuaire des
assignés au retour de connexion, avant toute vérification du `tenant_id` (`TenantResolutionFilter` ne voit que les
requêtes suivantes) : un utilisateur d'un autre tenant du realm partagé entrait dans l'annuaire de l'instance. La règle
est partagée (`TenantMembership`) ; le gestionnaire ne l'inscrit plus, ferme sa session et répond 403.

**Lot A — preuves.** `KnownUserRecordingSuccessHandlerTest` (rouge si l'on retire le contrôle) ;
`ProvisionTenantRealmScriptTest` (structure) ; `scripts/keycloak-realm-smoke.sh` sur Keycloak 26.7.1 : deux applications
successives du script sans erreur, agent connecté par le FAS simulé (`private_key_jwt` signé par la clé du realm),
compte sans `tenant_id`, sans NRN, sans rôle FlowForge ; connexion directe par mot de passe en `both` ; mot de passe
refusé à un compte ordinaire en `idp-only` (`access_denied`) ; compte de secours : mot de passe temporaire changé, OTP
enrôlé, reconnexion par mot de passe et OTP.

**Lot B — portail, serveur.** `flowable:portalIdentityField`, refus au déploiement (E2), émission `NOT_SENT` ;
`PortalAccessResolver` distingue l'accès pour agir (identité exigée) de la vue réduite ; routes publiques
`/api/portal/identity/start`, `/callback` et `/jwks` ; soumission qui recalcule la correspondance sur le dossier relu
(T6) et écrit le NRN par le use case de correction des champs, en contexte système et à version attendue ; trace
conservée sans NRN ni preuve, montrée à l'instructeur avec le lien (nom réservé à qui peut gérer le lien).

Écarts avec la conception, sans arbitrage : la trace reste dans `portal_link_identity` (NRN et preuve effacés à la
soumission) plutôt que dans `task_question_answer`, aujourd'hui en écriture seule ; l'exigence d'identité est relue
dans la définition BPMN de la tâche plutôt que stockée sur le lien ; l'adaptateur OIDC est écrit avec Nimbus plutôt
qu'avec les `ClientRegistration` de Spring Security, pensés pour une session ; le contrôle préalable à un upload ne lit
toujours pas la tâche (F-PORTAL-1) : le porteur du seul jeton d'une tâche à authentification forte peut faire lire un
upload avant son refus, comme tout porteur du lien le pouvait déjà.

**Lot B — preuves.** `PortalIdentityModelTest`, `PortalAccessResolverIdentityTest`, `PortalIdentityUseCasesTest`
(13 cas ; le refus d'un champ modifié depuis l'authentification et celui d'un niveau faible ont été retirés à titre
d'essai : les tests échouent), `PortalStrongAuthPropertiesTest`, `OidcPortalIdentityProviderAdapterTest` (fournisseur en
mémoire : PKCE, `private_key_jwt`, `nonce`, émetteur, audience, expiration, signature, ID token chiffré),
`PortalIdentityRepositoryIT` (PostgreSQL), `PortalStrongAuthApiTest` (bout en bout : vue réduite, preuve, premier
enregistrement, refus d'une autre personne, rejeu, JWKS ; NRN absent des journaux, des variables de process et des
outbox).

**Constats de la recette Keycloak.** Keycloak refuse un fournisseur qui valide les signatures sans URL JWKS : le JSON
n'active la validation qu'avec les URL posées par le script (découverte). Le client BFF généré n'exige pas PKCE, comme le
client du realm de dev : rien ne garantit que le BFF l'envoie.

**Lot C — page du portail.** Écran d'identification (nom de la tâche, référence, un bouton par fournisseur), départ vers
le fournisseur, retour `#identity:<preuve>` lu une fois puis effacé, `#identity-refused` neutre, rappel « Identité vérifiée
: <nom> » ; panneau instructeur avec la trace et la raison `INVALID_IDENTITY_FIELD`. Vitest et Playwright `E2E-PORTAL-6`,
`E2E-PORTAL-7`.

**Revue adversariale (2026-10-25).**
- *Corrigé* — le client du portail ne savait s'authentifier que par `private_key_jwt` : si BOSA n'émet qu'un secret pour
  FAS, le portail ne pouvait pas s'y connecter alors que Keycloak le pouvait. `client_secret_post` quand un secret est
  configuré (`OidcPortalIdentityProviderAdapterTest`).
- *Corrigé* — le rattachement d'un compte existant (A3) n'était pas prouvé : scénario ajouté à la recette Keycloak (même
  `sub`, mêmes rôles après ré-authentification).
- *Vérifié* — `state` à usage unique avant tout appel au fournisseur ; preuve hachée, liée au lien, inutilisable une fois
  le lien révoqué, expiré ou soumis ; aucune session ni cookie ; `Location` du retour construite depuis l'URL publique
  configurée (pas de redirection ouverte) ; `start` exige l'en-tête du jeton (pas de requête intersite sans pré-vol) ;
  aucun compte Keycloak pour un tiers ; JWKS sans partie privée ; motifs de refus journalisés sans NRN (messages internes
  et noms de classes d'exception) ; NRN absent des journaux, des variables de process et des outbox (test de bout en bout).
- *Accepté* — un tiers légitime d'un dossier dont le champ d'identité est vide peut amener une autre personne à
  s'identifier sur son lien : le NRN de cette personne serait écrit dans ce dossier si elle soumettait la réponse ;
  l'instructeur voit le nom vérifié dans la trace. Le porteur du seul jeton d'une tâche à authentification forte peut
  faire lire un upload avant son refus, comme avant (limite par IP). En `idp-only`, l'identifiant saisi révèle si le
  compte est un compte de secours. Désactiver l'authentification forte laisse sans réponse possible les tâches qui
  l'exigent (guide de déploiement).
- *Non vérifiable ici* — noms des claims et valeurs de niveau réels de FAS et d'itsme (réglables, aucune valeur par
  défaut) : recette réelle dans #192.

**Impacte :** `docs/specs/03-authentification.md`, `docs/specs/02-multi-tenant.md`, `docs/specs/26-portail-externe.md`,
`docs/guides/DEPLOIEMENT.md`, `docs/guides/GUIDE-CONCEPTION-BPMN.md`, `docs/guides/GUIDE-UTILISATEUR.md`.
