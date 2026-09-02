package com.flowforge.domain.connector.model;

/** No execution is waiting on {@code messageName}/{@code correlationKey}=value (D-CALLBACK-RACE). */
public class CallbackNoMatchingInstanceException extends RuntimeException {

    private final String messageName;
    private final String correlationKey;
    private final Object correlationValue;

    public CallbackNoMatchingInstanceException(
            String messageName, String correlationKey, Object correlationValue) {
        super("No process execution waiting on message '" + messageName + "' with " + correlationKey
                + "=" + correlationValue);
        this.messageName = messageName;
        this.correlationKey = correlationKey;
        this.correlationValue = correlationValue;
    }

    public String messageName() {
        return messageName;
    }

    public String correlationKey() {
        return correlationKey;
    }

    public Object correlationValue() {
        return correlationValue;
    }
}
