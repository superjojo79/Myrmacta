package com.flowforge.infrastructure.persistence.connector;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataTenantConnectorDekRepository extends JpaRepository<TenantConnectorDekJpaEntity, String> {}
