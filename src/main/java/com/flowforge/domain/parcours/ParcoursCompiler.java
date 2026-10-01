package com.flowforge.domain.parcours;

import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.parcours.model.CompiledParcours;
import com.flowforge.domain.parcours.model.InvalidParcoursException;
import com.flowforge.domain.parcours.model.ParcoursDefinition;
import com.flowforge.domain.parcours.model.ParcoursDefinition.Branch;
import com.flowforge.domain.parcours.model.ParcoursDefinition.Kind;
import com.flowforge.domain.parcours.model.ParcoursDefinition.Step;
import com.flowforge.domain.workflow.model.WorkflowStatusDefinition;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * TODO §88 (LC5) — compile un parcours en BPMN, de façon <b>déterministe</b> (même parcours, même XML octet pour octet) et
 * uniquement avec les constructions que la liste blanche accepte déjà : briques du catalogue (paramètres = attributs
 * {@code flowable:*} littéraux), passerelle exclusive à conditions simples, minuteur à durée littérale. Aucun {@code ${…}} n'est
 * jamais écrit hors des conditions de passerelle. Les contrôles de forme et d'existence des briques restent ceux du dépôt : le XML
 * est validé par le chemin habituel avant d'être déposé en brouillon ; ici on garantit un graphe cohérent (étapes atteignables,
 * sortie possible, références de statuts) et des messages qui nomment l'étape.
 */
public final class ParcoursCompiler {

    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final Pattern DURATION = Pattern.compile("P(?:\\d{1,3}D)?(?:T(?=\\d)(?:\\d{1,4}H)?(?:\\d{1,4}M)?)?");
    private static final Pattern SCHEMA_KEY = Pattern.compile("[A-Za-z0-9_.:-]{1,100}");
    private static final Set<String> RESERVED_IDS = Set.of("start", "end");
    private static final Set<String> DOCUMENT_KEYS = Set.of("documentType", "documentVariant", "documentLocale", "attach");
    private static final Set<String> NOTIFY_KEYS = Set.of("notificationChannels", "notificationTo", "notificationSubject",
            "notificationBody", "notificationUserId", "notificationWebhookConfigId");

    private ParcoursCompiler() {}

    /** @param schemaFields champs (donc variables produites) d'un schéma de question, pour les avertissements de variables */
    public static CompiledParcours compile(
            String processKey, String label, ParcoursDefinition definition, Function<String, Set<String>> schemaFields) {
        List<Step> steps = definition.steps();
        if (processKey == null || !com.flowforge.domain.parcours.model.Parcours.KEY.matcher(processKey).matches()) {
            throw new InvalidParcoursException("the process key is camelCase (letters and digits, starting with a lowercase letter)");
        }
        if (RESERVED_IDS.contains(processKey) || steps.stream().anyMatch(s -> s.id().equals(processKey))) {
            throw new InvalidParcoursException("the process key '" + processKey + "' clashes with a step id or a reserved id");
        }
        if (label == null || label.isBlank() || label.length() > 200 || label.contains("${") || label.contains("#{")
                || label.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidParcoursException("the parcours label is a single line of plain text (max 200)");
        }
        if (steps.isEmpty()) {
            throw new InvalidParcoursException("a parcours needs at least one step");
        }
        Map<String, Step> byId = index(steps);
        validateStatuses(definition);
        for (Step step : steps) {
            validateStep(step, byId, definition);
        }
        Graph graph = graph(steps, byId);
        checkReachabilityAndExit(steps, graph);
        List<String> warnings = warnings(steps, schemaFields);
        return new Writer(processKey, label, steps, graph, warnings).write();
    }

    // ---------------------------------------------------------------- contrôles

