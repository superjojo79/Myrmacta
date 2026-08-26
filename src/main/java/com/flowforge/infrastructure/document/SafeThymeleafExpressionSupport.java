package com.flowforge.infrastructure.document;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.thymeleaf.context.IContext;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.expression.Dates;
import org.thymeleaf.expression.Lists;
import org.thymeleaf.expression.Numbers;
import org.thymeleaf.expression.Strings;

/**
 * Allowlist B2 (D-40) : chemins de propriétés du modèle, {@code #vars}, et méthodes
 * <em>déclarées</em> sur {@link Dates}, {@link Numbers}, {@link Strings}, {@link Lists}.
 * Pas de SpEL {@code T()}, pas {@code #beans}/{@code #request}, pas {@code new}, pas
 * {@code Object.getClass}/{@code wait}/{@code notify} (A-EXPR-METHODS).
 */
final class SafeThymeleafExpressionSupport {

    private static final Set<String> UTILITIES = Set.of("dates", "numbers", "strings", "lists");
    private static final Set<String> BLOCKED_PROPERTIES =
            Set.of("class", "getclass", "wait", "notify", "notifyall");

    private SafeThymeleafExpressionSupport() {}

    static void assertAllowed(String raw) {
        new Parser(raw, null).parseTop();
    }

    static Object evaluate(IContext context, String raw) {
        return new Parser(raw, context).parseTop();
    }

    static List<String> extractVariableExpressions(String html) {
        List<String> expressions = new ArrayList<>();
        int i = 0;
        while (i < html.length()) {
            int dollar = indexOfExprOpen(html, i);
            if (dollar < 0) {
                break;
            }
            int start = dollar + 2;
            int end = matchingBrace(html, start);
            if (end < 0) {
                throw unsupported("Unclosed template expression");
            }
            expressions.add(html.substring(start, end));
            i = end + 1;
        }
        return expressions;
    }

    private static int indexOfExprOpen(String html, int from) {
        int dollar = html.indexOf("${", from);
        int star = html.indexOf("*{", from);
        if (dollar < 0) {
            return star;
        }
        if (star < 0) {
            return dollar;
        }
        return Math.min(dollar, star);
    }

