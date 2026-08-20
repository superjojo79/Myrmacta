package com.flowforge.domain.connector.port.out;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConnectorInstanceRepository {

    ConnectorInstanceData save(ConnectorInstanceData data);

    Optional<ConnectorInstanceData> findByIdAndTenantId(UUID id, String tenantId);

    Optional<ConnectorInstanceData> findByIdAndTenantIdIncludingDeleted(UUID id, String tenantId);

    List<ConnectorInstanceData> listByTenantId(String tenantId);

    default PageResult<ConnectorInstanceData> pageByTenantId(String tenantId, PageQuery query) {
        return PageResult.slice(listByTenantId(tenantId), query);
    }

    void softDelete(UUID id, String tenantId);
}
