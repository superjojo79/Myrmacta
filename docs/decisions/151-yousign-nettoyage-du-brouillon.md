# §151 — Yousign : nettoyage du brouillon quand l'envoi échoue

> **Statut :** clos · point de premier niveau · [registre](README.md)

[x] clos (2026-11-20, issue [#133](https://github.com/superjojo79/Myrmacta/issues/133)). Prolonge
[§109](109-fournisseurs-de-signature-yousign-et-docusign.md) (D-SIGNATURE-ANCHOR).

**Constat.** `YousignSignatureAdapter.submit` crée la demande, dépose le PDF, ajoute les signataires puis l'active. Le
brouillon n'était nettoyé que si une ancre manquait, et par `POST /cancel`. Un dépôt refusé (premier essai réel : nom
de fichier sans extension, 400), un signataire refusé ou une activation en échec laissaient la demande en brouillon
chez Yousign ; un nouvel essai en créait une autre.

**Documentation de l'API v3 (relue le 2026-11-20).** `POST /signature_requests/{id}/cancel` n'annule qu'une demande
en `approval` ou `ongoing`. `DELETE /signature_requests/{id}` supprime une demande, sauf en `approval` ou `ongoing` ;
réponse 204 sans corps ; `permanent_delete=true` la rend irrécupérable. Le `cancel` posé jusqu'ici sur un brouillon
était donc refusé, en silence.

**Décision (D-SIGNATURE-DRAFT-CLEANUP).**

- Tout échec après la création (dépôt, ancre manquante, signataire, activation) supprime la demande par `DELETE`,
  sans `permanent_delete` : Yousign garde la trace d'une suppression.
- Si la suppression est refusée, la demande a pu passer en cours malgré l'erreur (activation aboutie, réponse
  perdue) : elle est annulée par `POST /cancel`.
- Le nettoyage est au mieux. Son échec est journalisé (`WARN`, sans secret ni lien) et ne lève rien : l'erreur
  d'origine remonte telle quelle, et la demande Myrmacta passe `FAILED` comme avant.
- Un échec de la création elle-même n'a rien à nettoyer.

**Tests.** `YousignSignatureAdapterTest` (échec au dépôt, au deuxième signataire, à l'activation ; suppression
refusée puis annulation refusée ; création en échec), `YousignHttpClientTest` (`DELETE` et réponse 204, refus 405).

**Impacte.** [spec 30](../specs/30-signature-electronique.md) (implémentation Yousign), `identifiants.md`.
