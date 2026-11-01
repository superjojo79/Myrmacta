package be.myrmacta.infrastructure.mcp;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * TODO §82 (D6, D-MCP-LC-MODES = A) — classement de chaque outil MCP dans le mode <b>conception</b> (lire le schéma, proposer
 * de la configuration en brouillon, tester à blanc) ou <b>opération</b> (lire et écrire des données de dossiers), ou les deux
 * (catalogues de référence sans donnée métier). Le mode conception n'expose <b>aucun</b> outil qui lit le contenu des
 * dossiers : c'est la mitigation principale d'une injection de consignes par des données (analyse 46) — l'assistant LC3
 * choisit son sous-ensemble ici.
 *
 * <p>Un test interdit un outil non classé (et un classement sans outil) : ajouter un outil, c'est choisir son mode.
 */
public final class McpToolModes {

    public enum Mode {
        /** Conception : configuration, catalogues, tests à blanc — jamais de données de dossiers. */
        DESIGN,
        /** Opération : données de dossiers et actions métier. */
        OPERATION,
        /** Catalogues de référence, utiles dans les deux modes. */
        BOTH
    }

    private static final Map<String, Mode> MODES = Map.ofEntries(
            // conception : écrire de la configuration (en brouillon) et la tester
            Map.entry("define_dossier_type", Mode.DESIGN),
            Map.entry("define_connector_instance", Mode.DESIGN),
            Map.entry("define_task_question_schema", Mode.DESIGN),
            Map.entry("deploy_workflow", Mode.DESIGN),
            Map.entry("deploy_decision_table", Mode.DESIGN),
            Map.entry("redeploy_decision_table", Mode.DESIGN),
            Map.entry("list_pending_drafts", Mode.DESIGN),
            Map.entry("publish_draft", Mode.DESIGN),
            Map.entry("define_document_template", Mode.DESIGN),
            Map.entry("preview_document", Mode.DESIGN),
            Map.entry("list_parcours", Mode.DESIGN),
            Map.entry("get_parcours", Mode.DESIGN),
            Map.entry("define_parcours", Mode.DESIGN),
            Map.entry("compile_parcours", Mode.DESIGN),
            Map.entry("export_configuration", Mode.DESIGN),
            Map.entry("import_configuration", Mode.DESIGN),
            Map.entry("test_decision", Mode.DESIGN),
            Map.entry("validate_expression", Mode.DESIGN),
            Map.entry("evaluate_expression", Mode.DESIGN),
            Map.entry("list_bpmn_building_blocks", Mode.DESIGN),
            Map.entry("list_connector_definitions", Mode.DESIGN),
            // les deux : catalogues et configuration en lecture, sans donnée de dossier
            Map.entry("list_dossier_types", Mode.BOTH),
            Map.entry("get_dossier_type", Mode.BOTH),
            Map.entry("list_workflow_types", Mode.BOTH),
            Map.entry("list_document_templates", Mode.BOTH),
            Map.entry("list_decision_tables", Mode.BOTH),
            Map.entry("get_decision_table", Mode.BOTH),
            Map.entry("list_decision_table_versions", Mode.BOTH),
            Map.entry("list_connector_instances", Mode.BOTH),
            Map.entry("get_connector_instance", Mode.BOTH),
            Map.entry("list_task_question_schemas", Mode.BOTH),
            Map.entry("get_task_question_schema", Mode.BOTH),
            Map.entry("evaluate_dossier_rules", Mode.BOTH),
            // opération : données de dossiers et actions métier
            Map.entry("search_dossiers", Mode.OPERATION),
            Map.entry("get_dossier", Mode.OPERATION),
            Map.entry("create_dossier", Mode.OPERATION),
            Map.entry("add_dossier_element", Mode.OPERATION),
            Map.entry("start_workflow", Mode.OPERATION),
            Map.entry("generate_document", Mode.OPERATION),
            Map.entry("evaluate_decision", Mode.OPERATION),
            Map.entry("query_sla_summary", Mode.OPERATION),
            Map.entry("list_signature_requests", Mode.OPERATION),
            Map.entry("get_signature_request", Mode.OPERATION),
            Map.entry("cancel_signature_request", Mode.OPERATION),
            // TODO §106 (issue #211) : pré-vérification IA (données de dossier) — jamais en conception.
            Map.entry("run_dossier_precheck", Mode.OPERATION),
            Map.entry("get_dossier_precheck", Mode.OPERATION),
            // TODO §113 (D-MAILROOM-MCP) : mail room — tâches, fil d'un dossier et actions gardées (données de dossiers).
            Map.entry("list_my_tasks", Mode.OPERATION),
            Map.entry("get_mail_thread", Mode.OPERATION),
            Map.entry("reassign_task_to_group", Mode.OPERATION),
            Map.entry("attach_mail_to_dossier", Mode.OPERATION),
            Map.entry("complete_task", Mode.OPERATION));

    private McpToolModes() {}

    /** Mode d'un outil ; {@code null} s'il n'est pas classé (ce que le test interdit). */
    public static Mode modeOf(String toolName) {
        return MODES.get(toolName);
    }

    /** Outils exposables dans ce mode : ceux du mode lui-même et les outils {@code BOTH}. */
    public static Set<String> toolsFor(Mode mode) {
        if (mode == Mode.BOTH) {
            throw new IllegalArgumentException("ask for DESIGN or OPERATION");
        }
        Set<String> names = new TreeSet<>();
        MODES.forEach((name, m) -> {
            if (m == mode || m == Mode.BOTH) {
                names.add(name);
            }
        });
        return names;
    }

    public static Set<String> classifiedTools() {
        return new TreeSet<>(MODES.keySet());
    }
}
