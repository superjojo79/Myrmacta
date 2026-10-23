# §54 — 2ème revue de sécurité adversariale sur `connecteur-callback` — 4 nouveaux écarts (F5–F8)

> **Statut :** clos · **Section :** [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) · ancien `TODO.md` §54 · [registre](README.md)

**Statut (2026-09-03) : corrigé.** Nouvelle revue adversariale complète, indépendante de la
1ère (TODO §51), demandée explicitement par l'utilisateur. Reconfirme F0–F4 toujours clos, trouve
et corrige 4 nouveaux écarts (« corrige tout maintenant ») :

- **F5 (haute)** — le corps de requête est intégralement bufférisé en mémoire par Spring **avant**
  toute garde applicative (`body.length > maxBodyBytes`, rate limiter, authentification) : DoS
  mémoire non authentifié via `Transfer-Encoding: chunked` (ou tout mensonge sur `Content-Length`)
  sur `/api/callbacks/**`/`/api/webhooks/**`. Corrigé : `WebhookBodySizeLimitFilter` enveloppe la
  requête dans un `ServletInputStream` qui borne les octets réellement lus, indépendamment de ce
  que la requête déclare.
- **F6 (moyenne)** — pas de protection anti-rejeu HMAC (signature du corps seul, `Idempotency-Key`
  non signée) : une paire (corps, signature) capturée une fois était rejouable indéfiniment.
  Corrigé : signature étendue à `timestamp + "." + corps` (`X-Webhook-Timestamp` requis, fenêtre
  de fraîcheur 5 min) — **rupture de contrat pour les appelants HMAC existants**, documentée dans
  les champs de config et `docs/specs/06-connecteurs.md`.
- **F7 (moyenne)** — aucune longueur minimale sur `webhookSecret` : brute-forçable en pratique avec
  la limite de débit par défaut (120 req/min) si l'opérateur choisit un secret court. Corrigé :
  `WebhookSecretPolicy.MIN_LENGTH = 20`, appliqué dans `validate()` des 2 connecteurs concernés.
- **F8 (moyenne)** — échec d'authentification webhook jamais journalisé : une tentative de brute
  force soutenue contre F7 aurait été invisible. Corrigé :
  `SecurityAuditLog.webhookAuthenticationFailed(...)` (WARN), câblé dans
  `ConnectorExceptionHandler`.

Détail complet (root cause, faux positifs écartés) :
[`docs/ai/implementation/2026-09-02-feature-connecteur-callback.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-09-02-feature-connecteur-callback.md) § « Correctifs post-merge
(2ème passe adversariale) ». `./mvnw verify` : **1554 tests, 0 échec** (+13 tests de régression
permanents).
