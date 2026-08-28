package com.flowforge.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
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
}
