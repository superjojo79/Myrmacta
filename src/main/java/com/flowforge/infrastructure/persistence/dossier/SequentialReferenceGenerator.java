package com.flowforge.infrastructure.persistence.dossier;

import com.flowforge.domain.dossier.port.out.DossierReferenceGenerator;
import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SequentialReferenceGenerator implements DossierReferenceGenerator {

    private static final String INSERT_SQL = """
            insert into dossier_reference_seq (tenant_id, dossier_type_code, "year", last_value)
            values (?, ?, ?, 0)
            """;

    private static final String UPDATE_SQL = """
            update dossier_reference_seq
            set last_value = last_value + 1
            where tenant_id = ? and dossier_type_code = ? and "year" = ?
            """;

    private static final String SELECT_SQL = """
            select last_value
            from dossier_reference_seq
            where tenant_id = ? and dossier_type_code = ? and "year" = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Autowired
    public SequentialReferenceGenerator(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC());
    }

    SequentialReferenceGenerator(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    @Transactional
    public String generate(String tenantId, String dossierTypeCode) {
        int year = clock.instant().atZone(ZoneOffset.UTC).getYear();
        try {
            jdbcTemplate.update(INSERT_SQL, tenantId, dossierTypeCode, year);
        } catch (DuplicateKeyException ignored) {
            // Existing counter row: proceed to the atomic increment below.
        }
        jdbcTemplate.update(UPDATE_SQL, tenantId, dossierTypeCode, year);
        Long sequence = jdbcTemplate.queryForObject(SELECT_SQL, Long.class, tenantId, dossierTypeCode, year);

        return "DOS-%s-%d-%06d".formatted(dossierTypeCode, year, sequence);
    }
}
