# §4 — Gestion des clés de chiffrement des secrets de connecteurs

> **Statut :** clos · **Section :** [Décisions actées › Sécurité](section-decisions-securite.md) · ancien `TODO.md` §4 · [registre](README.md)

**Problème :** Les champs `PASSWORD` des `ConnectorInstance` sont chiffrés AES-GCM avec une « clé par tenant », mais la gestion de ces clés n'était pas documentée.

**Décision retenue :** Chiffrement en enveloppe — une KEK (Key Encryption Key) unique gérée par un service externe (HashiCorp Vault Transit, Azure Key Vault ou AWS KMS), et une DEK (Data Encryption Key) par tenant, chiffrée par la KEK et stockée en base sous forme chiffrée. La rotation de la KEK est déléguée au vault (versionnage natif) ; la rotation d'une DEK est un job explicite (`RotateTenantEncryptionKeyUseCase`), jamais automatique. Une clé maître unique via variable d'environnement reste un compromis toléré uniquement en mono-tenant/développement, documenté comme limitation temporaire.

**Documenté dans :** [06-connecteurs.md](../specs/06-connecteurs.md).