    private static int matchingBrace(String html, int start) {
        int depth = 1;
        boolean inSingle = false;
        boolean inDouble = false;
        boolean escape = false;
        for (int i = start; i < html.length(); i++) {
            char c = html.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if ((inSingle || inDouble) && c == '\\') {
                escape = true;
                continue;
            }
            if (!inDouble && c == '\'') {
                inSingle = !inSingle;
                continue;
            }
            if (!inSingle && c == '"') {
                inDouble = !inDouble;
                continue;
            }
            if (inSingle || inDouble) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    static TemplateProcessingException unsupported(String raw) {
        return new TemplateProcessingException("Unsupported Thymeleaf expression: " + raw);
    }

    private static final class Parser {
        private final String source;
        private final IContext context;
        private int index;

        private Parser(String raw, IContext context) {
            this.source = raw == null ? "" : raw.trim();
            this.context = context;
            this.index = 0;
        }

        private boolean validateOnly() {
            return context == null;
        }

        private Object parseTop() {
            if (source.isEmpty()) {
                throw unsupported(source);
            }
            Object value = parseExpr();
            skipWs();
            if (index != source.length()) {
                throw unsupported(source);
            }
            return value;
        }

        private Object parseExpr() {
            skipWs();
            if (index >= source.length()) {
                throw unsupported(source);
            }
            char c = source.charAt(index);
            if (c == '#') {
                return parseHash();
            }
            if (c == '\'' || c == '"') {
                return parseStringLiteral();
            }
            if (c == '-' || Character.isDigit(c)) {
                return parseNumberLiteral();
            }
            if (isIdentStart(c)) {
                String ident = parseIdent();
                skipWs();
                if ("new".equals(ident)) {
                    throw unsupported(source);
                }
                if ("T".equals(ident) && peek('(')) {
                    throw unsupported(source);
                }
                if (isBareKeyword(ident)) {
                    return keywordValue(ident);
                }
                return finishPropertyPath(ident);
            }
            throw unsupported(source);
        }

        private boolean isBareKeyword(String ident) {
            if (!("true".equals(ident) || "false".equals(ident) || "null".equals(ident))) {
                return false;
            }
            skipWs();
            return !peek('.') && !peek('(');
        }

        private static Object keywordValue(String ident) {
            if ("true".equals(ident)) {
                return Boolean.TRUE;
            }
            if ("false".equals(ident)) {
                return Boolean.FALSE;
            }
            return null;
        }

        private Object parseHash() {
            consume('#');
            String objectName = parseIdent();
            if ("vars".equals(objectName)) {
                return parseVars();
            }
            if (!UTILITIES.contains(objectName)) {
                throw unsupported(source);
            }
            skipWs();
            consume('.');
            skipWs();
            String methodName = parseIdent();
            assertNotBlocked(methodName);
            skipWs();
            consume('(');
            List<Object> args = parseArgList();
            consume(')');
            skipWs();
            if (peek('.')) {
                throw unsupported(source);
            }
            return invokeUtility(objectName, methodName, args);
        }

        private Object parseVars() {
            skipWs();
            consume('.');
            skipWs();
            String first = parseIdent();
            assertNotBlocked(first);
            List<String> rest = new ArrayList<>();
            skipWs();
            while (peek('.')) {
                consume('.');
                skipWs();
                String next = parseIdent();
                assertNotBlocked(next);
                skipWs();
                if (peek('(')) {
                    throw unsupported(source);
                }
                rest.add(next);
            }
            if (peek('(')) {
                throw unsupported(source);
            }
            if (validateOnly()) {
                return null;
            }
            Object current = context.getVariable(first);
            if (current == null) {
                throw new TemplateProcessingException("Unknown template variable: " + first);
            }
            for (String property : rest) {
                current = readProperty(current, property, source);
            }
            return current;
        }

        private Object finishPropertyPath(String first) {
            assertNotBlocked(first);
            List<String> rest = new ArrayList<>();
            skipWs();
            while (peek('.')) {
                consume('.');
                skipWs();
                String next = parseIdent();
                assertNotBlocked(next);
                skipWs();
                if (peek('(')) {
                    throw unsupported(source);
                }
                rest.add(next);
            }
            if (validateOnly()) {
                return null;
            }
            Object current = context.getVariable(first);
            if (current == null) {
                throw new TemplateProcessingException("Unknown template variable: " + first);
            }
            for (String property : rest) {
                current = readProperty(current, property, source);
            }
            return current;
        }

        private List<Object> parseArgList() {
            skipWs();
            List<Object> args = new ArrayList<>();
            if (peek(')')) {
                return args;
            }
            args.add(parseExpr());
            skipWs();
            while (peek(',')) {
                consume(',');
                args.add(parseExpr());
                skipWs();
            }
            return args;
        }

        private Object invokeUtility(String objectName, String methodName, List<Object> args) {
            Class<?> type = utilityType(objectName);
            List<Method> candidates = declaredMethods(type, methodName, args.size());
            if (candidates.isEmpty()) {
                throw unsupported(source);
            }
            if (validateOnly()) {
                return null;
            }
            Object target = utilityInstance(objectName, context.getLocale());
            Exception last = null;
            for (Method method : candidates) {
                try {
                    Object[] invokeArgs = coerceArguments(method, args);
                    method.setAccessible(true);
                    return method.invoke(target, invokeArgs);
                } catch (ReflectiveOperationException | IllegalArgumentException ex) {
                    last = ex;
                }
            }
            throw new TemplateProcessingException("Unsupported Thymeleaf expression: " + source, last);
        }

        private static Class<?> utilityType(String objectName) {
            return switch (objectName) {
                case "dates" -> Dates.class;
                case "numbers" -> Numbers.class;
                case "strings" -> Strings.class;
                case "lists" -> Lists.class;
                default -> throw unsupported(objectName);
            };
        }

        private static Object utilityInstance(String objectName, Locale locale) {
            Locale resolved = locale == null ? Locale.ROOT : locale;
            return switch (objectName) {
                case "dates" -> new Dates(resolved);
                case "numbers" -> new Numbers(resolved);
                case "strings" -> new Strings(resolved);
                case "lists" -> new Lists();
                default -> throw unsupported(objectName);
            };
        }

        private static List<Method> declaredMethods(Class<?> type, String name, int argCount) {
            List<Method> matches = new ArrayList<>();
            for (Method method : type.getDeclaredMethods()) {
                if (method.isSynthetic() || method.isBridge() || !method.getName().equals(name)) {
                    continue;
                }
                if (arityMatches(method, argCount)) {
                    matches.add(method);
                }
            }
            return matches;
        }

        private static boolean arityMatches(Method method, int argCount) {
            int params = method.getParameterCount();
            if (method.isVarArgs()) {
                return argCount >= params - 1;
            }
            return params == argCount;
        }

        private static Object[] coerceArguments(Method method, List<Object> args) {
            Class<?>[] types = method.getParameterTypes();
            if (!method.isVarArgs()) {
                Object[] coerced = new Object[types.length];
                for (int i = 0; i < types.length; i++) {
                    coerced[i] = coerce(types[i], args.get(i));
                }
                return coerced;
            }
            int fixed = types.length - 1;
            Object[] coerced = new Object[types.length];
            for (int i = 0; i < fixed; i++) {
                coerced[i] = coerce(types[i], args.get(i));
            }
            Class<?> component = types[fixed].getComponentType();
            int extra = args.size() - fixed;
            Object array = Array.newInstance(component, extra);
            for (int i = 0; i < extra; i++) {
                Array.set(array, i, coerce(component, args.get(fixed + i)));
            }
            coerced[fixed] = array;
            return coerced;
        }

        private static Object coerce(Class<?> param, Object value) {
            if (value == null) {
                if (param.isPrimitive()) {
                    throw new IllegalArgumentException("null primitive");
                }
                return null;
            }
            Class<?> boxed = box(param);
            if (boxed.isInstance(value)) {
                return value;
            }
            if (value instanceof Number number) {
                if (boxed == Integer.class) {
                    return number.intValue();
                }
                if (boxed == Long.class) {
                    return number.longValue();
                }
                if (boxed == Double.class) {
                    return number.doubleValue();
                }
                if (boxed == Float.class) {
                    return number.floatValue();
                }
                if (boxed == Short.class) {
                    return number.shortValue();
                }
                if (boxed == Byte.class) {
                    return number.byteValue();
                }
                if (boxed == Number.class) {
                    return number;
                }
            }
            if (boxed == Date.class) {
                if (value instanceof Date date) {
                    return date;
                }
                if (value instanceof Instant instant) {
                    return Date.from(instant);
                }
            }
            if (boxed == String.class && value instanceof String string) {
                return string;
            }
            if (param == Object.class) {
                return value;
            }
            if (List.class.isAssignableFrom(param) && value instanceof List<?> list) {
                return list;
            }
            throw new IllegalArgumentException("Cannot coerce " + value.getClass().getName() + " to " + param.getName());
        }

        private static Class<?> box(Class<?> type) {
            if (!type.isPrimitive()) {
                return type;
            }
            if (type == int.class) {
                return Integer.class;
            }
            if (type == long.class) {
                return Long.class;
            }
            if (type == double.class) {
                return Double.class;
            }
            if (type == float.class) {
                return Float.class;
            }
            if (type == short.class) {
                return Short.class;
            }
            if (type == byte.class) {
                return Byte.class;
            }
            if (type == boolean.class) {
                return Boolean.class;
            }
            if (type == char.class) {
                return Character.class;
            }
            return type;
        }

        private String parseIdent() {
            skipWs();
            if (index >= source.length() || !isIdentStart(source.charAt(index))) {
                throw unsupported(source);
            }
            int start = index;
            index++;
            while (index < source.length() && isIdentPart(source.charAt(index))) {
                index++;
            }
            return source.substring(start, index);
        }

        private String parseStringLiteral() {
            char quote = source.charAt(index);
            index++;
            StringBuilder builder = new StringBuilder();
            while (index < source.length()) {
                char c = source.charAt(index);
                if (c == '\\' && index + 1 < source.length()) {
                    builder.append(source.charAt(index + 1));
                    index += 2;
                    continue;
                }
                if (c == quote) {
                    if (index + 1 < source.length() && source.charAt(index + 1) == quote) {
                        builder.append(quote);
                        index += 2;
                        continue;
                    }
                    index++;
                    return builder.toString();
                }
                builder.append(c);
                index++;
            }
            throw unsupported(source);
        }

        private Object parseNumberLiteral() {
            int start = index;
            if (peek('-')) {
                index++;
            }
            if (index >= source.length() || !Character.isDigit(source.charAt(index))) {
                throw unsupported(source);
            }
            while (index < source.length() && Character.isDigit(source.charAt(index))) {
                index++;
            }
            boolean decimal = false;
            if (peek('.') && index + 1 < source.length() && Character.isDigit(source.charAt(index + 1))) {
                decimal = true;
                index++;
                while (index < source.length() && Character.isDigit(source.charAt(index))) {
                    index++;
                }
            }
            String token = source.substring(start, index);
            if (decimal) {
                return Double.valueOf(token);
            }
            try {
                return Integer.valueOf(token);
            } catch (NumberFormatException ex) {
                return Long.valueOf(token);
            }
        }

        private void skipWs() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
                index++;
            }
        }

        private boolean peek(char expected) {
            return index < source.length() && source.charAt(index) == expected;
        }

        private void consume(char expected) {
            skipWs();
            if (!peek(expected)) {
                throw unsupported(source);
            }
            index++;
        }
    }

