package be.myrmacta.infrastructure.promotion;

import be.myrmacta.domain.promotion.port.out.GroupReferenceExtractor;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * TODO §117 (D-PROMO-ORPHAN) — lecture StAX (sans DTD ni entité externe) des groupes nommés littéralement : {@code candidateGroups}
 * d'une {@code userTask}, attribut {@code groupeTri} d'une tâche de service (« Router le courrier »), valeurs de la sortie
 * {@code routageService} d'une table DMN. Un document illisible donne un ensemble vide : sa validation le bloque ailleurs.
 */
public final class XmlGroupReferenceExtractor implements GroupReferenceExtractor {

    static final String FLOWABLE_NS = "http://flowable.org/bpmn";
    static final String ROUTING_OUTPUT = "routageService";
    /** Forme d'un code de groupe (§114) : une valeur d'une autre forme n'est pas un groupe, on ne la signale pas. */
    private static final Pattern GROUP_CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");
    private static final Pattern QUOTED = Pattern.compile("^\\s*(?:\"([^\"]*)\"|'([^']*)')\\s*$");

    @Override
    public Set<String> fromBpmn(byte[] bpmnXml) {
        Set<String> codes = new TreeSet<>();
        try {
            XMLStreamReader reader = reader(bpmnXml);
            while (reader.hasNext()) {
                if (reader.next() != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                String local = reader.getLocalName();
                if ("userTask".equals(local)) {
                    String groups = reader.getAttributeValue(FLOWABLE_NS, "candidateGroups");
                    if (groups != null) {
                        for (String code : groups.split(",")) {
                            addIfCode(codes, code.strip());
                        }
                    }
                }
                if ("serviceTask".equals(local)) {
                    String sorting = reader.getAttributeValue(FLOWABLE_NS, "groupeTri");
                    if (sorting != null) {
                        addIfCode(codes, sorting.strip());
                    }
                }
            }
        } catch (XMLStreamException | RuntimeException e) {
            return Set.of();
        }
        return codes;
    }

    @Override
    public Set<String> fromDmn(byte[] dmnXml) {
        Set<String> codes = new TreeSet<>();
        try {
            XMLStreamReader reader = reader(dmnXml);
            List<String> outputs = new ArrayList<>();
            int outputIndex = -1;
            boolean inEntry = false;
            StringBuilder text = new StringBuilder();
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    switch (reader.getLocalName()) {
                        case "decisionTable" -> outputs.clear();
                        case "output" -> outputs.add(String.valueOf(reader.getAttributeValue(null, "name")));
                        case "rule" -> outputIndex = -1;
                        case "outputEntry" -> {
                            outputIndex++;
                            inEntry = outputIndex < outputs.size() && ROUTING_OUTPUT.equals(outputs.get(outputIndex));
                            text.setLength(0);
                        }
                        default -> {
                            // autres éléments sans intérêt
                        }
                    }
                } else if (inEntry && (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA)) {
                    text.append(reader.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT && "outputEntry".equals(reader.getLocalName())) {
                    if (inEntry) {
                        var m = QUOTED.matcher(text);
                        if (m.matches()) {
                            addIfCode(codes, m.group(1) != null ? m.group(1) : m.group(2));
                        }
                    }
                    inEntry = false;
                }
            }
        } catch (XMLStreamException | RuntimeException e) {
            return Set.of();
        }
        return codes;
    }

    private static void addIfCode(Set<String> codes, String value) {
        if (value != null && GROUP_CODE.matcher(value).matches()) {
            codes.add(value);
        }
    }

    private static XMLStreamReader reader(byte[] xml) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        return factory.createXMLStreamReader(new ByteArrayInputStream(xml));
    }
}
