# §145 — Nom du publicateur dans le journal des brouillons

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (issue [#112](https://github.com/superjojo79/Myrmacta/issues/112)). Mandat cloud agent 2026-11-16.
Comportement : [spec 37](../specs/37-socle-mcp-low-code.md) § « Implémentation » (journal des publications).

**Constat.** Dans *Admin → Brouillons*, *Dernières publications* indique qui a publié par le sujet OIDC
(UUID), pas par le nom. Relevé au tournage de la capsule C10 ([§140](140-capsules-courtes-du-site.md)).

**D-DRAFT-PUB-NAME** — l'API d'administration enrichit chaque publication avec le nom issu de l'annuaire
local (D-ASSIGN-DIR, [§92](092-assignation-des-taches-m-assigner-et-annuaire.md)) :

1. `publishedBy` reste le sujet technique (journal, MCP, audit inchangés) ;
2. `publishedByDisplayName` est le `displayName` de `known_user` pour ce sujet, ou le sujet lui-même si
   l'utilisateur est inconnu (pas encore connecté, compte supprimé de l'annuaire, acteur M2M) ;
3. l'écran *Brouillons* affiche `publishedByDisplayName` dans la ligne du journal ;
4. écarté : résoudre côté SPA via `GET /api/admin/user-directory` (N+1, double source de vérité pour le
   libellé). Écarté aussi : remplacer la valeur de `publishedBy` (casserait le contrat MCP / journal qui
   attend le sujet).

**Impacte :** `DraftsController`, schéma OpenAPI `ConfigPublication`, `DraftsAdminView`,
`docs/guides/GUIDE-UTILISATEUR.md` § Brouillons. Capsule C10 : sortie non versionnée ([§127](127-demo-par-paquets-et-captures-du-site.md)) ;
régénération locale (`scripts/video.sh capsule c10-brouillons`) après déploiement du correctif.
