package com.flowforge.domain.connector.model;

public enum ConnectorCapability {
    TRIGGER,
    SERVICE,
    BOTH,
    CALLBACK;

    public boolean includesTrigger() {
        return this == TRIGGER || this == BOTH;
    }

    public boolean includesService() {
        return this == SERVICE || this == BOTH;
    }
}