    private static Map<String, Step> index(List<Step> steps) {
        Map<String, Step> byId = new LinkedHashMap<>();
        Set<String> taken = new HashSet<>(RESERVED_IDS);
        for (Step step : steps) {
            if (RESERVED_IDS.contains(step.id())) {
                throw new InvalidParcoursException("step id '" + step.id() + "' is reserved (start, end)");
            }
            if (byId.put(step.id(), step) != null) {
                throw new InvalidParcoursException("duplicate step id: " + step.id());
            }
        }
        for (Step step : steps) {
            if (step.kind() == Kind.DOCUMENT && byId.containsKey(step.id() + "Attach")) {
                throw new InvalidParcoursException("step id '" + step.id() + "Attach' clashes with the attachment of step '" + step.id() + "'");
            }
            taken.add(step.id());
        }
        return byId;
    }

    private static void validateStatuses(ParcoursDefinition definition) {
        if (definition.allowedDossierStatuses().isEmpty()) {
            throw new InvalidParcoursException("allowedDossierStatuses must not be empty");
        }
        if (definition.statuses().isEmpty()) {
            throw new InvalidParcoursException("a parcours declares its workflow statuses (exactly one initial)");
        }
        long initial = definition.statuses().stream().filter(WorkflowStatusDefinition::initial).count();
        if (initial != 1) {
            throw new InvalidParcoursException("exactly one workflow status must be initial (found " + initial + ")");
        }
        Set<String> codes = new HashSet<>();
        for (WorkflowStatusDefinition s : definition.statuses()) {
            if (!codes.add(s.code())) {
                throw new InvalidParcoursException("duplicate workflow status: " + s.code());
            }
        }
    }

    private static void validateStep(Step step, Map<String, Step> byId, ParcoursDefinition definition) {
        for (Map.Entry<String, String> param : step.params().entrySet()) {
            String value = param.getValue();
            if (value.contains("${") || value.contains("#{")) {
                throw new InvalidParcoursException("step " + step.id() + ": parameter " + param.getKey()
                        + " must be a literal (expressions ${…} are not allowed)");
            }
            if (value.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t' && c != '\r')) {
                throw new InvalidParcoursException("step " + step.id() + ": parameter " + param.getKey() + " contains control characters");
            }
            if (!param.getKey().matches("[A-Za-z][A-Za-z0-9]{0,39}")) {
                throw new InvalidParcoursException("step " + step.id() + ": invalid parameter name " + param.getKey());
            }
        }
        requireTarget(step, step.next(), byId, "next");
        requireTarget(step, step.defaultNext(), byId, "defaultNext");
        for (Branch branch : step.branches()) {
            requireTarget(step, branch.next(), byId, "a branch");
        }
        if (step.kind() != Kind.DECISION && (!step.branches().isEmpty() || step.defaultNext() != null)) {
            throw new InvalidParcoursException("step " + step.id() + ": only a DECISION has branches or defaultNext");
        }
        switch (step.kind()) {
            case QUESTION -> {
                allowOnly(step, Set.of("schemaKey"));
                requireKey(step, "schemaKey");
            }
            case EXTERNAL -> {
                allowOnly(step, Set.of("schemaKey", "portalRecipient", "portalLinkTtlDays"));
                requireKey(step, "schemaKey");
                require(step, "portalRecipient");
                optionalInt(step, "portalLinkTtlDays", 1, 365);
            }
            case DOCUMENT -> {
                allowOnly(step, DOCUMENT_KEYS);
                require(step, "documentType");
                String attach = step.params().get("attach");
                if (attach != null && !attach.equals("true") && !attach.equals("false")) {
                    throw new InvalidParcoursException("step " + step.id() + ": attach is true or false");
                }
            }
            case NOTIFY -> {
                allowOnly(step, NOTIFY_KEYS);
                require(step, "notificationChannels");
            }
            case CONNECTOR -> {
                allowOnly(step, Set.of("connectorInstanceId"));
                require(step, "connectorInstanceId");
            }
            case DMN -> {
                allowOnly(step, Set.of("decisionTableReferenceKey"));
                requireKey(step, "decisionTableReferenceKey");
            }
            case EXPRESSION -> {
                allowOnly(step, Set.of("formula", "formulaResult"));
                require(step, "formula");
                String result = require(step, "formulaResult");
                if (!ParcoursDefinition.VARIABLE.matcher(result).matches()) {
                    throw new InvalidParcoursException("step " + step.id() + ": formulaResult is a plain variable name");
                }
            }
            case MAP_FIELDS -> {
                allowOnly(step, Set.of("fieldMapping"));
                require(step, "fieldMapping");
            }
            case SET_WORKFLOW_STATUS -> {
                allowOnly(step, Set.of("targetWorkflowStatus"));
                String code = require(step, "targetWorkflowStatus");
                if (definition.statuses().stream().noneMatch(s -> s.code().equals(code))) {
                    throw new InvalidParcoursException("step " + step.id() + ": workflow status '" + code + "' is not declared");
                }
            }
            case SET_DOSSIER_STATUS -> {
                allowOnly(step, Set.of("targetDossierStatus"));
                String name = require(step, "targetDossierStatus");
                try {
                    DossierStatus.valueOf(name);
                } catch (IllegalArgumentException ex) {
                    throw new InvalidParcoursException("step " + step.id() + ": unknown dossier status '" + name + "'");
                }
            }
            case WAIT -> {
                allowOnly(step, Set.of("duration"));
                String duration = require(step, "duration");
                if (!DURATION.matcher(duration).matches() || duration.equals("P") || !duration.matches(".*[1-9].*")) {
                    throw new InvalidParcoursException("step " + step.id() + ": duration is an ISO 8601 duration such as PT24H, PT30M or P2D");
                }
            }
            case DECISION -> {
                allowOnly(step, Set.of());
                if (step.branches().isEmpty()) {
                    throw new InvalidParcoursException("step " + step.id() + ": a decision needs at least one branch");
                }
                if (step.defaultNext() == null) {
                    throw new InvalidParcoursException("step " + step.id() + ": a decision needs a defaultNext (the path when no branch matches)");
                }
                if (step.next() != null) {
                    throw new InvalidParcoursException("step " + step.id() + ": a decision uses branches and defaultNext, not next");
                }
            }
        }
    }

