package com.flowforge.infrastructure.persistence.dossier;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataTenantRetentionSettingsRepository
        extends JpaRepository<TenantRetentionSettingsJpaEntity, String> {}
