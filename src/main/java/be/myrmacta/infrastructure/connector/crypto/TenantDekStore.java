package be.myrmacta.infrastructure.connector.crypto;

import java.util.Optional;

public interface TenantDekStore {

    Optional<String> findWrappedDek(String tenantId);

    void saveWrappedDek(String tenantId, String wrappedDek, int keyVersion);
}
