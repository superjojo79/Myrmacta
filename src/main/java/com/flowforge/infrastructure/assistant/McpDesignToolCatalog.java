package com.flowforge.infrastructure.assistant;

import com.flowforge.domain.assistant.model.ToolSpec;
import com.flowforge.domain.assistant.port.out.DesignToolsPort;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.infrastructure.mcp.McpActorScope;
import com.flowforge.infrastructure.mcp.McpToolModes;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §86 (LC3, D-MCP-LC-IDENTITY = A, D-MCP-LC-MODES = A) — le catalogue MCP <b>en mode conception</b>, appelé dans le
 * processus avec l'identité de l'utilisateur de la session ({@link McpActorScope}) : ni jeton dans le navigateur, ni passage par
 * le canal M2M. Les outils sont ceux que {@link McpToolModes} classe en conception (ou les deux), moins ceux que l'assistant
 * n'a pas le droit d'appeler ({@link #DENIED}). Les schémas viennent des annotations des outils : un seul contrat.
 */
public final class McpDesignToolCatalog implements DesignToolsPort {

    /**
     * L'assistant ne publie jamais (D-MCP-LC-PUBLISH) et n'applique pas de bundle (geste d'exploitation). Les écritures de
     * configuration restent des brouillons que l'humain relit.
     */
    static final Set<String> DENIED = Set.of("publish_draft", "import_configuration");

    /** Clés de configuration de connecteur qui désignent un secret : l'assistant n'en écrit jamais (principe P9). */
    private static final Pattern SECRET_KEY =
            Pattern.compile("(?i).*(secret|password|passwd|passphrase|token|api[-_]?key|private[-_]?key|credential|authorization).*");

    private final ApplicationContext context;
    private final ObjectMapper mapper;
    private volatile Map<String, Entry> entries;

    private record Entry(Object bean, Method method, ToolSpec spec) {}

    public McpDesignToolCatalog(ApplicationContext context, ObjectMapper mapper) {
        this.context = Objects.requireNonNull(context, "context");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public List<ToolSpec> specs() {
        return entries().values().stream().map(Entry::spec).toList();
    }

    @Override
    public String invoke(String toolName, String argumentsJson, AccessContext.User actor) {
        Entry entry = entries().get(toolName);
        if (entry == null) {
            throw new ToolInvocationException("unknown tool: " + toolName + " (available: " + String.join(", ", entries().keySet()) + ")");
        }
        JsonNode args;
        try {
            args = mapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
        } catch (RuntimeException ex) {
            throw new ToolInvocationException("arguments are not valid JSON");
        }
        if (!args.isObject()) {
            throw new ToolInvocationException("arguments must be a JSON object");
        }
        rejectSecrets(toolName, args);
        Parameter[] parameters = entry.method().getParameters();
        Object[] values = new Object[parameters.length];
        boolean rawRequest = false;
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getType() == McpSchema.CallToolRequest.class) {
                // Outils qui lisent leurs arguments dans la requête brute (imbrications) : même requête que sur le canal MCP.
                @SuppressWarnings("unchecked")
                Map<String, Object> raw = mapper.convertValue(args, Map.class);
                values[i] = McpSchema.CallToolRequest.builder().name(toolName).arguments(raw).build();
                rawRequest = true;
            }
        }
        for (int i = 0; i < parameters.length; i++) {
            if (rawRequest) {
                if (required(parameters[i]) && (args.get(parameters[i].getName()) == null || args.get(parameters[i].getName()).isNull())) {
                    throw new ToolInvocationException("missing required argument: " + parameters[i].getName());
                }
                continue; // l'outil relit tout dans la requête brute
            }
            JsonNode node = args.get(parameters[i].getName());
            if (node == null || node.isNull()) {
                if (required(parameters[i])) {
                    throw new ToolInvocationException("missing required argument: " + parameters[i].getName());
                }
                values[i] = parameters[i].getType().isPrimitive() ? defaultPrimitive(parameters[i].getType()) : null;
                continue;
            }
            try {
                values[i] = mapper.convertValue(node, mapper.constructType(parameters[i].getParameterizedType()));
            } catch (RuntimeException ex) {
                throw new ToolInvocationException("argument '" + parameters[i].getName() + "' has the wrong type");
            }
        }
        try {
            Object result = McpActorScope.call(actor, () -> call(entry, values));
            return mapper.writeValueAsString(result);
        } catch (ToolInvocationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ToolInvocationException(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(), ex);
        }
    }

    private Object call(Entry entry, Object[] values) {
        try {
            return entry.method().invoke(entry.bean(), values);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new ToolInvocationException(String.valueOf(cause), cause);
        } catch (IllegalAccessException ex) {
            throw new ToolInvocationException("tool not callable", ex);
        }
    }

    private static void rejectSecrets(String toolName, JsonNode args) {
        if (!"define_connector_instance".equals(toolName)) {
            return;
        }
        JsonNode config = args.get("config");
        if (config != null && config.isObject()) {
            for (String key : config.propertyNames()) {
                if (SECRET_KEY.matcher(key).matches()) {
                    throw new ToolInvocationException("the assistant never writes secrets ('" + key + "'): create the connector "
                            + "without it and ask the user to enter it in Admin > Connecteurs");
                }
            }
        }
    }

    private Map<String, Entry> entries() {
        Map<String, Entry> local = entries;
        if (local == null) {
            synchronized (this) {
                if (entries == null) {
                    entries = discover();
                }
                local = entries;
            }
        }
        return local;
    }

    private Map<String, Entry> discover() {
        Set<String> allowed = new java.util.TreeSet<>(McpToolModes.toolsFor(McpToolModes.Mode.DESIGN));
        allowed.removeAll(DENIED);
        Map<String, Entry> found = new TreeMap<>();
        for (String beanName : context.getBeanNamesForType(Object.class)) {
            Class<?> type = context.getType(beanName);
            if (type == null || !ClassUtils.getUserClass(type).getName().startsWith("com.flowforge.infrastructure.mcp.tool.")) {
                continue;
            }
            Object bean = context.getBean(beanName);
            Class<?> userClass = AopUtils.isAopProxy(bean) ? AopUtils.getTargetClass(bean) : ClassUtils.getUserClass(bean);
            for (Method method : userClass.getMethods()) {
                McpTool tool = AnnotationUtils.findAnnotation(method, McpTool.class);
                if (tool == null || !allowed.contains(tool.name())) {
                    continue;
                }
                found.put(tool.name(), new Entry(bean, method, specOf(tool, method)));
            }
        }
        return java.util.Collections.unmodifiableMap(found);
    }

    private static ToolSpec specOf(McpTool tool, Method method) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Parameter parameter : method.getParameters()) {
            if (parameter.getType() == McpSchema.CallToolRequest.class) {
                continue; // la requête brute n'est pas un argument du modèle
            }
            McpToolParam meta = parameter.getAnnotation(McpToolParam.class);
            Map<String, Object> schema = new LinkedHashMap<>(typeSchema(parameter.getType()));
            if (meta != null && !meta.description().isBlank()) {
                schema.put("description", meta.description());
            }
            properties.put(parameter.getName(), schema);
            if (required(parameter)) {
                required.add(parameter.getName());
            }
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("type", "object");
        input.put("properties", properties);
        if (!required.isEmpty()) {
            input.put("required", required);
        }
        return new ToolSpec(tool.name(), tool.description().isBlank() ? tool.name() : tool.description(), input);
    }

    private static boolean required(Parameter parameter) {
        McpToolParam meta = parameter.getAnnotation(McpToolParam.class);
        return meta != null && meta.required();
    }

    private static Map<String, Object> typeSchema(Class<?> type) {
        if (type == String.class || type.isEnum() || type == java.util.UUID.class) {
            return Map.of("type", "string");
        }
        if (type == Integer.class || type == int.class || type == Long.class || type == long.class) {
            return Map.of("type", "integer");
        }
        if (type == Boolean.class || type == boolean.class) {
            return Map.of("type", "boolean");
        }
        if (type == Double.class || type == double.class || type == Number.class) {
            return Map.of("type", "number");
        }
        if (List.class.isAssignableFrom(type) || type.isArray()) {
            return Map.of("type", "array", "items", Map.of());
        }
        return Map.of("type", "object");
    }

    private static Object defaultPrimitive(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0d;
        }
        return null;
    }

}
