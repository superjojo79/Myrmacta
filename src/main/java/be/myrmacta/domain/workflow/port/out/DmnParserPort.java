package be.myrmacta.domain.workflow.port.out;

public interface DmnParserPort {

    /**
     * Parses and validates the given bytes as a DMN 1.3 document containing exactly one decision
     * table, and returns the {@code id} of that decision. Throws
     * {@link be.myrmacta.domain.workflow.model.InvalidDmnException} if the bytes are not
     * well-formed XML, not a valid DMN document, define anything other than exactly one decision
     * table, use a hit policy outside the allowlist, or contain an input/output cell expression
     * outside the JUEL allowlist (TODO §61 — same risk class as H1/H1-bis: Flowable DMN evaluates
     * cells via JUEL, not FEEL).
     */
    String extractDecisionKey(byte[] dmnXml);
}
