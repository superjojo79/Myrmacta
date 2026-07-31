package com.flowforge.infrastructure.persistence.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

class SequentialReferenceGeneratorTest {

    @Test
    void generate_usesFixedUtcYearAndPaddedSequenceAfterInsertUpdateSelect() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Clock clock = Clock.fixed(Instant.parse("2026-02-03T04:05:06Z"), ZoneOffset.UTC);
        SequentialReferenceGenerator generator = new SequentialReferenceGenerator(jdbcTemplate, clock);
        when(jdbcTemplate.queryForObject(
                        contains("select last_value"), eq(Long.class), eq("tenant_dev"), eq("TYPE"), eq(2026)))
                .thenReturn(1L);

        String reference = generator.generate("tenant_dev", "TYPE");

        assertThat(reference).isEqualTo("DOS-TYPE-2026-000001");
        InOrder calls = inOrder(jdbcTemplate);
        calls.verify(jdbcTemplate).update(contains("insert into dossier_reference_seq"), eq("tenant_dev"), eq("TYPE"), eq(2026));
        calls.verify(jdbcTemplate).update(contains("update dossier_reference_seq"), eq("tenant_dev"), eq("TYPE"), eq(2026));
        calls.verify(jdbcTemplate).queryForObject(
                contains("select last_value"), eq(Long.class), eq("tenant_dev"), eq("TYPE"), eq(2026));
    }
}
