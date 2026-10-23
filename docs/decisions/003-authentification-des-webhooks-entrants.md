# §3 — Authentification des webhooks entrants

> **Statut :** clos · **Section :** [Décisions actées › Sécurité](section-decisions-securite.md) · ancien `TODO.md` §3 · [registre](README.md)

**Problème :** Le connecteur `http-rest` en mode TRIGGER recevait des webhooks externes sans mécanisme d'authentification documenté.

**Décision retenue :** Signature HMAC-SHA256 par défaut (modèle GitHub/Stripe), secret stocké comme champ `PASSWORD` de la `ConnectorInstance` (donc chiffré au repos comme les autres secrets). Un champ `webhookAuthType` (SELECT : `HMAC_SHA256`, `SHARED_SECRET_TOKEN`, `IP_ALLOWLIST`) permet d'adapter le mécanisme aux capacités du système appelant. Vérification effectuée dans l'adaptateur web (`WebhookController`), avant d'atteindre `ConnectorTriggerRuntime` — c'est un détail HTTP/infrastructure, pas une responsabilité du domaine.

**Documenté dans :** [06-connecteurs.md](../specs/06-connecteurs.md).
