package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.workflow.WorkflowVariables;
import be.myrmacta.domain.workflow.model.InvalidBpmnException;
import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Issue #203 (TODO §99, D-BPMN-RESERVED-WRITE) — {@link WorkflowVariables#assertNoReservedKeys} ferme les entrées
 * utilisateur, mais le BPMN déployé peut lui-même <em>recopier</em> une valeur dans un nom réservé : une boucle
 * multi-instance {@code elementVariable="generatedDocumentId"} sur {@code refs} (posée à une complétion) crée une variable
 * locale que « Joindre le document » lit en priorité ; un {@code callActivity} {@code <flowable:in source="docRef"
 * target="dossierId"/>} fait de même dans le sous-processus. Ces attributs n'ont pas de {@code ${…}} : le §69 les laisse
 * passer.
 *
 * <p>Même méthode que {@link BpmnExpressionSurfaceScanner} : lecture brute du XML, tout espace de noms (alias
 * {@code activiti:} compris). Une écriture de variable dont le nom est réservé est refusée :
 * <ul>
 *   <li>tout attribut {@code …Variable} / {@code …VariableName} ({@code elementVariable}, {@code elementIndexVariable},
 *       {@code resultVariable}, {@code taskIdVariableName}, {@code taskCompleterVariableName}, {@code errorVariableName}…),
 *       règle de suffixe pour couvrir les attributs futurs ;</li>
 *   <li>{@code initiator} (identifiant de l'utilisateur qui démarre) ;</li>
 *   <li>{@code target} / {@code targetExpression} de tout élément ({@code flowable:in}, {@code flowable:out},
 *       {@code eventOutParameter}, {@code variableAggregation}…), sauf recopie à l'identique d'un {@code in} / {@code out}
 *       ({@code source} = {@code target}, sans {@code sourceExpression}) : passer {@code dossierId} tel quel au
 *       sous-processus reste permis ;</li>
 *   <li>{@code name} d'un {@code dataObject} (variable posée au démarrage du process ou, locale, à l'entrée d'un
 *       sous-processus) et d'un {@code inputDataItem} (forme standard de l'{@code elementVariable}) ;</li>
 *   <li>{@code id} / {@code variable} d'un {@code formProperty}.</li>
 * </ul>
 * Les lectures ({@code source}, {@code collection}…) restent libres. La comparaison se fait sur la valeur sans espaces de
 * bord, sensible à la casse comme les noms de variables Flowable.
 */
final class BpmnReservedVariableWriteScanner {

    private static final Pattern VARIABLE_ATTRIBUTE = Pattern.compile(".+Variable(Name)?");
    private static final Set<String> TARGET_ATTRIBUTES = Set.of("target", "targetExpression");
    private static final Set<String> IDENTITY_COPY_ELEMENTS = Set.of("in", "out");
    private static final Map<String, Set<String>> ELEMENT_WRITE_ATTRIBUTES = Map.of(
            "dataObject", Set.of("name"),
            "inputDataItem", Set.of("name"),
            "formProperty", Set.of("id", "variable"));

    private BpmnReservedVariableWriteScanner() {}

    static void assertNoReservedVariableWrites(byte[] bpmnXml) {
        XMLStreamReader reader = null;
        try {
            reader = secureFactory().createXMLStreamReader(new ByteArrayInputStream(bpmnXml));
            scan(reader);
        } catch (XMLStreamException ex) {
            throw new InvalidBpmnException("File is not a valid BPMN 2.0 document: " + ex.getMessage(), ex);
        } finally {
            closeQuietly(reader);
        }
    }

    private static void scan(XMLStreamReader reader) throws XMLStreamException {
        Deque<String> owners = new ArrayDeque<>();
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT -> {
                    String element = reader.getLocalName();
                    String id = reader.getAttributeValue(null, "id");
                    String owner = id != null && !id.isBlank()
                            ? element + " " + id
                            : owners.isEmpty() ? element : owners.peek() + " > " + element;
                    assertNoReservedWrite(reader, element, owner);
                    owners.push(owner);
                }
                case XMLStreamConstants.END_ELEMENT -> owners.pop();
                default -> {
                    // texte, commentaires : aucune écriture de variable
                }
            }
        }
    }

    private static void assertNoReservedWrite(XMLStreamReader reader, String element, String owner) {
        Set<String> elementWrites = ELEMENT_WRITE_ATTRIBUTES.getOrDefault(element, Set.of());
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String name = reader.getAttributeLocalName(i);
            String value = reader.getAttributeValue(i).strip();
            if (!WorkflowVariables.reservedKeys().contains(value)) {
                continue;
            }
            boolean writes = VARIABLE_ATTRIBUTE.matcher(name).matches()
                    || "initiator".equals(name)
                    || elementWrites.contains(name)
                    || TARGET_ATTRIBUTES.contains(name) && !isIdentityCopy(reader, element);
            if (writes) {
                throw new InvalidBpmnException("BPMN cannot write the reserved variable '" + value + "': " + owner
                        + " @" + name);
            }
        }
    }

    /** {@code <flowable:in|out source="x" target="x"/>} : la valeur reste celle du même nom dans l'autre process. */
    private static boolean isIdentityCopy(XMLStreamReader reader, String element) {
        if (!IDENTITY_COPY_ELEMENTS.contains(element)) {
            return false;
        }
        String source = null;
        String target = null;
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            switch (reader.getAttributeLocalName(i)) {
                case "source" -> source = reader.getAttributeValue(i).strip();
                case "target" -> target = reader.getAttributeValue(i).strip();
                case "sourceExpression", "targetExpression" -> {
                    return false;
                }
                default -> {
                    // sourceType, targetType, transient… : sans effet sur le nom écrit
                }
            }
        }
        return target != null && target.equals(source);
    }

    private static XMLInputFactory secureFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory;
    }

    private static void closeQuietly(XMLStreamReader reader) {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (XMLStreamException ignored) {
            // nothing left to release for an in-memory stream
        }
    }
}
