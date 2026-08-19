package com.flowforge.domain.dossier.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PurgeOutboxPayloadsTest {

    @Test
    void json_escapesBackslashAndQuote() {
        String json = PurgeOutboxPayloads.json(
                "tenant\\with\"quote", UUID.randomUUID(), "TYPE", Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(json).contains("\"tenantId\":\"tenant\\\\with\\\"quote\"");
    }

    @Test
    void json_escapesNamedControlCharactersWithShortForms() {
        String json = PurgeOutboxPayloads.json(
                "tenant_dev", UUID.randomUUID(), "A\nB\rC\tD\bE\fF", Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(json).contains("\"typeCode\":\"A\\nB\\rC\\tD\\bE\\fF\"");
    }

    @Test
    void json_escapesOtherControlCharactersAsUnicodeSequence() {
        // SOH (decimal 1) has no short-form JSON escape (RFC 8259) — must fall back to a 4-digit
        // hex escape sequence.
        String startOfHeading = Character.toString(1);
        String typeCode = "A" + startOfHeading + "B";
        String json = PurgeOutboxPayloads.json(
                "tenant_dev", UUID.randomUUID(), typeCode, Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(json).contains("\"typeCode\":\"A" + "\\u0001" + "B\"");
    }

    @Test
    void json_neverContainsARawUnescapedControlCharacter() {
        // A raw control char here would previously produce syntactically invalid JSON — the
        // failure mode that broke the retention job's single-transaction batch (TODO §38).
        String json = PurgeOutboxPayloads.json(
                controlCharsString(), UUID.randomUUID(), "TYPE", Instant.parse("2026-01-01T00:00:00Z"));

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            assertThat((int) c)
                    .withFailMessage(
                            "payload_json must not contain a raw control character (found 0x%02x at %d)",
                            (int) c, i)
                    .isGreaterThanOrEqualTo(0x20);
        }
    }

    @Test
    void parseTenantId_and_parseEligibleAt_roundTripThroughJson() {
        UUID dossierId = UUID.randomUUID();
        Instant eligibleAt = Instant.parse("2026-03-15T10:30:00Z");

        String json = PurgeOutboxPayloads.json("tenant_dev", dossierId, "TYPE", eligibleAt);

        assertThat(PurgeOutboxPayloads.parseTenantId(json)).isEqualTo("tenant_dev");
        assertThat(PurgeOutboxPayloads.parseEligibleAt(json)).isEqualTo(eligibleAt);
    }

    private static String controlCharsString() {
        StringBuilder sb = new StringBuilder();
        for (char c = 0; c < 0x20; c++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
