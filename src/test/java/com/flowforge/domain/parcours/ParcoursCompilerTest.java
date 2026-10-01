package com.flowforge.domain.parcours;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.parcours.model.CompiledParcours;
import com.flowforge.domain.parcours.model.InvalidParcoursException;
import com.flowforge.domain.parcours.model.ParcoursDefinition;
import com.flowforge.domain.parcours.model.ParcoursDefinition.Branch;
import com.flowforge.domain.parcours.model.ParcoursDefinition.Kind;
import com.flowforge.domain.parcours.model.ParcoursDefinition.Operator;
import com.flowforge.domain.parcours.model.ParcoursDefinition.Step;
import com.flowforge.domain.workflow.model.StatusOutcome;
import com.flowforge.domain.workflow.model.WorkflowStatusDefinition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** TODO §88 (LC5) — compilation d'un parcours en BPMN : déterminisme, constructions, refus, avertissements. */
class ParcoursCompilerTest {

    private static final List<WorkflowStatusDefinition> STATUSES = List.of(
            new WorkflowStatusDefinition("OUVERT", "Ouvert", true, StatusOutcome.NONE),
            new WorkflowStatusDefinition("VALIDE", "Validé", false, StatusOutcome.COMPLETED));

    private static ParcoursDefinition definition(Step... steps) {
        return new ParcoursDefinition(STATUSES, List.of(DossierStatus.OUVERT, DossierStatus.EN_COURS), null, List.of(steps));
    }

    private static Step step(String id, Kind kind, Map<String, String> params) {
        return new Step(id, id, kind, params, null, null, null);
    }

    private static CompiledParcours compile(ParcoursDefinition d) {
        return ParcoursCompiler.compile("sinistre", "Sinistre", d, key -> Set.of("avis", "montant"));
    }

