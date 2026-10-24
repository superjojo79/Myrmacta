package com.flowforge.infrastructure.persistence.dossier;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierFieldValidationException;
import com.flowforge.domain.dossier.model.FieldValidationError;
import com.flowforge.domain.dossier.model.UniqueKey;
import com.flowforge.domain.dossier.port.out.DossierUniquenessPort;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * TODO §102 (issue #184) — table {@code dossier_unique_value} (D-UNIQUE-STORE) et verrou consultatif par type
 * (D-UNIQUE-TYPE-LOCK).
 *
 * <p>Le verrou est <b>partagé</b> pendant l'enregistrement d'un dossier (les enregistrements ne s'attendent jamais entre
 * eux) et <b>exclusif</b> pendant celui de son type, qui reconstruit la table : un dossier enregistré pendant qu'on active
 * {@code unique} ne peut donc pas échapper à la reconstruction. La clé du verrou inclut le schéma courant, les verrous
 * consultatifs valant pour toute la base ; une collision de hachage ne coûte qu'une attente.
 *
 * <p>Les insertions utilisent {@code on conflict do nothing} plutôt qu'une violation de clé : sous PostgreSQL une violation
 * avorte la transaction, qui ne pourrait plus lire le dossier détenteur pour construire l'erreur.
 */
@Component
public class JdbcDossierUniqueValueStore implements DossierUniquenessPort {

    /** Espace de clés propre à la fonctionnalité (premier argument de {@code pg_advisory_xact_lock*}). */
    static final int LOCK_NAMESPACE = 184_102;

    private static final String LOCK_KEY = "hashtext(current_schema() || ':' || ? || ':' || ?)";
    private static final String LOCK_SHARED_SQL = "select pg_advisory_xact_lock_shared(" + LOCK_NAMESPACE + ", " + LOCK_KEY + ")";
    private static final String LOCK_EXCLUSIVE_SQL = "select pg_advisory_xact_lock(" + LOCK_NAMESPACE + ", " + LOCK_KEY + ")";

    private static final String DELETE_FOR_DOSSIER_SQL = "delete from dossier_unique_value where dossier_id = ?";

    private static final String INSERT_SQL = """
            insert into dossier_unique_value (tenant_id, dossier_type_code, field_name, field_value, dossier_id)
            values (?, ?, ?, ?, ?)
            on conflict do nothing
            """;

    private static final String HOLDER_SQL = """
            select d.reference, u.dossier_id
            from dossier_unique_value u join dossier d on d.id = u.dossier_id
            where u.tenant_id = ? and u.dossier_type_code = ? and u.field_name = ? and u.field_value = ?
            """;

    private static final String DELETE_FOR_TYPE_SQL =
            "delete from dossier_unique_value where tenant_id = ? and dossier_type_code = ?";

    private final JdbcTemplate jdbcTemplate;

    public JdbcDossierUniqueValueStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Verrou partagé du type, jusqu'à la fin de la transaction courante (enregistrement d'un dossier). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockTypeShared(String tenantId, String dossierTypeCode) {
        jdbcTemplate.queryForList(LOCK_SHARED_SQL, tenantId, dossierTypeCode);
    }

    /** Verrou exclusif du type, jusqu'à la fin de la transaction courante (enregistrement du type). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockTypeExclusive(String tenantId, String dossierTypeCode) {
        jdbcTemplate.queryForList(LOCK_EXCLUSIVE_SQL, tenantId, dossierTypeCode);
    }

    /**
     * Remplace les valeurs uniques tenues par {@code dossier} par {@code keys}. Une valeur déjà tenue par un autre dossier
     * lève {@link DossierFieldValidationException} ({@code UNIQUE}) : l'appelant annule la transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void sync(Dossier dossier, Set<UniqueKey> keys) {
        jdbcTemplate.update(DELETE_FOR_DOSSIER_SQL, dossier.id());
        List<FieldValidationError> conflicts = new ArrayList<>();
        for (UniqueKey key : sorted(keys)) {
            if (!insert(dossier.tenantId(), dossier.dossierTypeCode(), key, dossier.id())) {
                conflicts.add(FieldValidationError.notUnique(
                        key.field(), holder(dossier.tenantId(), dossier.dossierTypeCode(), key)));
            }
        }
        if (!conflicts.isEmpty()) {
            throw new DossierFieldValidationException(conflicts);
        }
    }

    /**
     * Reconstruit toutes les valeurs d'un type (enregistrement du type, sous verrou exclusif) : supprime les lignes du type,
     * puis insère {@code keysByDossier}. Renvoie les clés déjà prises par un autre dossier de la liste (doublon).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<UniqueKey> replaceForType(String tenantId, String dossierTypeCode, Map<UUID, Set<UniqueKey>> keysByDossier) {
        jdbcTemplate.update(DELETE_FOR_TYPE_SQL, tenantId, dossierTypeCode);
        List<UniqueKey> conflicts = new ArrayList<>();
        keysByDossier.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sorted(entry.getValue()).forEach(key -> {
                    if (!insert(tenantId, dossierTypeCode, key, entry.getKey())) {
                        conflicts.add(key);
                    }
                }));
        return List.copyOf(conflicts);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UniqueKey, String> findHolders(
            String tenantId, String dossierTypeCode, Set<UniqueKey> keys, UUID excludeDossierId) {
        Map<UniqueKey, String> holders = new LinkedHashMap<>();
        for (UniqueKey key : sorted(keys)) {
            jdbcTemplate.query(HOLDER_SQL, rs -> {
                UUID holderId = rs.getObject("dossier_id", UUID.class);
                if (!holderId.equals(excludeDossierId)) {
                    holders.put(key, rs.getString("reference"));
                }
            }, tenantId, dossierTypeCode, key.field(), key.value());
        }
        return Map.copyOf(holders);
    }

    private boolean insert(String tenantId, String dossierTypeCode, UniqueKey key, UUID dossierId) {
        return jdbcTemplate.update(INSERT_SQL, tenantId, dossierTypeCode, key.field(), key.value(), dossierId) == 1;
    }

    private String holder(String tenantId, String dossierTypeCode, UniqueKey key) {
        List<String> references = jdbcTemplate.query(HOLDER_SQL, (rs, i) -> rs.getString("reference"),
                tenantId, dossierTypeCode, key.field(), key.value());
        return references.isEmpty() ? "?" : references.getFirst();
    }

    /** Ordre stable d'insertion : deux transactions concurrentes prennent les clés dans le même ordre (pas d'interblocage). */
    private static List<UniqueKey> sorted(Set<UniqueKey> keys) {
        return keys.stream()
                .sorted(Comparator.comparing(UniqueKey::field).thenComparing(UniqueKey::value))
                .toList();
    }
}
