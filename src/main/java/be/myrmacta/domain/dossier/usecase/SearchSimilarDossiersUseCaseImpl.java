package be.myrmacta.domain.dossier.usecase;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldCondition;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.InvalidDossierSearchException;
import be.myrmacta.domain.dossier.model.UniqueFieldKeys;
import be.myrmacta.domain.dossier.port.in.SearchSimilarDossiersUseCase;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierSearchPort;
import be.myrmacta.domain.dossier.port.out.DossierSearchPort.FieldPredicate;
import be.myrmacta.domain.dossier.port.out.DossierSearchPort.MatchSummary;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * TODO §102 (issue #184, lot B). Une valeur vide, ou qu'aucun dossier ne peut tenir (texte dans un champ nombre, date mal
 * formée), ne correspond à rien : nombre 0 sans requête (R6). Un champ non cherchable, une source
 * sensible ou non déclarée, une valeur structurée sont des erreurs de conception : exception, donc incident rejouable (R5).
 *
 * <p>Lit {@code Dossier.fields()} (liste blanche ArchUnit) : seulement les champs sources déclarés et non sensibles, dont la
 * valeur ne sert qu'à la requête et n'est jamais renvoyée.
 */
public final class SearchSimilarDossiersUseCaseImpl implements SearchSimilarDossiersUseCase {

    private final DossierRepository dossierRepository;
    private final DossierTypeRepository typeRepository;
    private final DossierSearchPort searchPort;

    public SearchSimilarDossiersUseCaseImpl(
            DossierRepository dossierRepository, DossierTypeRepository typeRepository, DossierSearchPort searchPort) {
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.typeRepository = Objects.requireNonNull(typeRepository, "typeRepository");
        this.searchPort = Objects.requireNonNull(searchPort, "searchPort");
    }

    @Override
    public MatchSummary search(UUID dossierId, List<Criterion> criteria) {
        Objects.requireNonNull(dossierId, "dossierId");
        Objects.requireNonNull(criteria, "criteria");
        if (criteria.isEmpty() || criteria.size() > MAX_CRITERIA) {
            throw new InvalidDossierSearchException("a dossier search takes 1 to " + MAX_CRITERIA + " criteria");
        }
        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + dossierId));
        DossierType type = typeRepository.findByCode(dossier.tenantId(), dossier.dossierTypeCode())
                .orElseThrow(() -> new DossierNotFoundException(
                        "Dossier type not found: " + dossier.tenantId() + "/" + dossier.dossierTypeCode()));

        // Toutes les règles de conception d'abord : un critère invalide échoue même quand un autre est vide.
        Set<String> seen = new HashSet<>();
        List<FieldDefinition> targets = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Criterion criterion : criteria) {
            if (!seen.add(criterion.field())) {
                throw new InvalidDossierSearchException("field '" + criterion.field() + "' is searched twice");
            }
            targets.add(searchableField(type, criterion.field()));
            values.add(valueOf(type, dossier, criterion));
        }
        List<FieldPredicate> predicates = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            Optional<FieldPredicate> predicate = equality(targets.get(i), values.get(i));
            if (predicate.isEmpty()) {
                return MatchSummary.none();
            }
            predicates.add(predicate.get());
        }
        return searchPort.findOpenMatches(dossier.tenantId(), type.code(), List.copyOf(predicates), dossier.id());
    }

    private static FieldDefinition searchableField(DossierType type, String name) {
        return declared(type, name)
                .filter(def -> def.isSearchable() && !def.isSensitive())
                .orElseThrow(() -> new InvalidDossierSearchException(
                        "field '" + name + "' is not searchable on dossier type " + type.code()));
    }

    private static Object valueOf(DossierType type, Dossier dossier, Criterion criterion) {
        return switch (criterion) {
            case Criterion.FromValue fromValue -> requireScalar(criterion.field(), fromValue.value());
            case Criterion.FromDossierField fromField -> {
                FieldDefinition source = declared(type, fromField.sourceField())
                        .orElseThrow(() -> new InvalidDossierSearchException("source field '" + fromField.sourceField()
                                + "' is not declared on dossier type " + type.code()));
                if (source.isSensitive()) {
                    throw new InvalidDossierSearchException("source field '" + fromField.sourceField()
                            + "' is sensitive: its value cannot drive a search (the count would reveal it)");
                }
                yield requireScalar(criterion.field(), dossier.fields().get(fromField.sourceField()));
            }
        };
    }

    private static Optional<FieldDefinition> declared(DossierType type, String name) {
        return type.fieldDefinitions().stream().filter(def -> def.name().equals(name)).findFirst();
    }

    private static Object requireScalar(String field, Object value) {
        if (value instanceof Map<?, ?> || value instanceof Iterable<?>) {
            throw new InvalidDossierSearchException("the value searched in '" + field + "' must be a scalar");
        }
        return value;
    }

    /**
     * Prédicat d'égalité typé comme la recherche D2, ou vide si aucun dossier ne peut tenir cette valeur. Une valeur de
     * {@code SELECT} est cherchée telle quelle : une option retirée du type reste stockée dans d'anciens dossiers.
     */
    private static Optional<FieldPredicate> equality(FieldDefinition def, Object raw) {
        if (raw == null || raw instanceof String s && (s.isBlank() || s.indexOf('\u0000') >= 0)) {
            return Optional.empty();
        }
        String text = raw instanceof LocalDate date ? date.toString() : String.valueOf(raw);
        return switch (def.type()) {
            case NUMBER -> number(raw, text).map(n -> predicate(def, DossierSearchPort.Kind.NUMBER, n));
            case BOOLEAN -> raw instanceof Boolean || "true".equals(text) || "false".equals(text)
                    ? Optional.of(predicate(def, DossierSearchPort.Kind.BOOLEAN, Boolean.valueOf(text)))
                    : Optional.empty();
            case DATE -> isoDate(text).map(d -> predicate(def, DossierSearchPort.Kind.TEXT, d));
            default -> Optional.of(predicate(def, DossierSearchPort.Kind.TEXT, text));
        };
    }

    private static Optional<BigDecimal> number(Object raw, String text) {
        try {
            String canonical = UniqueFieldKeys.canonical(be.myrmacta.domain.dossier.model.FieldType.NUMBER,
                    raw instanceof Number ? raw : text.strip());
            return Optional.of(new BigDecimal(canonical));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    private static Optional<String> isoDate(String text) {
        try {
            return Optional.of(LocalDate.parse(text.strip()).toString());
        } catch (DateTimeParseException notADate) {
            return Optional.empty();
        }
    }

    private static FieldPredicate predicate(FieldDefinition def, DossierSearchPort.Kind kind, Object value) {
        return new FieldPredicate(def.name(), kind, FieldCondition.Op.EQ, List.of(value));
    }
}
