package be.myrmacta.infrastructure.persistence.dossier;

import be.myrmacta.domain.dossier.port.out.DossierReferenceGenerator;
import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SequentialReferenceGenerator implements DossierReferenceGenerator {

    private final SpringDataDossierReferenceSeqRepository repository;
    private final Clock clock;

    @Autowired
    SequentialReferenceGenerator(SpringDataDossierReferenceSeqRepository repository) {
        this(repository, Clock.systemUTC());
    }

    SequentialReferenceGenerator(SpringDataDossierReferenceSeqRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public String generate(String tenantId, String dossierTypeCode) {
        int year = clock.instant().atZone(ZoneOffset.UTC).getYear();
        long sequence = repository.next(tenantId, dossierTypeCode, year);
        return "DOS-%s-%d-%06d".formatted(dossierTypeCode, year, sequence);
    }
}
