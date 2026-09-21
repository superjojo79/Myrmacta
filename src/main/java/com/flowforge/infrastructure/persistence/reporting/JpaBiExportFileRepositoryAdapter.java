package com.flowforge.infrastructure.persistence.reporting;

import com.flowforge.domain.reporting.model.BiExportFile;
import com.flowforge.domain.reporting.model.BiExportKind;
import com.flowforge.domain.reporting.model.TenantSlaSettings;
import com.flowforge.domain.reporting.port.out.BiExportCommitPort;
import com.flowforge.domain.reporting.port.out.BiExportFileRepository;
import com.flowforge.domain.reporting.port.out.TenantSlaSettingsRepository;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
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
    public void commitSuccessfulExport(String tenantId, List<BiExportFile> files, Instant exportedAt) {
        for (BiExportFile file : files) {
            repository.save(BiExportFileJpaEntity.fromDomain(file));
        }
        TenantSlaSettings current = settingsRepository
                .findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalStateException("SLA settings disappeared during BI export"));
        if (!current.biExportEnabled()) {
            throw new IllegalStateException("BI export was disabled before commit");
        }
        settingsRepository.save(current.withLastBiExportAt(exportedAt));
    }
}
