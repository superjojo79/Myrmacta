package be.myrmacta.examples;

import be.myrmacta.domain.document.model.DocumentStorageException;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.DossierFieldRules;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.PieceType;
import be.myrmacta.domain.expression.model.ExpressionLimits;
import be.myrmacta.domain.precheck.PrecheckBudget;
import be.myrmacta.domain.precheck.PrecheckEngine;
import be.myrmacta.domain.precheck.PrecheckPrompt;
import be.myrmacta.domain.precheck.model.PrecheckFinding;
import be.myrmacta.domain.precheck.model.PrecheckLimits;
import be.myrmacta.domain.precheck.model.PrecheckOutcome;
import be.myrmacta.domain.precheck.model.PrecheckRun;
import be.myrmacta.domain.precheck.model.PrecheckSpec;
import be.myrmacta.domain.precheck.port.out.PrecheckModelPort;
import be.myrmacta.infrastructure.expression.JsonataExpressionEngine;
import be.myrmacta.infrastructure.precheck.PrecheckInfrastructure;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * TODO §106 (issue #211, lot D, D3) — jeu d'évaluation de la pré-vérification sur l'exemple riverain : chaque cas de
 * {@code evaluation/cas.json} passe par le vrai moteur (PDFBox, prompt, validation de la sortie) avec la brique du BPMN
 * {@code autorisation-riverain-preverification}, et la réponse du modèle à chaque question est comparée à l'attendu.
 * Le rapport donne la précision et le rappel par type de constat. Le modèle est fourni par l'appelant : le vrai
 * ({@link PrecheckEvaluation}) ou le faux fournisseur ({@code PrecheckEvaluationHarnessTest}).
 */
final class PrecheckEvaluationHarness {

    static final Path EXAMPLE = Path.of("exemples/autorisation-riverain");
    static final List<String> KINDS = List.of("OK", "INCONSISTENT", "TO_CHECK");
    static final String NO_ANSWER = "SANS_REPONSE";
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final String TENANT = "evaluation";

    record Piece(String type, String label, String text) {}

    record Case(String id, String description, Map<String, Object> fields, List<Piece> pieces,
            Map<String, String> expected) {}

    record Prediction(String caseId, String question, String expected, String actual) {
        boolean correct() {
            return expected.equals(actual);
        }
    }

    record Report(String model, List<Prediction> predictions, int failedRuns, int discarded, long tokensIn,
            long tokensOut) {

        double accuracy() {
            return predictions.isEmpty() ? 0 : (double) predictions.stream().filter(Prediction::correct).count()
                    / predictions.size();
        }

        /** {précision, rappel, nombre attendu} pour un type de constat. */
        double[] scores(String kind) {
            long predicted = predictions.stream().filter(p -> p.actual().equals(kind)).count();
            long expected = predictions.stream().filter(p -> p.expected().equals(kind)).count();
            long right = predictions.stream().filter(p -> p.correct() && p.expected().equals(kind)).count();
            return new double[] {predicted == 0 ? Double.NaN : (double) right / predicted,
                    expected == 0 ? Double.NaN : (double) right / expected, expected};
        }

        String markdown() {
            StringBuilder out = new StringBuilder("# Évaluation de la pré-vérification IA — exemple riverain\n\n")
                    .append("Modèle : `").append(model).append("` · prompt `").append(PrecheckPrompt.VERSION)
                    .append("` · ").append(predictions.size()).append(" réponses attendues · exactitude ")
                    .append(percent(accuracy())).append(" · exécutions en échec : ").append(failedRuns)
                    .append(" · constats écartés : ").append(discarded).append(" · jetons : ").append(tokensIn)
                    .append(" + ").append(tokensOut).append("\n\n")
                    .append("| Type de constat | Attendus | Précision | Rappel |\n|---|---:|---:|---:|\n");
            for (String kind : KINDS) {
                double[] s = scores(kind);
                out.append("| ").append(kind).append(" | ").append((long) s[2]).append(" | ").append(percent(s[0]))
                        .append(" | ").append(percent(s[1])).append(" |\n");
            }
            out.append("\n| Cas | Question | Attendu | Obtenu |\n|---|---|---|---|\n");
            for (Prediction p : predictions) {
                out.append("| ").append(p.caseId()).append(" | ").append(p.question()).append(" | ").append(p.expected())
                        .append(" | ").append(p.correct() ? p.actual() : "**" + p.actual() + "**").append(" |\n");
            }
            return out.toString();
        }

        private static String percent(double value) {
            return Double.isNaN(value) ? "—" : String.format(Locale.ROOT, "%.0f %%", value * 100);
        }
    }

    private PrecheckEvaluationHarness() {}

    static List<Case> cases() throws Exception {
        Map<String, Object> base = JSON.convertValue(
                JSON.readTree(EXAMPLE.resolve("config/exemple-dossier.json").toFile()).get("fields"), Map.class);
        List<Case> cases = new ArrayList<>();
        for (JsonNode node : JSON.readTree(EXAMPLE.resolve("evaluation/cas.json").toFile()).get("cas")) {
            Map<String, Object> fields = new LinkedHashMap<>(base);
            if (node.has("champs")) {
                fields.putAll(JSON.convertValue(node.get("champs"), Map.class));
            }
            List<Piece> pieces = new ArrayList<>();
            for (JsonNode piece : node.get("pieces")) {
                pieces.add(new Piece(piece.get("type").asString(), piece.get("intitule").asString(),
                        piece.get("texte").asString()));
            }
            Map<String, String> expected = new LinkedHashMap<>();
            node.get("attendu").properties().forEach(e -> expected.put(e.getKey(), e.getValue().asString()));
            cases.add(new Case(node.get("id").asString(), node.get("description").asString(), fields, pieces, expected));
        }
        return cases;
    }

    static DossierType dossierType() throws Exception {
        JsonNode node = JSON.readTree(EXAMPLE.resolve("config/type-dossier.json").toFile());
        List<FieldDefinition> fields = new ArrayList<>();
        node.get("fieldDefinitions").forEach(f -> fields.add(JSON.treeToValue(f, FieldDefinition.class)));
        List<PieceType> pieces = new ArrayList<>();
        node.get("pieceTypes").forEach(p -> pieces.add(JSON.treeToValue(p, PieceType.class)));
        return new DossierType(UUID.randomUUID(), TENANT, node.get("code").asString(), node.get("label").asString(),
                node.get("workflowKey").asString(), true, false, fields, null, false, null, List.of(), pieces,
                "evaluation", "evaluation");
    }

    /** Spécification de la brique telle que livrée dans le BPMN de l'exemple (attributs {@code flowable:precheck*}). */
    static PrecheckSpec spec() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        NodeList tasks = factory.newDocumentBuilder()
                .parse(EXAMPLE.resolve("autorisation-riverain-preverification.bpmn20.xml").toFile())
                .getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "serviceTask");
        for (int i = 0; i < tasks.getLength(); i++) {
            Element task = (Element) tasks.item(i);
            if ("preverification".equals(task.getAttribute("id"))) {
                Map<String, String> attributes = new HashMap<>();
                NamedNodeMap all = task.getAttributes();
                for (int a = 0; a < all.getLength(); a++) {
                    if ("http://flowable.org/bpmn".equals(all.item(a).getNamespaceURI())) {
                        attributes.put(all.item(a).getLocalName(), all.item(a).getNodeValue());
                    }
                }
                return PrecheckSpec.parse(attributes, "preverification");
            }
        }
        throw new IllegalStateException("activité « preverification » absente du BPMN de l'exemple");
    }

    static Report run(String modelLabel, PrecheckModelPort model, PrecheckLimits limits) throws Exception {
        DossierType type = dossierType();
        PrecheckSpec spec = spec();
        Map<String, byte[]> store = new HashMap<>();
        PrecheckEngine engine = new PrecheckEngine(TENANT,
                new DossierFieldRules(new JsonataExpressionEngine(ExpressionLimits.DEFAULTS, Clock.systemUTC())),
                new MemoryStorage(store), PrecheckInfrastructure.textExtractor(), model,
                PrecheckInfrastructure.outputReader(JSON),
                new PrecheckBudget(limits.maxTokensPerDay(), since -> 0, Clock.systemUTC()), limits);
        List<Prediction> predictions = new ArrayList<>();
        int failed = 0;
        int discarded = 0;
        long tokensIn = 0;
        long tokensOut = 0;
        for (Case c : cases()) {
            Dossier dossier = new Dossier(UUID.randomUUID(), TENANT, "EVAL-" + c.id(), type.code(), c.description(),
                    DossierStatus.EN_COURS, c.fields(), null, 0, "evaluation", Instant.now(), "evaluation",
                    Instant.now(), null, null, null);
            List<DossierElement> elements = new ArrayList<>();
            for (Piece piece : c.pieces()) {
                String ref = TENANT + "/" + UUID.randomUUID();
                store.put(ref, pdf(piece.text()));
                elements.add(new DossierElement(UUID.randomUUID(), dossier.id(), TENANT, ElementType.ATTACHMENT,
                        piece.label(), ref, Map.of("contentType", "application/pdf", PieceType.METADATA_KEY, piece.type()),
                        "evaluation", Instant.now(), null));
            }
            PrecheckOutcome outcome = engine.compute(dossier, type, elements, spec, Map.of("codePostalIncoherent", false));
            if (outcome.status() == PrecheckRun.Status.FAILED) {
                failed++;
            }
            discarded += outcome.discarded();
            tokensIn += outcome.tokensIn();
            tokensOut += outcome.tokensOut();
            c.expected().forEach((question, kind) -> predictions.add(new Prediction(c.id(), question, kind,
                    outcome.findings().stream()
                            .filter(f -> f.origin() == PrecheckFinding.Origin.MODEL
                                    && f.sourceType() == PrecheckFinding.SourceType.QUESTION
                                    && f.sourceRef().equals(question))
                            .map(f -> f.kind().name())
                            .findFirst()
                            .orElse(NO_ANSWER))));
        }
        return new Report(modelLabel, predictions, failed, discarded, tokensIn, tokensOut);
    }

    static Path write(Report report) throws Exception {
        Path target = Path.of("target", "precheck-evaluation.md");
        Files.createDirectories(target.getParent());
        Files.writeString(target, report.markdown());
        return target;
    }

    /** Texte en PDF à couche texte ; un texte vide donne une page sans texte (scan simulé). */
    static byte[] pdf(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            if (!text.isBlank()) {
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                    content.setLeading(12);
                    content.newLineAtOffset(40, 740);
                    for (String line : wrap(text, 95)) {
                        content.showText(line);
                        content.newLine();
                    }
                    content.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() + word.length() + 1 > width && !line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(line.isEmpty() ? "" : " ").append(word);
        }
        lines.add(line.toString());
        return lines;
    }

    private record MemoryStorage(Map<String, byte[]> store) implements DocumentStoragePort {
        @Override
        public String store(String tenantId, String contentType, byte[] content) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] retrieve(String tenantId, String storageRef) {
            byte[] content = store.get(storageRef);
            if (content == null) {
                throw new DocumentStorageException("missing " + storageRef);
            }
            return content;
        }

        @Override
        public boolean exists(String tenantId, String storageRef) {
            return store.containsKey(storageRef);
        }

        @Override
        public void delete(String tenantId, String storageRef) {
            store.remove(storageRef);
        }
    }
}
