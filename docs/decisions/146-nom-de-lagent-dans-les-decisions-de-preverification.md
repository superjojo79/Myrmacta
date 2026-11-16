# §146 — Nom de l'agent dans les décisions de pré-vérification

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (issue [#118](https://github.com/superjojo79/Myrmacta/issues/118)). Mandat cloud agent 2026-11-16.
Comportement : [spec 48](../specs/48-preverification-ia.md) §4–5.

**Constat.** Sur la fiche d'un dossier, quand l'agent accepte ou écarte un constat de pré-vérification, la
décision affiche l'auteur par le sujet OIDC (UUID), pas par le nom. Même famille que [#112](https://github.com/superjojo79/Myrmacta/issues/112)
([§145](145-nom-du-publicateur-dans-le-journal-des-brouillons.md)). Relevé au tournage de la capsule C6
([§143](143-scene-riverain-capsules-c5-c6.md)).

**D-PRECHECK-DECIDER-NAME** — l'API des pré-vérifications enrichit chaque constat décidé avec le nom issu de
l'annuaire local (D-ASSIGN-DIR, [§92](092-assignation-des-taches-m-assigner-et-annuaire.md)), via le helper
commun `UserDisplayNames` (partagé avec §145) :

1. `decidedBy` reste le sujet technique (persistance, audit, journal de sécurité inchangés) ;
2. `decidedByDisplayName` est le `displayName` de `known_user` pour ce sujet, ou le sujet lui-même si
   l'utilisateur est inconnu ; `null` si aucune décision ;
3. le panneau `PrecheckPanel` affiche `decidedByDisplayName` (repli sur `decidedBy`) ;
4. écarté : résoudre côté SPA via l'annuaire admin (N+1) ; écarté : remplacer `decidedBy` (contrat d'audit).

**Impacte :** `DossierPrecheckController`, `PrecheckRunResponse`, `PrecheckPanel`,
`docs/guides/GUIDE-UTILISATEUR.md` §16. Capsule C6 : sortie non versionnée ([§127](127-demo-par-paquets-et-captures-du-site.md)) ;
régénération locale (`scripts/video.sh capsule c06-preverification`) après déploiement du correctif.