    private static void requireTarget(Step step, String target, Map<String, Step> byId, String what) {
        if (target != null && !"end".equals(target) && !byId.containsKey(target)) {
            throw new InvalidParcoursException("step " + step.id() + ": " + what + " points to an unknown step '" + target + "'");
        }
    }

    private static void allowOnly(Step step, Set<String> allowed) {
        for (String key : step.params().keySet()) {
            if (!allowed.contains(key)) {
                throw new InvalidParcoursException("step " + step.id() + ": parameter '" + key + "' does not apply to " + step.kind());
            }
        }
    }

    private static String require(Step step, String name) {
        String value = step.params().get(name);
        if (value == null) {
            throw new InvalidParcoursException("step " + step.id() + ": parameter '" + name + "' is required for " + step.kind());
        }
        return value;
    }

    private static void requireKey(Step step, String name) {
        if (!SCHEMA_KEY.matcher(require(step, name)).matches()) {
            throw new InvalidParcoursException("step " + step.id() + ": parameter '" + name + "' has an invalid format");
        }
    }

    private static void optionalInt(Step step, String name, int min, int max) {
        String value = step.params().get(name);
        if (value == null) {
            return;
        }
        try {
            int n = Integer.parseInt(value.trim());
            if (n < min || n > max) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            throw new InvalidParcoursException("step " + step.id() + ": " + name + " is a whole number between " + min + " and " + max);
        }
    }

    private static boolean attach(Step step) {
        return !"false".equals(step.params().get("attach"));
    }

    // ---------------------------------------------------------------- graphe

    /** Arêtes sortantes de chaque étape, dans l'ordre : suivant naturel, ou {@code next}, ou les branches puis le défaut. */
    private record Edge(String from, String to, Branch branch, boolean isDefault) {}

    private record Graph(Map<String, List<Edge>> out) {}

