package com.flowforge.infrastructure.persistence.dossier;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataTenantSlaSettingsRepository extends JpaRepository<TenantSlaSettingsJpaEntity, String> {}
