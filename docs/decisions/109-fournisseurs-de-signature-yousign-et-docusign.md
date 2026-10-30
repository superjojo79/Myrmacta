# §109 — Fournisseurs de signature Yousign et DocuSign

> **Statut :** clos · point de premier niveau

**Conception (2026-10-28).** Cadrage validé le même jour (issue #191, document de travail
supprimé à la clôture). Le socle reste celui du
[§72](072-signature-electronique-socle-generique-et-stub.md).

**Livraison (2026-10-28).** Les deux lots sont livrés ([PR #247](https://github.com/superjojo79/Myrmacta/pull/247)) :
Yousign, puis DocuSign. Le comportement est repris dans la spec
[30](../specs/30-signature-electronique.md). Le parcours eID reste hors point.

**Décisions.**

- **D-SIGNATURE-PROVIDER.** Deux adaptateurs, `yousign` puis `docusign`, un seul actif par instance
  (`app.signature.provider`). Le niveau eIDAS reste celui du compte chez le fournisseur
  (D-SIGNATURE-LEVEL, informatif). Pas de bascule de l'un vers l'autre. La piste eID et l'horodatage
  BOSA sont une autre issue, non conçue ici.
- **D-SIGNATURE-HTTP.** Client HTTP du JDK (`java.net.http`), le même motif que `HttpGraphMailbox` :
  pas de redirection, réponse bornée, secret absent des erreurs, hôtes fixés par le fournisseur et
  par l'environnement (bac à sable ou production), jamais une URL venue du BPMN. Pas de SDK éditeur.
  Pas de client généré : les opérations appelées sont peu nombreuses, et une génération de la spec
  DocuSign complète réimporterait toute l'API eSignature.
- **D-SIGNATURE-ANCHOR.** Le modèle de document porte le repère textuel
  `{{sN|signature|85|37}}`, N = rang du signataire à partir de 1. Yousign le lit comme ancre
  intelligente (`parse_anchors=true`, doc « Field creation with Smart Anchors »). DocuSign pose un
  onglet SignHere sur la même chaîne (`anchorString`, doc « Auto-Place »). Aucun paramètre de
  coordonnées dans le BPMN. Yousign ne renvoie que le nombre d'ancres : chaque rang doit avoir son
  repère dans le PDF. Repère absent : la demande passe `FAILED`, le brouillon côté fournisseur
  est annulé au mieux.
- **D-SIGNATURE-LINK.** L'URL de signature n'est pas conservée à la création. Yousign la donne pour
  48 heures par défaut et la renouvelle via « Get a Signer » ; en mode séquentiel elle est nulle
  tant que ce n'est pas le tour du signataire. L'URL DocuSign (`createRecipientView`) est à usage
  unique et expire au bout de 5 minutes : la doc interdit de la stocker. Le portail la demande à
  l'ouverture de la page. « Mes signatures » la demande au clic, pas dans la liste. Puis redirection.
  L'hôte de cette URL est contrôlé (liste fixe du fournisseur) avant la redirection.
- **D-SIGNATURE-RETURN.** L'URL de retour envoyée à DocuSign ne contient pas le jeton Myrmacta.
  La page de retour est neutre. La confirmation vient du webhook.
- **D-SIGNATURE-WEBHOOK.** Le connecteur `signature` ne peut pas recevoir ces retours avec
  l'authentifiant actuel : `CallbackWebhookController` exige `Idempotency-Key`, et
  `HmacWebhookAuthenticator` signe `horodatage + "." + clé + "." + corps` dans `X-Signature-256`.
  Yousign signe le corps brut (`x-yousign-signature-256` = `sha256=` + hex HMAC-SHA256) et met
  l'identifiant dans `event_id`. DocuSign signe le corps brut en Base64 (`X-Docusign-Signature-N`).
  Le lot 1 ajoute un mode dont la clé d'idempotence est lue dans le corps déjà authentifié
  (`event_id`), sans accepter une clé d'en-tête qui contredirait ce corps. Le lot 2 ajoute le mode
  DocuSign sur le même point d'extension. Yousign exige une réponse 2xx en moins d'une seconde :
  le webhook enregistre l'issue et répond ; le téléchargement du PDF et de la preuve, puis la
  reprise du process, passent par le répartiteur déjà en place (`SignatureOutcomeDispatcherJob`).
- **D-SIGNATURE-SIGNER-EMAIL.** Un signataire interne n'a pas d'adresse aujourd'hui
  (`RequestSignatureUseCaseImpl.resolveSigner`). Les deux API en exigent une. Elle est prise dans
  `KnownUser.email`. Absente ou blanche : demande `FAILED`, motif `NO_ADDRESS`.
- **D-SIGNATURE-OPTIONAL.** Les deux adaptateurs déclarent `optionalSigners=false` tant qu'aucune
  opération publique ne distingue un signataire facultatif. Le cas d'usage refuse déjà le bloc
  (`OPTIONAL_UNSUPPORTED`). `orderedSigning=true` : `ordered_signers` chez Yousign, `routingOrder`
  chez DocuSign.

**Vérifié dans les docs publiques le 2026-10-28.** Yousign : `POST /signature_requests`
(`delivery_mode=none`), document multipart avec `parse_anchors`, activation, `signature_link` au
GET signataire, webhook `signature_request.done`. DocuSign : enveloppe avec `clientUserId`,
`createRecipientView`, ancre `anchorString`. Les noms d'événements « refusé » et « expiré » sont
relus sur la référence au début de chaque lot, avant d'écrire le mapping.

**Hors point.** Parcours eID. Contrat commercial, go-live DocuSign, abonnement Yousign. Appel des
bacs à sable depuis `./mvnw verify`.

**Impacte (à la livraison des lots).** `docs/specs/30-signature-electronique.md`,
`docs/decisions/identifiants.md` pour chaque `D-SIGNATURE-…` cité par le code, guides et
`.env*.example` seulement si une variable d'environnement nouvelle apparaît. Les secrets restent
des champs `PASSWORD` du connecteur.
