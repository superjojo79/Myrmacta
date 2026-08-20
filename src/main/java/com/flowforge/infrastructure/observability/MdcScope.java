package com.flowforge.infrastructure.observability;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Puts MDC keys and restores only those keys on close (no global {@link MDC#clear()}).
 */
public final class MdcScope implements AutoCloseable {

    public static final String TENANT_ID = "tenantId";
    public static final String DOSSIER_ID = "dossierId";
    public static final String PROCESS_INSTANCE_ID = "processInstanceId";

    private final Map<String, String> previous = new LinkedHashMap<>();
    private final Set<String> placed = new LinkedHashSet<>();

    public static MdcScope tenant(String tenantId) {
        return new MdcScope().and(TENANT_ID, tenantId);
    }

    public MdcScope dossier(UUID dossierId) {
        return and(DOSSIER_ID, dossierId == null ? null : dossierId.toString());
    }

    public MdcScope dossier(String dossierId) {
        return and(DOSSIER_ID, dossierId);
    }

    public MdcScope processInstance(String processInstanceId) {
        return and(PROCESS_INSTANCE_ID, processInstanceId);
    }

    public MdcScope and(String key, String value) {
        if (key == null || value == null || value.isBlank()) {
            return this;
        }
        previous.putIfAbsent(key, MDC.get(key));
        MDC.put(key, value);
        placed.add(key);
        return this;
    }

    @Override
    public void close() {
        for (String key : placed) {
            String prev = previous.get(key);
            if (prev == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, prev);
            }
        }
    }
}
