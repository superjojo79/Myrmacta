package com.flowforge.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class McpArgsTest {

    @Test
    void requireString_present_returnsValue() {
        Map<String, Object> args = Map.of("code", "CLAIM");

        assertThat(McpArgs.requireString(args, "code")).isEqualTo("CLAIM");
    }

    @Test
    void requireString_missing_throws() {
        Map<String, Object> args = Map.of();

        assertThatThrownBy(() -> McpArgs.requireString(args, "code"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("code is required");
    }

    @Test
    void requireString_null_throws() {
        Map<String, Object> args = new HashMap<>();
        args.put("code", null);

        assertThatThrownBy(() -> McpArgs.requireString(args, "code"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("code is required");
    }

    @Test
    void requireString_blank_throws() {
        Map<String, Object> args = Map.of("code", "   ");

        assertThatThrownBy(() -> McpArgs.requireString(args, "code"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("code is required");
    }

    @Test
    void requireString_wrongType_throws() {
        Map<String, Object> args = Map.of("code", 42);

        assertThatThrownBy(() -> McpArgs.requireString(args, "code"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("code is required");
    }

    @Test
    void requireBoolean_present_returnsValue() {
        Map<String, Object> args = Map.of("dryRun", true);

        assertThat(McpArgs.requireBoolean(args, "dryRun")).isTrue();
    }

    @Test
    void requireBoolean_missing_throws() {
        Map<String, Object> args = Map.of();

        assertThatThrownBy(() -> McpArgs.requireBoolean(args, "dryRun"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("dryRun is required");
    }

    @Test
    void requireBoolean_wrongType_throws() {
        Map<String, Object> args = Map.of("dryRun", "yes");

        assertThatThrownBy(() -> McpArgs.requireBoolean(args, "dryRun"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("dryRun is required");
    }

    @Test
    void requireUuid_valid_returnsUuid() {
        UUID id = UUID.randomUUID();

        assertThat(McpArgs.requireUuid(id.toString(), "dossierId")).isEqualTo(id);
    }

    @Test
    void requireUuid_blank_throws() {
        assertThatThrownBy(() -> McpArgs.requireUuid("  ", "dossierId"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("dossierId is required");
    }

    @Test
    void requireUuid_malformed_throws() {
        assertThatThrownBy(() -> McpArgs.requireUuid("not-a-uuid", "dossierId"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("dossierId must be a UUID");
    }

    @Test
    void optionalMap_null_returnsEmpty() {
        assertThat(McpArgs.optionalMap(null, "fields")).isEmpty();
    }

    @Test
    void optionalMap_map_returnsCopy() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("amount", 10);

        Map<String, Object> copy = McpArgs.optionalMap(fields, "fields");

        assertThat(copy).containsEntry("amount", 10);
        fields.put("amount", 99);
        assertThat(copy).containsEntry("amount", 10);
    }

    @Test
    void optionalMap_wrongType_throws() {
        assertThatThrownBy(() -> McpArgs.optionalMap("nope", "fields"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("fields must be an object");
    }
}
