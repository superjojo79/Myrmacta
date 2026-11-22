package be.myrmacta.domain.ebox.model;

/**
 * TODO §155 (D-EBOX-SEND-CONSENT) — voie de secours quand l'eBox n'est pas utilisable (pas de consentement, pas d'eBox) ou en
 * copie (entreprise sans eBox exclusive). Myrmacta ne l'exécute pas lui-même : le process reçoit la voie et la suit.
 */
public enum FallbackChannel {
    PAPER,
    EMAIL
}
