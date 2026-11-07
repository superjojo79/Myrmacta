package be.myrmacta.infrastructure.persistence.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SequentialReferenceGeneratorTest {

    @Test
    void generate_usesFixedUtcYearAndPaddedSequence() {
        SpringDataDossierReferenceSeqRepository repository = mock(SpringDataDossierReferenceSeqRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-02-03T04:05:06Z"), ZoneOffset.UTC);
        when(repository.next("tenant_dev", "TYPE", 2026)).thenReturn(1L);

        String reference = new SequentialReferenceGenerator(repository, clock).generate("tenant_dev", "TYPE");

        assertThat(reference).isEqualTo("DOS-TYPE-2026-000001");
        verify(repository).next("tenant_dev", "TYPE", 2026);
    }
}