    private static Graph graph(List<Step> steps, Map<String, Step> byId) {
        Map<String, List<Edge>> out = new LinkedHashMap<>();
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            List<Edge> edges = new ArrayList<>();
            if (step.kind() == Kind.DECISION) {
                for (Branch b : step.branches()) {
                    edges.add(new Edge(step.id(), b.next(), b, false));
                }
                edges.add(new Edge(step.id(), step.defaultNext(), null, true));
            } else {
                String to = step.next() != null ? step.next() : i + 1 < steps.size() ? steps.get(i + 1).id() : "end";
                edges.add(new Edge(step.id(), to, null, false));
            }
            out.put(step.id(), edges);
        }
        return new Graph(out);
    }

    private static void checkReachabilityAndExit(List<Step> steps, Graph graph) {
        Set<String> seen = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(steps.getFirst().id());
        seen.add(steps.getFirst().id());
        boolean reachesEnd = false;
        while (!queue.isEmpty()) {
            for (Edge e : graph.out().get(queue.poll())) {
                if ("end".equals(e.to())) {
                    reachesEnd = true;
                } else if (seen.add(e.to())) {
                    queue.add(e.to());
                }
            }
        }
        for (Step step : steps) {
            if (!seen.contains(step.id())) {
                throw new InvalidParcoursException("step " + step.id() + " is unreachable from the first step");
            }
        }
        if (!reachesEnd) {
            throw new InvalidParcoursException("the parcours never ends: no path leads to the end");
        }
        // Chaque étape doit pouvoir atteindre la fin : sinon une boucle sans sortie piégerait le dossier.
        Map<String, Set<String>> reverse = new HashMap<>();
        graph.out().forEach((from, edges) -> edges.forEach(e -> reverse.computeIfAbsent(e.to(), k -> new HashSet<>()).add(from)));
        Set<String> canEnd = new HashSet<>();
        ArrayDeque<String> back = new ArrayDeque<>();
        back.add("end");
        while (!back.isEmpty()) {
            for (String from : reverse.getOrDefault(back.poll(), Set.of())) {
                if (canEnd.add(from)) {
                    back.add(from);
                }
            }
        }
        for (Step step : steps) {
            if (!canEnd.contains(step.id())) {
                throw new InvalidParcoursException("step " + step.id() + " is in a loop with no way out");
            }
        }
    }

    private static List<String> warnings(List<Step> steps, Function<String, Set<String>> schemaFields) {
        List<String> warnings = new ArrayList<>();
        boolean unknownProducers = steps.stream().anyMatch(s -> s.kind() == Kind.DMN || s.kind() == Kind.CONNECTOR || s.kind() == Kind.MAP_FIELDS);
        Set<String> produced = new HashSet<>();
        for (Step s : steps) {
            if (s.kind() == Kind.EXPRESSION) {
                produced.add(s.params().get("formulaResult"));
            }
            if (s.kind() == Kind.QUESTION || s.kind() == Kind.EXTERNAL) {
                Set<String> fields = schemaFields == null ? null : schemaFields.apply(s.params().get("schemaKey"));
                if (fields == null) {
                    unknownProducers = true;
                    warnings.add("step " + s.id() + ": the question schema '" + s.params().get("schemaKey") + "' was not found");
                } else {
                    produced.addAll(fields);
                }
            }
        }
        if (!unknownProducers) {
            for (Step s : steps) {
                for (Branch b : s.branches()) {
                    if (!produced.contains(b.variable())) {
                        warnings.add("step " + s.id() + ": no step produces the variable '" + b.variable() + "' (question field or expression result)");
                    }
                }
            }
        }
        return warnings;
    }

    // ---------------------------------------------------------------- écriture BPMN + diagramme

    private record Node(String id, String name, String tag, int width, int height) {}

    private static final class Writer {
        final String key;
        final String label;
        final List<Step> steps;
        final Graph graph;
        final List<String> warnings;
        final StringBuilder process = new StringBuilder();
        final List<Node> nodes = new ArrayList<>();
        final Map<String, String> entryOf = new HashMap<>(); // id d'étape → id du premier élément BPMN
        final Map<String, String> exitOf = new HashMap<>(); // id d'étape → id du dernier élément BPMN
        final List<String[]> flows = new ArrayList<>(); // {id, from, to}
        final StringBuilder flowXml = new StringBuilder();
        final Set<String> flowIds = new HashSet<>();

        Writer(String key, String label, List<Step> steps, Graph graph, List<String> warnings) {
            this.key = key;
            this.label = label;
            this.steps = steps;
            this.graph = graph;
            this.warnings = warnings;
        }

        CompiledParcours write() {
            nodes.add(new Node("start", "Début", "startEvent", 36, 36));
            for (Step step : steps) {
                element(step);
            }
            nodes.add(new Node("end", "Fin", "endEvent", 36, 36));
            entryOf.put("end", "end");
            exitOf.put("end", "end");
            // Flux : début → première étape ; chaînage interne d'une étape double (document + joindre) ; arêtes du graphe.
            flow("start", entryOf.get(steps.getFirst().id()), null);
            for (Step step : steps) {
                if (step.kind() == Kind.DOCUMENT && attach(step)) {
                    flow(step.id(), step.id() + "Attach", null);
                }
            }
            for (Step step : steps) {
                String from = exitOf.get(step.id());
                for (Edge e : graph.out().get(step.id())) {
                    flow(from, entryOf.get(e.to()), e);
                }
            }
            String xml = document();
            return new CompiledParcours(xml, sha256(xml), warnings, nodes.size());
        }

        private void element(Step step) {
            String id = step.id();
            String name = esc(step.label());
            Map<String, String> p = step.params();
            entryOf.put(id, id);
            exitOf.put(id, id);
            switch (step.kind()) {
                case QUESTION -> task(id, "userTask", name, " flowable:formKey=\"user-question:" + esc(p.get("schemaKey")) + "\"", "");
                case EXTERNAL -> task(id, "userTask", name,
                        " flowable:formKey=\"user-question:" + esc(p.get("schemaKey")) + "\" flowable:portalRecipient=\"" + esc(p.get("portalRecipient")) + "\""
                                + (p.containsKey("portalLinkTtlDays") ? " flowable:portalLinkTtlDays=\"" + esc(p.get("portalLinkTtlDays").trim()) + "\"" : ""), "");
                case DOCUMENT -> {
                    task(id, "serviceTask", name, delegate("documentGenerationTask") + attrs(p, List.of("documentType", "documentVariant", "documentLocale")), "");
                    if (attach(step)) {
                        task(id + "Attach", "serviceTask", esc(step.label()) + " (jointure)", delegate("attachGeneratedDocumentTask"), "");
                        exitOf.put(id, id + "Attach");
                    }
                }
                case NOTIFY -> task(id, "serviceTask", name, delegate("notificationDispatchTask") + attrs(p,
                        List.of("notificationChannels", "notificationTo", "notificationSubject", "notificationBody", "notificationUserId", "notificationWebhookConfigId")), "");
                case CONNECTOR -> task(id, "serviceTask", name, delegate("connectorServiceTask") + attrs(p, List.of("connectorInstanceId")), "");
                case DMN -> task(id, "serviceTask", name, " flowable:type=\"dmn\"",
                        "<extensionElements><flowable:field name=\"decisionTableReferenceKey\"><flowable:string>"
                                + esc(p.get("decisionTableReferenceKey")) + "</flowable:string></flowable:field></extensionElements>");
                case EXPRESSION -> task(id, "serviceTask", name, delegate("businessExpressionTask") + attrs(p, List.of("formula", "formulaResult")), "");
                case MAP_FIELDS -> task(id, "serviceTask", name, delegate("fieldMappingTask") + attrs(p, List.of("fieldMapping")), "");
                case SET_WORKFLOW_STATUS -> task(id, "serviceTask", name, delegate("updateWorkflowLinkStatusTask") + attrs(p, List.of("targetWorkflowStatus")), "");
                case SET_DOSSIER_STATUS -> task(id, "serviceTask", name, delegate("updateDossierStatusTask") + attrs(p, List.of("targetDossierStatus")), "");
                case WAIT -> {
                    nodes.add(new Node(id, step.label(), "intermediateCatchEvent", 36, 36));
                    process.append("    <intermediateCatchEvent id=\"").append(id).append("\" name=\"").append(name).append("\">\n")
                            .append("      <timerEventDefinition><timeDuration>").append(esc(p.get("duration"))).append("</timeDuration></timerEventDefinition>\n")
                            .append("    </intermediateCatchEvent>\n");
                }
                case DECISION -> {
                    nodes.add(new Node(id, step.label(), "exclusiveGateway", 50, 50));
                    // Le flux par défaut est connu après la création des flux : le gateway est écrit dans document().
                }
            }
        }

        private static String delegate(String bean) {
            return " flowable:delegateExpression=\"${" + bean + "}\"";
        }

        private static String attrs(Map<String, String> params, List<String> names) {
            StringBuilder sb = new StringBuilder();
            for (String name : names) {
                if (params.containsKey(name)) {
                    sb.append(" flowable:").append(name).append("=\"").append(esc(params.get(name))).append("\"");
                }
            }
            return sb.toString();
        }

        private void task(String id, String tag, String name, String attributes, String inner) {
            nodes.add(new Node(id, unesc(name), tag, 100, 80));
            process.append("    <").append(tag).append(" id=\"").append(id).append("\" name=\"").append(name).append("\"").append(attributes);
            if (inner.isEmpty()) {
                process.append("/>\n");
            } else {
                process.append(">").append(inner).append("</").append(tag).append(">\n");
            }
        }

        private void flow(String fromNode, String toNode, Edge edge) {
            String base = "flow_" + fromNode + "_" + toNode;
            // Deux branches vers la même étape : identifiant unique (suffixe _2, _3…).
            String unique = base;
            for (int n = 2; flowIds.contains(unique); n++) {
                unique = base + "_" + n;
            }
            flowIds.add(unique);
            flows.add(new String[] {unique, fromNode, toNode});
            flowXml.append("    <sequenceFlow id=\"").append(unique).append("\" sourceRef=\"").append(fromNode).append("\" targetRef=\"").append(toNode).append("\"");
            if (edge != null && edge.branch() != null) {
                Branch b = edge.branch();
                flowXml.append(">\n      <conditionExpression xsi:type=\"tFormalExpression\">${").append(b.variable()).append(' ')
                        .append(b.operator().symbol().replace("<", "&lt;").replace(">", "&gt;")).append(' ').append(esc(literal(b.value())))
                        .append("}</conditionExpression>\n    </sequenceFlow>\n");
            } else {
                flowXml.append("/>\n");
            }
            if (edge != null && edge.isDefault()) {
                defaults.put(edge.from(), unique);
            }
        }

        final Map<String, String> defaults = new HashMap<>();

        private static String literal(String value) {
            return value.matches("\\d+|true|false") ? value : "'" + value + "'";
        }

        private String document() {
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<definitions xmlns=\"").append(BPMN_NS).append("\"\n")
                    .append("             xmlns:flowable=\"http://flowable.org/bpmn\"\n")
                    .append("             xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n")
                    .append("             xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\"\n")
                    .append("             xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\"\n")
                    .append("             xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\"\n")
                    .append("             targetNamespace=\"http://flowforge/parcours\">\n");
            xml.append("  <process id=\"").append(esc(key)).append("\" name=\"").append(esc(label)).append("\" isExecutable=\"true\">\n");
            xml.append("    <startEvent id=\"start\" name=\"Début\"/>\n");
            // Les éléments, avec le flux par défaut des passerelles.
            String body = process.toString();
            for (Step step : steps) {
                if (step.kind() == Kind.DECISION) {
                    body = body + "    <exclusiveGateway id=\"" + step.id() + "\" name=\"" + esc(step.label()) + "\" default=\"" + defaults.get(step.id()) + "\"/>\n";
                }
            }
            xml.append(body);
            xml.append("    <endEvent id=\"end\" name=\"Fin\"/>\n");
            xml.append(flowXml);
            xml.append("  </process>\n");
            xml.append(diagram());
            xml.append("</definitions>\n");
            return xml.toString();
        }

        private String diagram() {
            Map<String, int[]> pos = new LinkedHashMap<>(); // id → {x, y, w, h} (coin haut-gauche)
            int cursor = 40;
            int centerY = 200;
            List<Node> ordered = new ArrayList<>();
            ordered.add(nodes.getFirst()); // start
            for (Step step : steps) {
                for (Node n : nodes) {
                    if (n.id().equals(step.id()) || (n.id().equals(step.id() + "Attach"))) {
                        ordered.add(n);
                    }
                }
            }
            ordered.add(nodes.stream().filter(n -> n.id().equals("end")).findFirst().orElseThrow());
            for (Node n : ordered) {
                pos.put(n.id(), new int[] {cursor, centerY - n.height() / 2, n.width(), n.height()});
                cursor += n.width() + 60;
            }
            List<String> order = new ArrayList<>(pos.keySet());
            StringBuilder d = new StringBuilder();
            d.append("  <bpmndi:BPMNDiagram id=\"BPMNDiagram_").append(key).append("\">\n")
                    .append("    <bpmndi:BPMNPlane id=\"BPMNPlane_").append(key).append("\" bpmnElement=\"").append(key).append("\">\n");
            for (Map.Entry<String, int[]> e : pos.entrySet()) {
                int[] b = e.getValue();
                d.append("      <bpmndi:BPMNShape id=\"Shape_").append(e.getKey()).append("\" bpmnElement=\"").append(e.getKey()).append("\">\n")
                        .append("        <dc:Bounds x=\"").append(b[0]).append("\" y=\"").append(b[1]).append("\" width=\"").append(b[2]).append("\" height=\"").append(b[3]).append("\"/>\n")
                        .append("      </bpmndi:BPMNShape>\n");
            }
            int detour = 0;
            for (String[] f : flows) {
                int[] a = pos.get(f[1]);
                int[] t = pos.get(f[2]);
                int ia = order.indexOf(f[1]);
                int it = order.indexOf(f[2]);
                d.append("      <bpmndi:BPMNEdge id=\"Edge_").append(f[0]).append("\" bpmnElement=\"").append(f[0]).append("\">\n");
                if (it == ia + 1) {
                    point(d, a[0] + a[2], a[1] + a[3] / 2);
                    point(d, t[0], t[1] + t[3] / 2);
                } else {
                    // Saut en avant ou retour en arrière : contourne par le bas, à une profondeur propre à chaque arête.
                    int depth = centerY + 90 + 22 * (detour++ % 8);
                    point(d, a[0] + a[2] / 2, a[1] + a[3]);
                    point(d, a[0] + a[2] / 2, depth);
                    point(d, t[0] + t[2] / 2, depth);
                    point(d, t[0] + t[2] / 2, t[1] + t[3]);
                }
                d.append("      </bpmndi:BPMNEdge>\n");
            }
            d.append("    </bpmndi:BPMNPlane>\n  </bpmndi:BPMNDiagram>\n");
            return d.toString();
        }

        private static void point(StringBuilder d, int x, int y) {
            d.append("        <di:waypoint x=\"").append(x).append("\" y=\"").append(y).append("\"/>\n");
        }
    }

    private static String esc(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("\n", "&#10;").replace("\r", "&#13;").replace("\t", "&#9;");
    }

    private static String unesc(String value) {
        return value.replace("&quot;", "\"").replace("&gt;", ">").replace("&lt;", "<").replace("&amp;", "&");
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
