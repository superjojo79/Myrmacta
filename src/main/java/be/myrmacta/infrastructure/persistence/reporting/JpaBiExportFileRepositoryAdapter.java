package be.myrmacta.infrastructure.persistence.reporting;

import be.myrmacta.domain.reporting.model.BiExportFile;
import be.myrmacta.domain.reporting.model.BiExportKind;
import be.myrmacta.domain.reporting.model.TenantSlaSettings;
import be.myrmacta.domain.reporting.port.out.BiExportCommitPort;
import be.myrmacta.domain.reporting.port.out.BiExportFileRepository;
import be.myrmacta.domain.reporting.port.out.TenantSlaSettingsRepository;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaBiExportFileRepositoryAdapter implements BiExportFileRepository, BiExportCommitPort {

    private final SpringDataBiExportFileRepository repository;
    private final TenantSlaSettingsRepository settingsRepository;

    public JpaBiExportFileRepositoryAdapter(
            SpringDataBiExportFileRepository repository, TenantSlaSettingsRepository settingsRepository) {
        this.repository = repository;
        this.settingsRepository = settingsRepository;
    }

    @Override
    @Transactional
    public BiExportFile save(BiExportFile file) {
        return repository.saveAndFlush(BiExportFileJpaEntity.fromDomain(file)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<BiExportFile> page(String tenantId, Set<String> viewableTypeCodes, PageQuery query) {
        PageRequest pageable = PageRequest.of(query.page(), query.size());
        Page<BiExportFileJpaEntity> page;
        if (viewableTypeCodes == null) {
            page = repository.pageAll(tenantId, pageable);
        } else if (viewableTypeCodes.isEmpty()) {
            page = repository.pageSla(tenantId, BiExportKind.SLA, pageable);
        } else {
            page = repository.pageVisible(tenantId, BiExportKind.SLA, viewableTypeCodes, pageable);
        }
        List<BiExportFile> content = page.getContent().stream().map(BiExportFileJpaEntity::toDomain).toList();
        return PageResult.of(content, query.page(), query.size(), page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BiExportFile> findById(String tenantId, UUID id) {
        return repository.findByTenantIdAndId(tenantId, id).map(BiExportFileJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BiExportFile> findCreatedBefore(String tenantId, Instant cutoff) {
        return repository.findByTenantIdAndCreatedAtBefore(tenantId, cutoff).stream()
                .map(BiExportFileJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        repository.deleteById(id);
    }

    @Override
    @Transactional
    public void commitSuccessfulExport(
            String tenantId, List<BiExportFile> files, Instant exportedAt, Instant expectedLastBiExportAt) {
        // D5 : la prise du créneau est la PREMIÈRE écriture de la transaction. Sur PostgreSQL, un second exécutant attend la
        // validation du premier puis ne trouve plus la valeur attendue : 0 ligne, exception, rollback — aucun fichier catalogué
        // en double (les objets déjà stockés sont retirés par l'appelant).
        if (!settingsRepository.advanceLastBiExportAt(tenantId, expectedLastBiExportAt, exportedAt)) {
            throw new be.myrmacta.domain.reporting.port.out.BiExportAlreadyCommittedException(tenantId);
        }
        for (BiExportFile file : files) {
            repository.save(BiExportFileJpaEntity.fromDomain(file));
        }
        TenantSlaSettings current = settingsRepository
                .findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalStateException("SLA settings disappeared during BI export"));
        if (!current.biExportEnabled()) {
            throw new IllegalStateException("BI export was disabled before commit");
        }
    }
}
