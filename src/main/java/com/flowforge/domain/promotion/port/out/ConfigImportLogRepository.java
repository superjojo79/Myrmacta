package com.flowforge.domain.promotion.port.out;

import com.flowforge.domain.promotion.model.ConfigImportRecord;
import java.util.List;

/** TODO §83 (D4) — journal des applications de bundle. */
public interface ConfigImportLogRepository {

    ConfigImportRecord save(ConfigImportRecord record);

    List<ConfigImportRecord> findRecent(String tenantId, int limit);
}
