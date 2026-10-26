package be.myrmacta.domain.precheck;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.DossierFieldRules;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.PieceType;
import be.myrmacta.domain.precheck.model.FindingKind;
import be.myrmacta.domain.precheck.model.ModelExchange;
import be.myrmacta.domain.precheck.model.ModelOutput;
import be.myrmacta.domain.precheck.model.PieceText;
import be.myrmacta.domain.precheck.model.PrecheckExceptions.PrecheckBudgetExceededException;
import be.myrmacta.domain.precheck.model.PrecheckExceptions.PrecheckDisabledException;
import be.myrmacta.domain.precheck.model.PrecheckExceptions.PrecheckModelUnavailableException;
import be.myrmacta.domain.precheck.model.PrecheckFinding;
import be.myrmacta.domain.precheck.model.PrecheckFinding.Origin;
import be.myrmacta.domain.precheck.model.PrecheckFinding.SourceType;
import be.myrmacta.domain.precheck.model.PrecheckLimits;
import be.myrmacta.domain.precheck.model.PrecheckOutcome;
import be.myrmacta.domain.precheck.model.PrecheckRun;
import be.myrmacta.domain.precheck.model.PrecheckSpec;
import be.myrmacta.domain.precheck.port.out.ModelOutputReaderPort;
import be.myrmacta.domain.precheck.port.out.PieceTextExtractorPort;
import be.myrmacta.domain.precheck.port.out.PrecheckModelPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * TODO §106 (issue #211, lot B, D-PRECHECK-PASSES) — calcul d'une pré-vérification, en trois passes du plus fiable au
 * moins fiable :
 * <ol>
 *   <li><b>complétude</b> déterministe : tous les champs obligatoires du type (et rendus obligatoires par une règle),
 *       sensibles compris en présence seulement, et pièces déclarées {@code obligatoire} par la brique (C6, R2) ;</li>
 *   <li><b>règles</b> : constats tirés de variables posées par des briques précédentes (R6) ;</li>
 *   <li><b>modèle</b> : un appel, sans outils, sur les seuls champs et pièces désignés et non sensibles (P7, R5, C3) ;
 *       sortie validée (schéma, sources connues, longueurs) avant d'atteindre l'agent (HC3).</li>
 * </ol>
 * Ne lit {@code Dossier.fields()} que pour la présence des valeurs et les champs désignés non sensibles (liste ArchUnit).
 * Ne lève jamais : un échec du modèle ou du budget donne un résultat {@code FAILED} qui garde les constats déterministes.
 */
public final class PrecheckEngine {

    public static final int MAX_MODEL_FINDINGS = 30;
    static final int MAX_FIELD_VALUE_CHARS = 500;

    private final String tenantId;
    private final DossierFieldRules fieldRules;
    private final DocumentStoragePort storage;
    private final PieceTextExtractorPort extractor;
    private final PrecheckModelPort model;
    private final ModelOutputReaderPort outputReader;
    private final PrecheckBudget budget;
    private final PrecheckLimits limits;
    private final SecureRandom random = new SecureRandom();

    public PrecheckEngine(String tenantId, DossierFieldRules fieldRules, DocumentStoragePort storage,
            PieceTextExtractorPort extractor, PrecheckModelPort model, ModelOutputReaderPort outputReader,
            PrecheckBudget budget, PrecheckLimits limits) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.fieldRules = Objects.requireNonNull(fieldRules, "fieldRules");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.extractor = Objects.requireNonNull(extractor, "extractor");
        this.model = Objects.requireNonNull(model, "model");
        this.outputReader = Objects.requireNonNull(outputReader, "outputReader");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public PrecheckOutcome compute(Dossier dossier, DossierType type, List<DossierElement> elements, PrecheckSpec spec,
            Map<String, Object> variables) {
        Findings findings = new Findings();
        completeness(dossier, type, elements, spec, findings);
        rules(spec, variables == null ? Map.of() : variables, findings);
        if (!spec.needsModel()) {
            return new PrecheckOutcome(PrecheckRun.Status.SUCCEEDED, null, null, null, null, null, null, 0, 0, 0,
                    findings.list);
        }
        Input input = input(dossier, type, elements, spec, findings);
        if (!input.hasContent()) {
            // Rien de lisible ni de transmissible : pas d'appel (les constats déterministes disent pourquoi).
            return new PrecheckOutcome(PrecheckRun.Status.SUCCEEDED, null, null, null, PrecheckPrompt.VERSION, null, null,
                    0, 0, 0, findings.list);
        }
        String nonce = HexFormat.of().formatHex(randomBytes());
        String system = PrecheckPrompt.system(nonce);
        String user = input.render(nonce);
        String hash = sha256(system + "\n" + user);
        ModelExchange.Reply reply;
        try {
            budget.requireAvailable();
            reply = model.complete(new ModelExchange.Request(system, user, PrecheckPrompt.OUTPUT_SCHEMA,
                    limits.maxOutputTokens()));
        } catch (PrecheckBudgetExceededException ex) {
            return failed("daily token budget reached", hash, findings);
        } catch (PrecheckDisabledException ex) {
            return failed("AI pre-check is disabled", hash, findings);
        } catch (PrecheckModelUnavailableException ex) {
            return failed("the language model provider is unavailable", hash, findings);
        }
        ModelOutput output = outputReader.read(reply.json()).orElse(null);
        if (output == null) {
            return new PrecheckOutcome(PrecheckRun.Status.FAILED, "the language model answer is not valid JSON",
                    reply.provider(), reply.model(), PrecheckPrompt.VERSION, hash, null, reply.tokensIn(),
                    reply.tokensOut(), 0, findings.list);
        }
        int discarded = modelFindings(output, input, spec, findings);
        return new PrecheckOutcome(PrecheckRun.Status.SUCCEEDED, null, reply.provider(), reply.model(),
                PrecheckPrompt.VERSION, hash, output.summary(), reply.tokensIn(), reply.tokensOut(), discarded,
                findings.list);
    }

    private PrecheckOutcome failed(String failure, String hash, Findings findings) {
        return new PrecheckOutcome(PrecheckRun.Status.FAILED, failure, null, null, PrecheckPrompt.VERSION, hash, null, 0,
                0, 0, findings.list);
    }

    // --- Passe 1 : complétude -----------------------------------------------------------------------------------

    private void completeness(Dossier dossier, DossierType type, List<DossierElement> elements, PrecheckSpec spec,
            Findings findings) {
        Map<String, Object> values = dossier.fields();
        Set<String> requiredByRule = Set.of();
        Set<String> hidden = Set.of();
        try {
            DossierFieldRules.Outcome rules = fieldRules.apply(type.fieldDefinitions(), values, Set.of());
            requiredByRule = rules.requiredByRule();
            hidden = rules.hidden();
        } catch (RuntimeException ex) {
            // Règles du type inévaluables : seuls les champs obligatoires fixes sont contrôlés.
        }
        for (FieldDefinition definition : type.fieldDefinitions()) {
            if (definition.hasFormula() || hidden.contains(definition.name())) {
                continue;
            }
            boolean required = definition.required() || requiredByRule.contains(definition.name());
            if (required && blank(values.get(definition.name()))) {
                findings.deterministic(FindingKind.MISSING, SourceType.FIELD, definition.name(),
                        "Champ obligatoire manquant : " + label(definition) + ".");
            }
        }
        for (PrecheckSpec.PieceRequirement piece : spec.pieces()) {
            PieceType pieceType = type.pieceType(piece.key()).orElse(null);
            if (pieceType == null) {
                findings.deterministic(FindingKind.TO_CHECK, SourceType.PIECE, piece.key(),
                        "Type de pièce « " + piece.key() + " » inconnu du type de dossier : non contrôlé.");
                continue;
            }
            if (piece.required() && attachmentsOf(elements, piece.key()).isEmpty()) {
                findings.deterministic(FindingKind.MISSING, SourceType.PIECE, piece.key(),
                        "Pièce manquante : " + pieceType.label() + ".");
            }
        }
    }

    // --- Passe 2a : règles ----------------------------------------------------------------------------------------

    private static void rules(PrecheckSpec spec, Map<String, Object> variables, Findings findings) {
        for (PrecheckSpec.Rule rule : spec.rules()) {
            if (!variables.containsKey(rule.variable())) {
                findings.deterministic(FindingKind.TO_CHECK, SourceType.RULE, rule.variable(),
                        "Règle non évaluée : variable « " + rule.variable() + " » absente.");
            } else if (rule.condition().test(variables.get(rule.variable()))) {
                findings.deterministic(rule.kind(), SourceType.RULE, rule.variable(), rule.message());
            }
        }
    }

    // --- Passe 2b et 3 : entrée minimisée du modèle ------------------------------------------------------------------

    private Input input(Dossier dossier, DossierType type, List<DossierElement> elements, PrecheckSpec spec,
            Findings findings) {
        Input input = new Input(spec.questions());
        Map<String, Object> values = dossier.fields();
        for (String name : spec.fields()) {
            FieldDefinition definition = type.fieldDefinitions().stream()
                    .filter(d -> d.name().equals(name)).findFirst().orElse(null);
            if (definition == null) {
                findings.deterministic(FindingKind.TO_CHECK, SourceType.FIELD, name,
                        "Champ « " + name + " » inconnu du type de dossier : non transmis.");
            } else if (definition.isSensitive()) {
                // C3 — devenu sensible depuis le déploiement : jamais transmis, signalé.
                findings.deterministic(FindingKind.TO_CHECK, SourceType.FIELD, name,
                        "Champ « " + label(definition) + " » sensible : non transmis au modèle.");
            } else {
                input.fields.put(name, label(definition) + " : " + display(values.get(name)));
            }
        }
        int remainingPieces = limits.maxPiecesRead();
        int remainingChars = limits.maxCharsTotal();
        int skipped = 0;
        for (PrecheckSpec.PieceRequirement piece : spec.readPieces()) {
            PieceType pieceType = type.pieceType(piece.key()).orElse(null);
            if (pieceType == null) {
                continue; // déjà signalé par la complétude
            }
            if (pieceType.sensitive()) {
                findings.deterministic(FindingKind.TO_CHECK, SourceType.PIECE, piece.key(),
                        "Pièces « " + pieceType.label() + " » sensibles : non lues par le modèle.");
                continue;
            }
            for (DossierElement element : attachmentsOf(elements, piece.key())) {
                if (remainingPieces == 0 || remainingChars == 0) {
                    skipped++;
                    continue;
                }
                PieceText text = read(element, Math.min(limits.maxCharsPerPiece(), remainingChars));
                remainingPieces--;
                if (!text.readable()) {
                    findings.deterministic(FindingKind.TO_CHECK, SourceType.PIECE, piece.key(), "Pièce « "
                            + element.label() + " » (" + pieceType.label() + ") non lisible (" + reason(text.status())
                            + ") : à vérifier par l'agent.");
                    continue;
                }
                if (text.truncated()) {
                    findings.deterministic(FindingKind.TO_CHECK, SourceType.PIECE, piece.key(), "Pièce « "
                            + element.label() + " » lue en partie seulement (limites de lecture).");
                }
                remainingChars -= text.text().length();
                input.pieces.add(new PieceInput(piece.key(), pieceType.label(), element.label(), text.text()));
            }
        }
        if (skipped > 0) {
            findings.deterministic(FindingKind.TO_CHECK, SourceType.PIECE, "*", skipped
                    + " pièce(s) non lue(s) : limite de lecture atteinte (" + limits.maxPiecesRead() + " pièces, "
                    + limits.maxCharsTotal() + " caractères).");
        }
        return input;
    }

    private PieceText read(DossierElement element, int maxChars) {
        Object contentType = element.metadata().get("contentType");
        try {
            byte[] content = storage.retrieve(tenantId, element.storageRef());
            return extractor.extract(content, contentType == null ? null : contentType.toString(),
                    limits.maxPagesPerPiece(), maxChars);
        } catch (RuntimeException unreadable) {
            return PieceText.unreadable(PieceText.Status.UNREADABLE);
        }
    }

    // --- Passe 3 : constats du modèle, validés -------------------------------------------------------------------

    private static int modelFindings(ModelOutput output, Input input, PrecheckSpec spec, Findings findings) {
        Set<String> fields = input.fields.keySet();
        Set<String> pieces = new LinkedHashSet<>();
        input.pieces.forEach(p -> pieces.add(p.key()));
        Map<String, PrecheckSpec.Question> questions = new LinkedHashMap<>();
        spec.questions().forEach(q -> questions.put(q.key(), q));
        Set<String> answered = new LinkedHashSet<>();
        int discarded = 0;
        int kept = 0;
        for (ModelOutput.Item item : output.findings()) {
            FindingKind kind = enumOf(FindingKind.class, item.kind());
            SourceType source = enumOf(SourceType.class, item.sourceType());
            String ref = item.sourceRef() == null ? "" : item.sourceRef().strip();
            boolean known = source != null && switch (source) {
                case FIELD -> fields.contains(ref);
                case PIECE -> pieces.contains(ref);
                case QUESTION -> questions.containsKey(ref);
                case RULE -> false; // le modèle ne produit jamais de constat de règle
            };
            Double confidence = item.confidence();
            if (kind == null || !known || item.message() == null || item.message().isBlank()
                    || confidence != null && (confidence.isNaN() || confidence < 0 || confidence > 1)
                    || kept >= MAX_MODEL_FINDINGS) {
                discarded++;
                continue;
            }
            if (source == SourceType.QUESTION) {
                answered.add(ref);
            }
            findings.add(new PrecheckFinding(UUID.randomUUID(), findings.list.size(), kind, Origin.MODEL, source, ref,
                    item.message(), confidence, null, null, null));
            kept++;
        }
        for (PrecheckSpec.Question question : spec.questions()) {
            if (!answered.contains(question.key())) {
                findings.deterministic(FindingKind.TO_CHECK, SourceType.QUESTION, question.key(),
                        "Question sans réponse exploitable du modèle : " + question.text());
            }
        }
        return discarded;
    }

    // --- Outils ------------------------------------------------------------------------------------------------

    private static List<DossierElement> attachmentsOf(List<DossierElement> elements, String pieceKey) {
        return elements.stream()
                .filter(e -> e.type() == ElementType.ATTACHMENT && pieceKey.equals(e.metadata().get(PieceType.METADATA_KEY)))
                .sorted(Comparator.comparing(DossierElement::addedAt).reversed())
                .toList();
    }

    private static boolean blank(Object value) {
        return value == null
                || value instanceof CharSequence s && s.toString().isBlank()
                || value instanceof Collection<?> c && c.isEmpty()
                || value instanceof Map<?, ?> m && m.isEmpty();
    }

    private static String label(FieldDefinition definition) {
        return definition.label() == null || definition.label().isBlank() ? definition.name() : definition.label();
    }

    private static String display(Object value) {
        String text = value == null ? "(vide)" : String.valueOf(value);
        return PrecheckFinding.truncate(text.replaceAll("[\\p{Cc}]", " ").strip(), MAX_FIELD_VALUE_CHARS);
    }

    private static String reason(PieceText.Status status) {
        return switch (status) {
            case NO_TEXT -> "scan ou image, sans texte";
            case ENCRYPTED -> "protégée par un mot de passe";
            case UNSUPPORTED -> "format non pris en charge";
            case TOO_COMPLEX -> "trop complexe pour être lue dans le délai";
            default -> "fichier illisible";
        };
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String name) {
        if (name == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, name.strip());
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private byte[] randomBytes() {
        byte[] bytes = new byte[8];
        random.nextBytes(bytes);
        return bytes;
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static final class Findings {
        private final List<PrecheckFinding> list = new ArrayList<>();

        void deterministic(FindingKind kind, SourceType type, String ref, String message) {
            list.add(PrecheckFinding.deterministic(list.size(), kind, type, ref, message));
        }

        void add(PrecheckFinding finding) {
            list.add(finding);
        }
    }

    private record PieceInput(String key, String typeLabel, String elementLabel, String text) {}

    /** Entrée du modèle : seuls les champs désignés non sensibles, les pièces lues et les questions. */
    private static final class Input {
        private final Map<String, String> fields = new LinkedHashMap<>();
        private final List<PieceInput> pieces = new ArrayList<>();
        private final List<PrecheckSpec.Question> questions;

        Input(List<PrecheckSpec.Question> questions) {
            this.questions = questions;
        }

        boolean hasContent() {
            return !fields.isEmpty() || !pieces.isEmpty() || !questions.isEmpty();
        }

        String render(String nonce) {
            StringBuilder out = new StringBuilder();
            out.append("<<<CHAMPS-").append(nonce).append(">>>\n");
            fields.forEach((name, line) -> out.append("- ").append(name).append(" (").append(neutral(line)).append(")\n"));
            out.append("<<<FIN-").append(nonce).append(">>>\n");
            for (PieceInput piece : pieces) {
                out.append("<<<PIECE-").append(nonce).append(" clé=").append(piece.key()).append(" type=\"")
                        .append(neutral(piece.typeLabel())).append("\" intitulé=\"").append(neutral(piece.elementLabel()))
                        .append("\">>>\n").append(neutral(piece.text())).append("\n<<<FIN-").append(nonce).append(">>>\n");
            }
            out.append("<<<QUESTIONS-").append(nonce).append(">>>\n");
            questions.forEach(q -> out.append("- ").append(q.key()).append(" : ").append(neutral(q.text())).append('\n'));
            out.append("<<<FIN-").append(nonce).append(">>>\n");
            return out.toString();
        }

        /** Une donnée ne peut pas imiter une borne : les chevrons triples sont neutralisés. */
        private static String neutral(String text) {
            return text.replace("<<<", "‹‹‹").replace(">>>", "›››");
        }
    }
}
