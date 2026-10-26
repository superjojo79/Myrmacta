package be.myrmacta.infrastructure.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;

/** TODO §82 (D6, D-MCP-LC-MODES) — tout outil MCP a un mode ; le mode conception n'expose aucune donnée de dossier. */
class McpToolModesTest {

    private static Set<String> registeredToolNames() throws Exception {
        // tous les types du paquet MCP, y compris ceux dont la présence est conditionnelle (outils de signature)
        var resolver = new org.springframework.core.io.support.PathMatchingResourcePatternResolver();
        Set<String> names = new TreeSet<>();
        for (var resource : resolver.getResources("classpath*:be/myrmacta/infrastructure/mcp/**/*.class")) {
            String path = resource.getURL().toString();
            String className = path.substring(path.indexOf("be/myrmacta/infrastructure/mcp"), path.length() - ".class".length())
                    .replace('/', '.');
            for (Method m : Class.forName(className).getDeclaredMethods()) {
                McpTool tool = m.getAnnotation(McpTool.class);
                if (tool != null) {
                    names.add(tool.name());
                }
            }
        }
        return names;
    }

    @Test
    void everyRegisteredToolHasAModeAndEveryModeHasATool() throws Exception {
        Set<String> registered = registeredToolNames();
        assertThat(registered).as("outils trouvés par balayage").hasSizeGreaterThan(30);
        assertThat(registered).as("outils sans mode : classez-les dans McpToolModes").allMatch(n -> McpToolModes.modeOf(n) != null);
        Set<String> classifiedButMissing = new TreeSet<>(McpToolModes.classifiedTools());
        classifiedButMissing.removeAll(registered);
        assertThat(classifiedButMissing).as("modes sans outil (nom erroné ou outil supprimé)").isEmpty();
    }

    @Test
    void designModeNeverExposesDossierDataTools() {
        Set<String> design = McpToolModes.toolsFor(McpToolModes.Mode.DESIGN);
        assertThat(design).doesNotContain("get_dossier", "search_dossiers", "create_dossier", "add_dossier_element",
                "start_workflow", "generate_document", "evaluate_decision", "query_sla_summary",
                "list_signature_requests", "get_signature_request", "cancel_signature_request",
                "run_dossier_precheck", "get_dossier_precheck"); // TODO §106 (D-ASSIST-TOOLS)
        assertThat(design).contains("deploy_workflow", "deploy_decision_table", "list_pending_drafts", "list_dossier_types");
    }

    @Test
    void operationModeNeverExposesConfigurationWriters() {
        Set<String> operation = McpToolModes.toolsFor(McpToolModes.Mode.OPERATION);
        assertThat(operation).doesNotContain("deploy_workflow", "deploy_decision_table", "redeploy_decision_table",
                "define_dossier_type", "define_connector_instance", "define_task_question_schema", "publish_draft",
                "export_configuration", "import_configuration");
        assertThat(operation).contains("search_dossiers", "get_dossier", "list_dossier_types");
    }
}
