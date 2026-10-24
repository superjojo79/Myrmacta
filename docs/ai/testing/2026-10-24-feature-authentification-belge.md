---
phase: testing
title: Authentification belge — tests
description: Scénarios dérivés des règles K, A, T, N et E
---

# Tests — `authentification-belge` (issue #210)

Objectif de couverture : 100 % des nouvelles classes du domaine (NRN, niveau, règle de correspondance, identité
vérifiée), chaque règle couverte par au moins un test. Aucune dépendance aux vrais FAS ou itsme : les tests passent contre
un fournisseur simulé.

## Unitaires — domaine

- [ ] `NationalRegisterNumber` : séparateurs retirés (`85.07.30-033.28` = `85073003328`) ; autre longueur ou caractère
      non numérique → absent (N3) ; `toString` masqué.
- [ ] `IdentityAssuranceLevel` : substantiel et élevé acceptés, faible et inconnu refusés (T4).
- [ ] Règle de correspondance : champ rempli + même NRN → conforme ; champ rempli + autre NRN → non conforme ; champ vide
      → premier enregistrement ; sans NRN + champ rempli → non conforme ; sans NRN + champ vide → sans NRN (T5-T7).
- [ ] `PortalTaskDefinition` / grammaire de `portalIdentityField` : `dossierField:` seulement, identifiant simple.
- [ ] Use case de soumission : identité absente, expirée ou d'un autre lien → refus neutre ; conforme → complétion ;
      premier enregistrement → écriture du champ puis complétion ; champ rempli entre l'authentification et la
      soumission → refus (T6) ; trace sans NRN (T8).
- [ ] Use case de démarrage : fournisseur non configuré refusé ; `state` à usage unique ; lien inactif → refus neutre.
- [ ] Use case de retour : `state` inconnu, expiré ou rejoué → refus neutre ; niveau faible → refus ; non-conformité
      détectée dès le retour ; succès → preuve remise, hachée en base, identité précédente du lien remplacée.
- [ ] Preuve : expirée (60 min), d'un lien révoqué ou d'un autre lien → refus neutre ; le jeton du lien seul ne suffit
      plus pour lire le contexte, déposer ou soumettre (T3b, T9).
- [ ] Durée : `identity-ttl` au-delà de 4 h → démarrage refusé.
- [ ] Trace `identity_verification` de la réponse : fournisseur, niveau, nom, résultat, sans NRN (T8).
- [ ] Émission du lien : champ d'identité absent, non `STRING` ou non sensible → `NOT_SENT` (`INVALID_IDENTITY_FIELD`)
      et `notifyNotSent`, sans exception vers le moteur (T2).
- [ ] Vue portail sans identité : nom de la tâche, référence et fournisseurs seulement ; dépôt de pièce refusé (T3b).
- [ ] `KnownUserRecordingSuccessHandler` : `tenant_id` absent ou différent → rien n'est enregistré, session invalidée,
      403 (A6).

## Intégration — backend

- [ ] Déploiement : `portalIdentityField` refusé si le réglage est éteint ou sans fournisseur (E2) ; accepté sinon ;
      syntaxe invalide refusée.
- [ ] Chaîne portail : `start` puis `callback` contre un émetteur OIDC simulé dans le test (JWKS, endpoint de jeton),
      ID token signé : identité rattachée au lien ; signature, émetteur, audience ou `nonce` faux → refus.
- [ ] Soumission de bout en bout (PostgreSQL, moteur) : champ vide → NRN écrit, masqué dans `GET /api/dossiers/{id}`
      sans droit sensible ; champ rempli et conforme → complétion ; non conforme → tâche toujours ouverte, lien actif.
- [ ] Révocation, expiration, renvoi du lien et purge du dossier → identité vérifiée effacée (T9).
- [ ] NRN absent des logs capturés, de l'audit, de l'outbox, des variables de process et des webhooks (N2).
- [ ] Limite par IP appliquée à `start` et `callback`.
- [ ] Portail éteint ou authentification forte éteinte : aucune route `identity` exposée.
- [ ] `GET /api/portal/identity/jwks` : clé publique de l'instance seulement (aucun paramètre privé) ; l'assertion
      `private_key_jwt` envoyée à l'endpoint de jeton simulé est signée par cette clé (T11).
- [ ] Connexion OIDC d'un utilisateur d'un autre tenant : absent de l'annuaire après la tentative (A6).
- [ ] Provisionnement : `provision-tenant.sh --realm-per-tenant --idp fas --idp itsme --password-login idp-only` génère
      `<tenant>.realm.json` (structure vérifiée : client BFF et mappers, rôles, `default-roles` vide, profil, mappers des
      fournisseurs sans NRN ni `tenant_id`, surcharge de flux) et `<tenant>.keycloak.sh`, sans secret en clair, et refuse
      d'écraser ; sans `--realm-per-tenant`, sortie inchangée.
- [ ] Recette Keycloak (`scripts/keycloak-realm-smoke.sh`, D-BEAUTH-KC-SMOKE) : realm appliqué deux fois sans erreur
      (idempotence), connexion par le simulateur, compte sans `tenant_id`, aucun NRN dans l'ID token, mode `idp-only`,
      admin de secours dans la console.

## Frontend — Vitest

- [ ] Page portail : identité requise → écran de choix du fournisseur, contexte selon la phase 2.
- [ ] Aucun stockage navigateur : `#identity:<preuve>` lu une fois, effacé de la barre d'adresse, envoyé en
      `X-Portal-Identity` (D-BEAUTH-PROOF).
- [ ] `#identity-refused` → refus neutre ; preuve valide → formulaire ; deux fournisseurs → deux boutons dans l'ordre
      reçu, un seul sinon.

## Frontend — Playwright

- [ ] Portail (mocks) : choix du fournisseur, retour simulé, soumission acceptée.
- [ ] Portail (mocks) : retour non conforme → refus neutre.

## Recette locale (docker-compose, simulateur)

- [ ] Agent : connexion par le simulateur → compte sans `tenant_id` ni rôle, 403 ; validé par l'admin → accès normal.
- [ ] Tiers : aucun compte créé dans Keycloak après une authentification portail.
- [ ] Agent existant : rattachement par ré-authentification, même `sub`.
- [ ] Mode `idp-only` : plus de formulaire de mot de passe, compte de secours utilisable.
- [ ] BFF : aucun claim NRN dans l'ID token reçu (A5).
- [ ] Tiers : lien reçu par e-mail (GreenMail ou Mailpit), authentification simulée, NRN écrit dans le dossier.

## Recette réelle (différée, #192)

- [ ] Connexion contre l'intégration FAS de BOSA, puis contre l'environnement de test itsme, une fois les contrats
      signés. Claims et niveaux réels comparés au simulateur.

## Fixtures et mocks

- Utilisateurs du simulateur : NRN connu, NRN différent, niveau faible, identité eIDAS sans NRN.
- Type de dossier de test avec un champ `nrnDemandeur` (`STRING`, sensible) et un BPMN de tâche externe à
  authentification forte.
- Émetteur OIDC de test côté Java (clé RSA générée, JWKS et endpoint de jeton servis localement).

## Vérifications finales

- [ ] `./mvnw verify`
- [ ] `cd frontend && npm run lint && npm run test:all`
- [ ] `scripts/check-docs.sh`
