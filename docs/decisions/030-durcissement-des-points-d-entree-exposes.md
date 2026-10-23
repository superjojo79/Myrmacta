# §30 — Durcissement des points d'entrée exposés

> **Statut :** clos · **Section :** [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) · ancien `TODO.md` §30 · [registre](README.md)

**Livré (audit sécurité, session 2026-08-20) :** rate limiting + plafond de taille en mémoire sur
`/api/webhooks/**` (`WebhookRequestRateLimiter`, `WebhookBodySizeLimitFilter`) et sur
`POST /api/documents/generate` (`DocumentGenerationRateLimiter`,
`DocumentGenerationBodySizeLimitFilter`) ; protection SSRF sur le rendu PDF
(`RestrictedResourceUserAgent`) et les webhooks sortants (`OutboundUrlGuard`, résolution DNS
incluse) ; journal d'audit sécurité structuré sur les 403 (`SecurityAuditLog`).

**Livré (feature `durcissement-donnees-sensibles`, 2026-08-21) :**
- Pièces jointes : validation du contenu réel (magic-bytes,
  `AttachmentContentSniffer`) en complément de l'allowlist MIME déclarative
  existante ; port `AttachmentScanPort` pluggable avec adaptateur no-op
  (aucun moteur antivirus dans la stack figée — le contrat est prêt pour un
  futur adaptateur, ex. ClamAV, décision explicite de ne pas l'intégrer
  maintenant, cf. `durcissement-donnees-sensibles` design D-AV).
- SSRF sur `http-rest` en rôle SERVICE : skeleton `HttpRestServiceConnector`
  réutilisant `OutboundUrlGuard`, non enregistré dans le `ConnectorRegistry`
  (connecteur SERVICE lui-même hors V1, cf. FILE-ATTENTE) — prouve la
  réutilisabilité du guard, prêt à activer sans redévelopper.
- Fuites de secrets déchiffrés : audit des endpoints admin connecteurs et
  webhooks de notification (déjà masqués, confirmé sain) + garde-fou
  anti-régression (`ConnectorResponseSecretMaskingTest`,
  `NotificationWebhookResponseSecretMaskingTest`).

**Décision : rate limiter distribué (Redis ou équivalent) non ajouté.** Les
deux limiteurs ci-dessus sont en mémoire locale (`ConcurrentHashMap` par
JVM) — corrects pour le modèle retenu (décision n° 13, une instance par
tenant), mais chaque réplique aurait son propre compteur si un tenant était
un jour scalé horizontalement. Aucun déploiement multi-réplique n'est prévu
à court terme, et Redis introduirait une dépendance d'infrastructure hors
stack figée sans besoin actuel. À réactiver si/quand un tenant est
effectivement scalé horizontalement.

**Impacte :** [06-connecteurs.md](../specs/06-connecteurs.md),
[09-notifications.md](../specs/09-notifications.md),
[12-autorisation-conformite.md](../specs/12-autorisation-conformite.md).
