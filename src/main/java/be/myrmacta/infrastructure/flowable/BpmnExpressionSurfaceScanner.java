package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.workflow.AllowedBpmnDelegates;
import be.myrmacta.domain.workflow.WorkflowVariables;
import be.myrmacta.domain.workflow.model.InvalidBpmnException;
import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * TODO §69 — allowlist <strong>générique</strong> des expressions UEL d'un document BPMN, par
 * lecture brute du XML plutôt que du modèle Flowable.
 *
 * <p>H1-bis (TODO §57/§58) avait fermé {@code assignee}/{@code candidateUsers}/{@code
 * candidateGroups} un par un. Or Flowable 8 évalue en UEL beaucoup d'autres surfaces — sur un
 * {@code userTask} seul : {@code name}, {@code documentation}, {@code formKey}, {@code dueDate},
 * {@code priority}, {@code category}, {@code owner}, {@code businessCalendarName}, {@code
 * taskIdVariableName} ({@code UserTaskActivityBehavior}, sources 8.0.0) ; ailleurs : timers,
 * {@code callActivity calledElement}, multi-instance, {@code skipExpression}... Preuve moteur :
 * {@code FlowableBpmnUelSurfaceGapTest} ({@code name}/{@code formKey} invoquent une méthode d'un
 * bean Spring vivant). Chercher la prochaine surface attribut par attribut reproduirait l'erreur
 * de H1-bis ; la règle est donc inversée : <strong>aucun</strong> {@code ${…}} / {@code #{…}} nulle
 * part (attribut ou texte, tout espace de noms, y compris l'alias {@code activiti:}), sauf aux trois
 * seuls endroits déjà validés par une règle dédiée et réellement utilisés par les BPMN livrés :
 *
 * <ul>
 *   <li>un attribut {@code delegateExpression} dont la valeur est dans {@link AllowedBpmnDelegates} ;
 *   <li>le texte d'un élément {@code conditionExpression}, revalidé ici par la même grammaire sûre
 *       ({@link FlowableBpmnParserAdapter#assertSafeConditionExpression}) ;
 *   <li>le texte d'un {@code timeDate} de minuteur égal à {@code ${echeanceRappel}} ou {@code ${echeanceDepassee}} (TODO §115).
 * </ul>
 *
 * <p>Un attribut {@code class} (instanciation d'une classe arbitraire par le moteur) est refusé
 * quel que soit l'élément — aucun BPMN livré n'en utilise. De même pour {@code language} /
 * {@code scriptFormat} : une {@code conditionExpression} portant {@code language} est exécutée
 * comme un script JSR-223 ({@code ConditionUtil} → {@code ScriptCondition}), sans aucun
 * {@code ${…}} à détecter. Aucun moteur de script n'est sur le classpath aujourd'hui (vérifié le
 * 2026-09-24) : défense en profondeur contre l'ajout futur, même transitif, de Groovy ou d'un
 * moteur JavaScript. Le texte d'une annotation contenant
 * {@code ${} est lui aussi refusé : faux positif accepté plutôt que de maintenir une liste des
 * éléments « non évalués ».
 *
 * <p>Le lecteur StAX est configuré sans DTD ni entité externe (pas de XXE) ; les entités
 * standard ({@code &#36;}) sont décodées avant contrôle, comme le moteur les voit.
 */
final class BpmnExpressionSurfaceScanner {

    private static final String DELEGATE_EXPRESSION = "delegateExpression";
    private static final String CONDITION_EXPRESSION = "conditionExpression";
    private static final String CLASS_ATTRIBUTE = "class";
    private static final Set<String> SCRIPT_LANGUAGE_ATTRIBUTES = Set.of("language", "scriptFormat");
    private static final String TIME_DATE = "timeDate";
    private static final String TIMER_EVENT_DEFINITION = "timerEventDefinition";
    /**
     * TODO §115 (D-DEADLINE-TIMER) — seules expressions admises dans un minuteur : le nom nu d'une variable réservée écrite par
     * la brique « Calculer une échéance ». Un nom nu ne peut ni appeler une méthode ni désigner un bean (aucun bean ne porte ces
     * noms), et une complétion de tâche ne peut pas les forger (WorkflowVariables).
     */
    private static final Set<String> DEADLINE_TIMER_VALUES = Set.of(
            "${" + WorkflowVariables.ECHEANCE_RAPPEL + "}", "${" + WorkflowVariables.ECHEANCE_DEPASSEE + "}");

    private BpmnExpressionSurfaceScanner() {}

    static void assertNoUnvettedExpressions(byte[] bpmnXml) {
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
        Deque<Frame> stack = new ArrayDeque<>();
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT -> {
                    String element = reader.getLocalName();
                    String id = reader.getAttributeValue(null, "id");
                    String owner = owner(element, id, stack);
                    for (int i = 0; i < reader.getAttributeCount(); i++) {
                        assertSafeAttribute(owner, reader.getAttributeLocalName(i), reader.getAttributeValue(i));
                    }
                    stack.push(new Frame(element, owner));
                }
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE -> {
                    if (!stack.isEmpty()) {
                        stack.peek().text.append(reader.getText());
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    Frame frame = stack.pop();
                    assertSafeText(frame, stack.peek());
                }
                default -> {
                    // comments, processing instructions: never evaluated by the engine
                }
            }
        }
    }

    private static void assertSafeAttribute(String owner, String name, String value) {
        if (CLASS_ATTRIBUTE.equals(name)) {
            throw new InvalidBpmnException("BPMN class attribute is not allowed: " + owner);
        }
        if (SCRIPT_LANGUAGE_ATTRIBUTES.contains(name)) {
            throw new InvalidBpmnException("BPMN script language attribute is not allowed: " + owner + " @" + name);
        }
        if (!containsExpression(value)) {
            return;
        }
        if (DELEGATE_EXPRESSION.equals(name) && AllowedBpmnDelegates.isAllowedDelegateExpression(value)) {
            return;
        }
        throw new InvalidBpmnException("BPMN expression is not allowed: " + owner + " @" + name);
    }

    private static void assertSafeText(Frame frame, Frame parent) {
        String text = frame.text.toString();
        if (!containsExpression(text)) {
            return;
        }
        if (CONDITION_EXPRESSION.equals(frame.element)) {
            FlowableBpmnParserAdapter.assertSafeConditionExpression(frame.owner, text);
            return;
        }
        if (TIME_DATE.equals(frame.element) && parent != null && TIMER_EVENT_DEFINITION.equals(parent.element)
                && DEADLINE_TIMER_VALUES.contains(text.strip())) {
            return;
        }
        throw new InvalidBpmnException("BPMN expression is not allowed: " + frame.owner + " text");
    }

    private static boolean containsExpression(String value) {
        return value != null && (value.contains("${") || value.contains("#{"));
    }

    private static String owner(String element, String id, Deque<Frame> stack) {
        if (id != null && !id.isBlank()) {
            return element + " " + id;
        }
        return stack.isEmpty() ? element : stack.peek().owner + " > " + element;
    }

    private static XMLInputFactory secureFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, false);
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

    private static final class Frame {
        private final String element;
        private final String owner;
        private final StringBuilder text = new StringBuilder();

        private Frame(String element, String owner) {
            this.element = element;
            this.owner = owner;
        }
    }
}
