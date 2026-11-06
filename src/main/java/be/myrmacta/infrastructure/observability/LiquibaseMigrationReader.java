package be.myrmacta.infrastructure.observability;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Lit l'état de migration Liquibase depuis {@code DATABASECHANGELOG} — §119.
 *
 * <p>Toujours idempotent et sans effet de bord : lecture seule sur la table native de Liquibase.
 * Si la table n'existe pas (schéma sans migration ou avant la première migration), renvoie un
 * {@link LiquibaseMigrationStatus} « vide » plutôt que de propager une exception.
 */
@Component
public class LiquibaseMigrationReader {

    private static final Logger log = LoggerFactory.getLogger(LiquibaseMigrationReader.class);

    private static final String QUERY_COUNT_AND_LAST =
            """
            SELECT COUNT(*) AS cnt,
                   (SELECT ID FROM DATABASECHANGELOG
                    WHERE ORDEREXECUTED = MAX(d.ORDEREXECUTED)) AS last_id,
                   (SELECT DATEEXECUTED FROM DATABASECHANGELOG
                    WHERE ORDEREXECUTED = MAX(d.ORDEREXECUTED)) AS last_at
            FROM DATABASECHANGELOG d
            """;

    private final DataSource dataSource;

    public LiquibaseMigrationReader(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Renvoie le statut de migration courant.
     *
     * @return statut avec count=0 et nulls si {@code DATABASECHANGELOG} est absente ou vide
     */
    public LiquibaseMigrationStatus read() {
        try (Connection conn = dataSource.getConnection();
                var stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(QUERY_COUNT_AND_LAST)) {
            if (rs.next()) {
                long count = rs.getLong("cnt");
                String lastId = rs.getString("last_id");
                Timestamp lastAt = rs.getTimestamp("last_at");
                return new LiquibaseMigrationStatus(
                        count,
                        lastId,
                        lastAt != null ? lastAt.toInstant() : null);
            }
            return empty();
        } catch (SQLException e) {
            if (isTableMissingError(e)) {
                return empty();
            }
            log.warn("Impossible de lire DATABASECHANGELOG — statut de migration indisponible", e);
            return empty();
        }
    }

    private static LiquibaseMigrationStatus empty() {
        return new LiquibaseMigrationStatus(0L, null, null);
    }

    /**
     * Détecte l'erreur PostgreSQL 42P01 (table inexistante) pour la distinguer d'une vraie
     * erreur de connectivité — le schéma peut être vide si l'instance n'a pas encore migré.
     */
    private static boolean isTableMissingError(SQLException e) {
        return "42P01".equals(e.getSQLState());
    }
}
