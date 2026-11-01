package be.myrmacta.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.promotion.model.ArtifactType;
import be.myrmacta.domain.promotion.model.BundleEntry;
import be.myrmacta.domain.promotion.model.ConfigurationBundle;
import be.myrmacta.domain.promotion.model.ImportPlan;
import be.myrmacta.domain.promotion.model.ImportReport;
import be.myrmacta.domain.promotion.model.InvalidBundleException;
import be.myrmacta.domain.promotion.port.in.ExportConfigurationUseCase;
import be.myrmacta.domain.promotion.port.in.ImportConfigurationUseCase;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import be.myrmacta.infrastructure.mcp.McpActorScope;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** TODO §83 (D4, D-PROMO-MCP) — export en lecture, import en plan par défaut, aucun secret accepté. */
class ConfigurationPromotionToolsTest {

    private static final AccessContext.User AGENT = AccessContext.user("agent-1", Set.of("API_SERVICE"));
    private final McpAccessContextFactory factory = new McpAccessContextFactory();

    /** TODO §117 (D-PROMO-MCP-TEMPLATE, revue de clôture #279 M2) — jamais de corps HTML de template par MCP. */
    @Test
    void exportListsTemplatesWithoutTheirHtml() {
        ExportConfigurationUseCase export = mock(ExportConfigurationUseCase.class);
        var bundle = new ConfigurationBundle(2, Instant.parse("2026-11-01T10:00:00Z"), "recette", "v", List.of(
                new BundleEntry(ArtifactType.DMN, "grid", "<dmn/>".getBytes(StandardCharsets.UTF_8)),
                new BundleEntry(ArtifactType.DOCUMENT_TEMPLATE, "accuse._.fr",
                        "{\"htmlContent\":\"<p>secret-html</p>\"}".getBytes(StandardCharsets.UTF_8))));
        when(export.export(any(), eq("recette"))).thenReturn(bundle);

        Map<String, Object> result = McpActorScope.call(AGENT, () -> new ExportConfigurationTool(export, factory).exportConfiguration("recette"));

        assertThat(result.toString()).doesNotContain("secret-html");
        @SuppressWarnings("unchecked")
        var entries = (List<Map<String, Object>>) result.get("entries");
        assertThat(entries).extracting(e -> e.get("type")).containsExactly("DMN");
        @SuppressWarnings("unchecked")
        var omitted = (List<Map<String, Object>>) result.get("omitted");
        assertThat(omitted).singleElement().satisfies(o -> {
            assertThat(o).containsEntry("type", "DOCUMENT_TEMPLATE").containsEntry("key", "accuse._.fr").containsKey("sha256");
            assertThat(o.get("reason").toString()).contains("ZIP export");
        });
        assertThat(result).containsEntry("bundleSha256", bundle.sha256());
    }

    @Test
    void exportReturnsTheFilesAsTextWithTheirChecksums() {
        ExportConfigurationUseCase export = mock(ExportConfigurationUseCase.class);
        var bundle = new ConfigurationBundle(1, Instant.parse("2026-10-01T10:00:00Z"), "recette", "v", List.of(
                new BundleEntry(ArtifactType.DMN, "grid", "<dmn/>".getBytes(StandardCharsets.UTF_8))));
        when(export.export(any(), eq("recette"))).thenReturn(bundle);

        Map<String, Object> result = McpActorScope.call(AGENT, () -> new ExportConfigurationTool(export, factory).exportConfiguration("recette"));

        assertThat(result).containsEntry("formatVersion", 1).containsEntry("sourceLabel", "recette")
                .containsEntry("bundleSha256", bundle.sha256());
        @SuppressWarnings("unchecked")
        var entries = (List<Map<String, Object>>) result.get("entries");
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst()).containsEntry("type", "DMN").containsEntry("key", "grid").containsEntry("content", "<dmn/>");
    }

    @Test
    void anExportTooLargeForAnAgentIsRefusedWithAPointerToTheAdminScreen() {
        ExportConfigurationUseCase export = mock(ExportConfigurationUseCase.class);
        String big = "x".repeat(ExportConfigurationTool.MAX_TOTAL_CHARS / 2 + 1);
        var bundle = new ConfigurationBundle(1, Instant.now(), "", "", List.of(
                new BundleEntry(ArtifactType.DMN, "a", big.getBytes(StandardCharsets.UTF_8)),
                new BundleEntry(ArtifactType.DMN, "b", big.getBytes(StandardCharsets.UTF_8))));
        when(export.export(any(), any())).thenReturn(bundle);
        assertThatThrownBy(() -> McpActorScope.call(AGENT, () -> new ExportConfigurationTool(export, factory).exportConfiguration(null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("admin screen");
    }

    private static List<Map<String, Object>> entries() {
        return List.of(Map.of("type", "dmn", "key", "grid", "content", "<dmn/>"));
    }

    @Test
    void importIsADryRunPlanByDefault() {
        ImportConfigurationUseCase use = mock(ImportConfigurationUseCase.class);
        var plan = new ImportPlan("sha", 1, "", List.of());
        when(use.plan(any(), any(), any())).thenReturn(plan);

        Map<String, Object> result = McpActorScope.call(AGENT, () -> new ImportConfigurationTool(use, factory)
                .importConfiguration(entries(), "recette", Map.of("pk", 42), null, null));

        assertThat(result).containsKey("plan").doesNotContainKey("report");
        verify(use).plan(any(), any(), eq(Map.of("pk", "42")));
        verify(use, org.mockito.Mockito.never()).apply(any(), any(), any(), any(), any(), any());
    }

    @Test
    void applyUsesTheMcpChannelWithNoSecretAndTheReviewedChecksum() {
        ImportConfigurationUseCase use = mock(ImportConfigurationUseCase.class);
        when(use.apply(any(), any(), any(), any(), any(), any())).thenReturn(new ImportReport("sha", Instant.now(), List.of()));

        McpActorScope.call(AGENT, () -> new ImportConfigurationTool(use, factory)
                .importConfiguration(entries(), null, null, true, "reviewed-sha"));

        verify(use).apply(any(), any(), eq(Map.of()), eq(Map.of()), eq("reviewed-sha"), eq(ImportConfigurationUseCase.Channel.MCP));
    }

    @Test
    void malformedEntriesAreRefused() {
        ImportConfigurationUseCase use = mock(ImportConfigurationUseCase.class);
        var tool = new ImportConfigurationTool(use, factory);
        assertThatThrownBy(() -> McpActorScope.call(AGENT, () -> tool.importConfiguration(null, null, null, null, null)))
                .isInstanceOf(InvalidBundleException.class);
        assertThatThrownBy(() -> McpActorScope.call(AGENT, () -> tool.importConfiguration(
                List.of(Map.of("type", "NOPE", "key", "k", "content", "x")), null, null, null, null)))
                .isInstanceOf(InvalidBundleException.class).hasMessageContaining("unknown artifact type");
        assertThatThrownBy(() -> McpActorScope.call(AGENT, () -> tool.importConfiguration(
                List.of(Map.of("type", "DMN", "key", "../x", "content", "x")), null, null, null, null)))
                .isInstanceOf(InvalidBundleException.class);
        assertThatThrownBy(() -> McpActorScope.call(AGENT, () -> tool.importConfiguration(
                List.of(Map.of("type", "DMN", "key", "k")), null, null, null, null)))
                .isInstanceOf(InvalidBundleException.class);
        org.mockito.Mockito.verifyNoInteractions(use);
    }
}
