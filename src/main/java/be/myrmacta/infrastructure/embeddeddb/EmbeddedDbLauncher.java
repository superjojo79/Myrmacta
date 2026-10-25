package be.myrmacta.infrastructure.embeddeddb;

/**
 * Contrat de démarrage de la base embarquée. Implémenté par le module {@code src/embedded}
 * (profil Maven {@code embedded-db}) et découvert par {@link java.util.ServiceLoader}.
 */
public interface EmbeddedDbLauncher {

    /** Démarre (ou reprend) la base persistante et renvoie ses coordonnées de connexion. */
    RunningDb start(EmbeddedDbSettings settings) throws Exception;

    /**
     * Base en cours d'exécution.
     *
     * @param handle fermeture propre (arrêt « fast » : données cohérentes, pas de crash recovery)
     */
    record RunningDb(String jdbcUrl, String username, String password, AutoCloseable handle) {}
}