    private static Document parse(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static ParcoursDefinition fullExample() {
        return definition(
                step("saisie", Kind.QUESTION, Map.of("schemaKey", "avis")),
                step("calcul", Kind.EXPRESSION, Map.of("formula", "dossier.fields.montant > 5000", "formulaResult", "gros")),
                new Step("choix", "Montant élevé ?", Kind.DECISION, Map.of(), null,
                        List.of(new Branch("gros", Operator.EQ, "true", "validation"), new Branch("montant", Operator.GE, "100", "attente")), "cloture"),
                step("validation", Kind.QUESTION, Map.of("schemaKey", "avis")),
                new Step("attente", "Attendre 24 h", Kind.WAIT, Map.of("duration", "PT24H"), "document", null, null),
                step("document", Kind.DOCUMENT, Map.of("documentType", "courrier")),
                step("cloture", Kind.SET_WORKFLOW_STATUS, Map.of("targetWorkflowStatus", "VALIDE")));
    }

    @Test
    void theSameParcoursAlwaysCompilesToTheSameBytes() {
        assertThat(compile(fullExample()).xml()).isEqualTo(compile(fullExample()).xml());
        assertThat(compile(fullExample()).sha256()).isEqualTo(compile(fullExample()).sha256()).hasSize(64);
    }

    @Test
    void theOutputIsWellFormedBpmnWithAProcessStartEndFlowsAndADiagram() throws Exception {
        String xml = compile(fullExample()).xml();
        Document doc = parse(xml);
        Element process = (Element) doc.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "process").item(0);
        assertThat(process.getAttribute("id")).isEqualTo("sinistre");
        assertThat(process.getAttribute("isExecutable")).isEqualTo("true");
        assertThat(xml).contains("<startEvent id=\"start\"").contains("<endEvent id=\"end\"");
        // une forme et un tracé par élément et par flux : l'éditeur BPMN sait ouvrir le diagramme
        int shapes = doc.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/DI", "BPMNShape").getLength();
        int edges = doc.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/DI", "BPMNEdge").getLength();
        int flows = doc.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "sequenceFlow").getLength();
        assertThat(edges).isEqualTo(flows);
        assertThat(shapes).isEqualTo(10); // start, 7 étapes dont document + jointure (8 éléments), end → 1 + 8 + 1
    }

    @Test
    void eachKindUsesTheBuildingBlockOfTheCatalog() {
        String xml = compile(definition(
                step("q", Kind.QUESTION, Map.of("schemaKey", "avis")),
                step("e", Kind.EXTERNAL, Map.of("schemaKey", "avis", "portalRecipient", "dossierField:email", "portalLinkTtlDays", "7")),
                step("d", Kind.DOCUMENT, Map.of("documentType", "courrier", "documentLocale", "fr")),
                step("n", Kind.NOTIFY, Map.of("notificationChannels", "EMAIL", "notificationTo", "a@b.fr", "notificationSubject", "Objet <x>")),
                step("c", Kind.CONNECTOR, Map.of("connectorInstanceId", "123e4567-e89b-12d3-a456-426614174000")),
                step("m", Kind.DMN, Map.of("decisionTableReferenceKey", "grille")),
                step("x", Kind.EXPRESSION, Map.of("formula", "1 + 1", "formulaResult", "deux")),
                step("f", Kind.MAP_FIELDS, Map.of("fieldMapping", "score = connectorResult.json.score")),
                step("s", Kind.SET_WORKFLOW_STATUS, Map.of("targetWorkflowStatus", "VALIDE")),
                step("t", Kind.SET_DOSSIER_STATUS, Map.of("targetDossierStatus", "CLOTURE")),
                step("w", Kind.WAIT, Map.of("duration", "P2D")))).xml();
        assertThat(xml)
                .contains("<userTask id=\"q\" name=\"q\" flowable:formKey=\"user-question:avis\"/>")
                .contains("flowable:portalRecipient=\"dossierField:email\" flowable:portalLinkTtlDays=\"7\"")
                .contains("flowable:delegateExpression=\"${documentGenerationTask}\" flowable:documentType=\"courrier\" flowable:documentLocale=\"fr\"")
                .contains("<serviceTask id=\"dAttach\"").contains("${attachGeneratedDocumentTask}")
                .contains("${notificationDispatchTask}").contains("flowable:notificationSubject=\"Objet &lt;x&gt;\"") // échappé
                .contains("${connectorServiceTask}").contains("flowable:connectorInstanceId=\"123e4567-e89b-12d3-a456-426614174000\"")
                .contains("flowable:type=\"dmn\"").contains("<flowable:string>grille</flowable:string>")
                .contains("${businessExpressionTask}").contains("flowable:formula=\"1 + 1\"").contains("flowable:formulaResult=\"deux\"")
                .contains("${fieldMappingTask}").contains("${updateWorkflowLinkStatusTask}").contains("flowable:targetWorkflowStatus=\"VALIDE\"")
                .contains("${updateDossierStatusTask}").contains("flowable:targetDossierStatus=\"CLOTURE\"")
                .contains("<timeDuration>P2D</timeDuration>");
    }

    @Test
    void theOnlyExpressionsWrittenAreTheFixedBeansAndTheSimpleGatewayConditions() {
        String xml = compile(fullExample()).xml();
        // aucun ${…} hors delegateExpression et conditionExpression (liste blanche BPMN)
        String stripped = xml.replaceAll("flowable:delegateExpression=\"\\$\\{[A-Za-z]+}\"", "")
                .replaceAll("<conditionExpression[^>]*>\\$\\{[^}]*}</conditionExpression>", "");
        assertThat(stripped).doesNotContain("${").doesNotContain("#{");
        assertThat(xml).contains("${gros == true}").contains("${montant &gt;= 100}");
        assertThat(xml).contains("default=\"flow_choix_cloture\"");
    }

    @Test
    void aStringValueIsQuotedAndANumberIsNot() {
        String xml = compile(definition(
                step("q", Kind.QUESTION, Map.of("schemaKey", "avis")),
                new Step("choix", "choix", Kind.DECISION, Map.of(), null,
                        List.of(new Branch("decision", Operator.EQ, "approve", "fin"), new Branch("montant", Operator.LT, "10", "fin")), "fin"),
                step("fin", Kind.SET_WORKFLOW_STATUS, Map.of("targetWorkflowStatus", "VALIDE")))).xml();
        assertThat(xml).contains("${decision == 'approve'}").contains("${montant &lt; 10}");
    }

    @Test
    void aCorrectionLoopIsAllowedWhenThereIsAWayOut() {
        var def = definition(
                step("saisie", Kind.QUESTION, Map.of("schemaKey", "avis")),
                new Step("controle", "Conforme ?", Kind.DECISION, Map.of(), null,
                        List.of(new Branch("avis", Operator.EQ, "non", "saisie")), "fin"),
                step("fin", Kind.SET_WORKFLOW_STATUS, Map.of("targetWorkflowStatus", "VALIDE")));
        assertThat(compile(def).xml()).contains("sourceRef=\"controle\" targetRef=\"saisie\"");
    }

    @Test
    void aLoopWithNoWayOutAnUnreachableStepAndAParcoursThatNeverEndsAreRefusedNamingTheStep() {
        var noExit = definition(
                new Step("a", "a", Kind.QUESTION, Map.of("schemaKey", "avis"), "b", null, null),
                new Step("b", "b", Kind.QUESTION, Map.of("schemaKey", "avis"), "a", null, null));
        assertThatThrownBy(() -> compile(noExit)).isInstanceOf(InvalidParcoursException.class).hasMessageContaining("never ends");

        var trapped = definition(
                new Step("start1", "d", Kind.DECISION, Map.of(), null, List.of(new Branch("avis", Operator.EQ, "x", "boucle")), "fin"),
                new Step("boucle", "b", Kind.QUESTION, Map.of("schemaKey", "avis"), "boucle", null, null),
                step("fin", Kind.SET_WORKFLOW_STATUS, Map.of("targetWorkflowStatus", "VALIDE")));
        assertThatThrownBy(() -> compile(trapped)).hasMessageContaining("boucle").hasMessageContaining("no way out");

        var unreachable = definition(
                new Step("a", "a", Kind.SET_WORKFLOW_STATUS, Map.of("targetWorkflowStatus", "VALIDE"), "end", null, null),
                step("orphelin", Kind.QUESTION, Map.of("schemaKey", "avis")));
        assertThatThrownBy(() -> compile(unreachable)).hasMessageContaining("orphelin").hasMessageContaining("unreachable");
    }

    @Test
    void structuralMistakesAreRefusedWithAMessageThatNamesTheStep() {
        assertThatThrownBy(() -> compile(definition())).hasMessageContaining("at least one step");
        assertThatThrownBy(() -> compile(definition(step("a", Kind.QUESTION, Map.of("schemaKey", "x")), step("a", Kind.QUESTION, Map.of("schemaKey", "x")))))
                .hasMessageContaining("duplicate step id: a");
        assertThatThrownBy(() -> compile(definition(step("start", Kind.QUESTION, Map.of("schemaKey", "x"))))).hasMessageContaining("reserved");
        assertThatThrownBy(() -> compile(definition(step("q", Kind.QUESTION, Map.of())))).hasMessageContaining("step q").hasMessageContaining("schemaKey");
        assertThatThrownBy(() -> compile(definition(step("w", Kind.WAIT, Map.of("duration", "bientôt"))))).hasMessageContaining("step w").hasMessageContaining("ISO 8601");
        assertThatThrownBy(() -> compile(definition(step("w", Kind.WAIT, Map.of("duration", "P"))))).hasMessageContaining("ISO 8601");
        assertThatThrownBy(() -> compile(definition(step("s", Kind.SET_WORKFLOW_STATUS, Map.of("targetWorkflowStatus", "INCONNU"))))).hasMessageContaining("not declared");
        assertThatThrownBy(() -> compile(definition(step("s", Kind.SET_DOSSIER_STATUS, Map.of("targetDossierStatus", "CLOSED"))))).hasMessageContaining("unknown dossier status");
        assertThatThrownBy(() -> compile(definition(new Step("q", "q", Kind.QUESTION, Map.of("schemaKey", "x"), "nulle-part", null, null)))).hasMessageContaining("unknown step");
        assertThatThrownBy(() -> compile(definition(step("q", Kind.QUESTION, Map.of("schemaKey", "x", "extra", "y"))))).hasMessageContaining("does not apply");
        assertThatThrownBy(() -> compile(definition(new Step("d", "d", Kind.DECISION, Map.of(), null, List.of(), "fin"), step("fin", Kind.QUESTION, Map.of("schemaKey", "x")))))
                .hasMessageContaining("at least one branch");
        assertThatThrownBy(() -> compile(definition(new Step("d", "d", Kind.DECISION, Map.of(), null, List.of(new Branch("v", Operator.EQ, "1", "end")), null))))
                .hasMessageContaining("defaultNext");
    }

    @Test
    void expressionsAndInjectionsInParametersAreRefused() {
        assertThatThrownBy(() -> compile(definition(step("n", Kind.NOTIFY, Map.of("notificationChannels", "EMAIL", "notificationBody", "${secret}")))))
                .hasMessageContaining("literal");
        assertThatThrownBy(() -> compile(definition(step("x", Kind.QUESTION, Map.of("schemaKey", "a\" onclick=\"x")))))
                .hasMessageContaining("invalid format");
        assertThatThrownBy(() -> new Branch("v", Operator.EQ, "x' || 'y", "end")).isInstanceOf(InvalidParcoursException.class);
        assertThatThrownBy(() -> new Branch("a.b", Operator.EQ, "1", "end")).isInstanceOf(InvalidParcoursException.class);
        assertThatThrownBy(() -> new Step("Bad-Id", "x", Kind.QUESTION, Map.of(), null, null, null)).isInstanceOf(InvalidParcoursException.class);
    }

    @Test
    void statusesAreChecked() {
        var noInitial = new ParcoursDefinition(List.of(new WorkflowStatusDefinition("A", "A", false, StatusOutcome.NONE)), List.of(DossierStatus.OUVERT),
                null, List.of(step("q", Kind.QUESTION, Map.of("schemaKey", "x"))));
        assertThatThrownBy(() -> compile(noInitial)).hasMessageContaining("exactly one workflow status must be initial");
        var noAllowed = new ParcoursDefinition(STATUSES, List.of(), null, List.of(step("q", Kind.QUESTION, Map.of("schemaKey", "x"))));
        assertThatThrownBy(() -> compile(noAllowed)).hasMessageContaining("allowedDossierStatuses");
    }

    @Test
    void aDecisionOnAVariableNothingProducesGetsAWarning_butNotWhenAnUnknownProducerExists() {
        var d = definition(
                step("q", Kind.QUESTION, Map.of("schemaKey", "avis")),
                new Step("choix", "choix", Kind.DECISION, Map.of(), null, List.of(new Branch("inconnue", Operator.EQ, "x", "fin")), "fin"),
                step("fin", Kind.SET_WORKFLOW_STATUS, Map.of("targetWorkflowStatus", "VALIDE")));
        assertThat(compile(d).warnings()).anyMatch(w -> w.contains("choix") && w.contains("inconnue"));

        var withDmn = definition(
                step("m", Kind.DMN, Map.of("decisionTableReferenceKey", "grille")),
                new Step("choix", "choix", Kind.DECISION, Map.of(), null, List.of(new Branch("inconnue", Operator.EQ, "x", "fin")), "fin"),
                step("fin", Kind.SET_WORKFLOW_STATUS, Map.of("targetWorkflowStatus", "VALIDE")));
        assertThat(compile(withDmn).warnings()).isEmpty(); // les sorties d'une table DMN ne sont pas connues d'avance

        var knownField = definition(
                step("q", Kind.QUESTION, Map.of("schemaKey", "avis")),
                new Step("choix", "choix", Kind.DECISION, Map.of(), null, List.of(new Branch("avis", Operator.EQ, "x", "fin")), "fin"),
                step("fin", Kind.SET_WORKFLOW_STATUS, Map.of("targetWorkflowStatus", "VALIDE")));
        assertThat(compile(knownField).warnings()).isEmpty();
    }

    @Test
    void aMissingQuestionSchemaIsWarnedNotRefused() {
        var d = definition(step("q", Kind.QUESTION, Map.of("schemaKey", "absent")));
        assertThat(ParcoursCompiler.compile("p", "P", d, key -> null).warnings()).anyMatch(w -> w.contains("absent"));
    }

    @Test
    void twoBranchesToTheSameStepGetDistinctFlowIds() {
        var d = definition(
                step("q", Kind.QUESTION, Map.of("schemaKey", "avis")),
                new Step("choix", "choix", Kind.DECISION, Map.of(), null,
                        List.of(new Branch("avis", Operator.EQ, "a", "fin"), new Branch("avis", Operator.EQ, "b", "fin")), "fin"),
                step("fin", Kind.SET_WORKFLOW_STATUS, Map.of("targetWorkflowStatus", "VALIDE")));
        String xml = compile(d).xml();
        assertThat(xml).contains("id=\"flow_choix_fin\"").contains("id=\"flow_choix_fin_2\"").contains("id=\"flow_choix_fin_3\"");
    }

    // ---- revue adversariale LC5 (régressions permanentes) ----------------------------------------------------------

    private static ParcoursDefinition decisionOn(String variable, Operator op, String value) {
        return definition(
                new Step("choix", "Choix", Kind.DECISION, Map.of(), null, List.of(new Branch(variable, op, value, "fin")), "fin"),
                step("fin", Kind.SET_WORKFLOW_STATUS, Map.of("targetWorkflowStatus", "VALIDE")));
    }

    @Test
    void aBranchValueTheConditionGrammarCannotWriteIsRefusedAtDesignTimeNamingTheStep() {
        // H1 : -1 et 1.5 étaient acceptés puis refusés par le parseur de déploiement (flow_a_b, sans nom d'étape)
        for (String value : List.of("-1", "1.5", "a.b", "M. Dupont", "(x)", "a[0]", "a@b", "R&D", "a<b", "x;y", "&apos; || true || &apos;")) {
            assertThatThrownBy(() -> compile(decisionOn("x", Operator.EQ, value)))
                    .as("value %s", value)
                    .isInstanceOf(InvalidParcoursException.class);
        }
        assertThat(compile(decisionOn("x", Operator.GE, "100")).xml()).contains("${x &gt;= 100}");
        assertThat(compile(decisionOn("pays", Operator.EQ, "France métropolitaine")).xml()).contains("${pays == 'France métropolitaine'}");
    }

    @Test
    void aBranchVariableThatIsAnElReservedWordIsRefused() {
        // M2 : `empty == 1` passait le parseur et échouait à la passerelle
        for (String word : List.of("empty", "not", "and", "or", "eq", "ne", "lt", "gt", "le", "ge", "div", "mod", "null", "true", "false", "instanceof", "EMPTY")) {
            assertThatThrownBy(() -> compile(decisionOn(word, Operator.EQ, "1")))
                    .as("word %s", word)
                    .isInstanceOf(InvalidParcoursException.class);
        }
    }

    @Test
    void theProcessKeyAndLabelAreValidatedAndMayNotClashWithAStepId() {
        // M4 : clé brute dans le XML d'aperçu, label nul, collision clé / identifiant d'étape
        ParcoursDefinition d = definition(step("saisie", Kind.QUESTION, Map.of("schemaKey", "avis")));
        for (String key : new String[] {"p\" x=\"y", null, "Bad Key", "saisie", "start"}) {
            assertThatThrownBy(() -> ParcoursCompiler.compile(key, "L", d, k -> Set.of()))
                    .as("key %s", key)
                    .isInstanceOf(InvalidParcoursException.class);
        }
        assertThatThrownBy(() -> ParcoursCompiler.compile("p", null, d, k -> Set.of())).isInstanceOf(InvalidParcoursException.class);
        assertThatThrownBy(() -> ParcoursCompiler.compile("p", "x ${y}", d, k -> Set.of())).isInstanceOf(InvalidParcoursException.class);
    }

    @Test
    void waitDurationsThatTheTimerCannotParseOrThatAreZeroAreRefused() {
        for (String duration : List.of("P1DT", "PT0H", "P0D", "PT0M", "P", "PT")) {
            assertThatThrownBy(() -> compile(definition(
                            step("w", Kind.WAIT, Map.of("duration", duration)),
                            step("fin", Kind.SET_WORKFLOW_STATUS, Map.of("targetWorkflowStatus", "VALIDE")))))
                    .as("duration %s", duration)
                    .isInstanceOf(InvalidParcoursException.class);
        }
        assertThat(compile(definition(step("w", Kind.WAIT, Map.of("duration", "P1DT2H")))).xml()).contains("P1DT2H");
    }

    @Test
    void stepAndParcoursLabelsMayNotCarryExpressions() {
        assertThatThrownBy(() -> new Step("a", "${'x'.concat('y')}", Kind.QUESTION, Map.of("schemaKey", "avis"), null, null, null))
                .isInstanceOf(InvalidParcoursException.class).hasMessageContaining("a");
        assertThatThrownBy(() -> new Step("a", "#{x}", Kind.QUESTION, Map.of("schemaKey", "avis"), null, null, null))
                .isInstanceOf(InvalidParcoursException.class);
    }

    @Test
    void controlCharactersInParametersAreRefusedExceptNewlinesAndTabsWhichRoundTripExactly() throws Exception {
        assertThatThrownBy(() -> compile(definition(step("e", Kind.EXPRESSION, Map.of("formula", "1\u0001+2", "formulaResult", "r")))))
                .isInstanceOf(InvalidParcoursException.class);
        String xml = compile(definition(step("e", Kind.EXPRESSION, Map.of("formula", "1\t+\r\n2", "formulaResult", "r")))).xml();
        Element task = (Element) parse(xml).getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "serviceTask").item(0);
        assertThat(task.getAttributeNS("http://flowable.org/bpmn", "formula")).isEqualTo("1\t+\r\n2");
    }

    @Test
    void aDocumentWithoutAttachmentStillReservesItsAttachIdSoTheDiagramHasNoDuplicate() {
        assertThatThrownBy(() -> compile(definition(
                        step("foo", Kind.DOCUMENT, Map.of("documentType", "courrier", "attach", "false")),
                        step("fooAttach", Kind.SET_WORKFLOW_STATUS, Map.of("targetWorkflowStatus", "VALIDE")))))
                .isInstanceOf(InvalidParcoursException.class);
    }
}