    private static void assertNotBlocked(String name) {
        if (BLOCKED_PROPERTIES.contains(name.toLowerCase(Locale.ROOT))) {
            throw unsupported(name);
        }
    }

    /**
     * Read-only property access (follow-up to D-40 Phase 9 finding). Only two shapes are
     * allowed, both {@code public} — no {@code setAccessible}, no arbitrary bare-name
     * method, no private/package/protected member reachable regardless of naming:
     *
     * <ul>
     *   <li>JavaBean getters — {@code get<Name>()} / {@code is<Name>()};
     *   <li>Java record canonical accessors — {@code <name>()}, resolved via
     *       {@link Class#getRecordComponents()} rather than by matching an arbitrary
     *       method name, so a record's own extra methods aren't reachable this way.
     * </ul>
     */
    private static Object readProperty(Object target, String name, String fullExpression) {
        assertNotBlocked(name);
        if (target instanceof Map<?, ?> map) {
            if (!map.containsKey(name)) {
                throw new TemplateProcessingException("Unknown template property: " + fullExpression);
            }
            Object value = map.get(name);
            if (value == null) {
                throw new TemplateProcessingException("Unknown template property: " + fullExpression);
            }
            return value;
        }
        Method method = beanGetter(target.getClass(), name);
        if (method == null && target.getClass().isRecord()) {
            method = recordAccessor(target.getClass(), name);
        }
        if (method == null) {
            throw new TemplateProcessingException("Unknown template property: " + fullExpression);
        }
        try {
            Object value = method.invoke(target);
            if (value == null) {
                throw new TemplateProcessingException("Unknown template property: " + fullExpression);
            }
            return value;
        } catch (TemplateProcessingException ex) {
            throw ex;
        } catch (ReflectiveOperationException ex) {
            throw new TemplateProcessingException("Unknown template property: " + fullExpression, ex);
        }
    }

    private static Method beanGetter(Class<?> type, String name) {
        String cap = name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
        Method method = findPublicNoArg(type, "get" + cap);
        if (method == null) {
            method = findPublicNoArg(type, "is" + cap);
        }
        return method;
    }

    private static Method recordAccessor(Class<?> type, String name) {
        for (RecordComponent component : type.getRecordComponents()) {
            if (component.getName().equals(name)) {
                return component.getAccessor();
            }
        }
        return null;
    }

    private static Method findPublicNoArg(Class<?> type, String methodName) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.isSynthetic()
                        && !method.isBridge()
                        && Modifier.isPublic(method.getModifiers())
                        && method.getName().equals(methodName)
                        && method.getParameterCount() == 0) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
