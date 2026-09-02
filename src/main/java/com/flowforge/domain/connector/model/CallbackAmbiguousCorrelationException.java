package com.flowforge.domain.connector.model;

/**
 * More than one execution is waiting on {@code messageName}/{@code correlationKey}=value
 * (D-CALLBACK-MULTI-MATCH) -- a BPMN modeling error, never resolved by broadcasting.
 */
public class CallbackAmbiguousCorrelationException extends RuntimeException {

    private final String messageName;
    private final String correlationKey;
    private final Object correlationValue;
    private final int matchCount;

    public CallbackAmbiguousCorrelationException(
            String messageName, String correlationKey, Object correlationValue, int matchCount) {
        super(matchCount + " process executions waiting on message '" + messageName + "' with "
                + correlationKey + "=" + correlationValue + ", expected exactly one");
        this.messageName = messageName;
        this.correlationKey = correlationKey;
        this.correlationValue = correlationValue;
        this.matchCount = matchCount;
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

    public int matchCount() {
        return matchCount;
    }
}
