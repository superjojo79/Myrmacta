package com.flowforge.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.FieldCondition;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import com.flowforge.domain.dossier.model.InvalidDossierSearchException;
import com.flowforge.domain.dossier.port.in.SearchSimilarDossiersUseCase.Criterion;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierSearchPort;
import com.flowforge.domain.dossier.port.out.DossierSearchPort.FieldPredicate;
import com.flowforge.domain.dossier.port.out.DossierSearchPort.MatchSummary;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TODO §102 (issue #184, lot B) — use case de la brique « Rechercher des dossiers » : R2, R3, R5, R6, R7, R8. */
class SearchSimilarDossiersUseCaseImplTest {

    private static final String TENANT = "t";
    private static final String TYPE = "DEMANDE";

    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final DossierTypeRepository types = mock(DossierTypeRepository.class);
    private final DossierSearchPort search = mock(DossierSearchPort.class);
    private final SearchSimilarDossiersUseCaseImpl useCase = new SearchSimilarDossiersUseCaseImpl(dossiers, types, search);
    private Dossier current;

    private static FieldDefinition field(String name, FieldType type, boolean searchable, boolean sensitive, List<String> options) {
        return new FieldDefinition(name, name, type, false, null, options, null, sensitive, null, null, null, null, null, null,
                searchable, null, null, false);
    }

    @BeforeEach
    void setUp() {
        current = new Dossier(UUID.randomUUID(), TENANT, "R-1", TYPE, "l", DossierStatus.EN_COURS,
                Map.of("email", "a@x.fr", "secret", "S", "libre", "L", "rang", 3), null, 0, "u", Instant.now(), "u",
                Instant.now(), null, null, null);
        when(dossiers.findById(current.id())).thenReturn(Optional.of(current));
        when(types.findByCode(TENANT, TYPE)).thenReturn(Optional.of(DossierType.inherit(UUID.randomUUID(), TENANT, TYPE,
                "T", "p", true, false, List.of(
                        field("email", FieldType.STRING, true, false, null),
                        field("rang", FieldType.NUMBER, true, false, null),
                        field("jour", FieldType.DATE, true, false, null),
                        field("actif", FieldType.BOOLEAN, true, false, null),
                        field("canal", FieldType.SELECT, true, false, List.of("WEB", "GUICHET")),
                        field("libre", FieldType.STRING, false, false, null),
                        field("secret", FieldType.STRING, false, true, null)))));
        when(search.findOpenMatches(any(), any(), anyList(), any())).thenReturn(new MatchSummary(2, "R-0"));
    }

    @Test
    void searchesTheCurrentTypeWithTheValueOfTheCurrentDossierExcludingItself_R2_R3() {
        var result = useCase.search(current.id(), List.of(new Criterion.FromDossierField("email", "email")));

        assertThat(result).isEqualTo(new MatchSummary(2, "R-0"));
        verify(search).findOpenMatches(TENANT, TYPE,
                List.of(new FieldPredicate("email", DossierSearchPort.Kind.TEXT, FieldCondition.Op.EQ, List.of("a@x.fr"))),
                current.id());
    }

    @Test
    void convertsAVariableValueToTheTypeOfTheSearchedField() {
        useCase.search(current.id(), List.of(
                new Criterion.FromValue("rang", 3L),
                new Criterion.FromValue("jour", LocalDate.of(2026, 1, 5)),
                new Criterion.FromValue("actif", "true")));
        verify(search).findOpenMatches(TENANT, TYPE, List.of(
                new FieldPredicate("rang", DossierSearchPort.Kind.NUMBER, FieldCondition.Op.EQ, List.of(new BigDecimal("3"))),
                new FieldPredicate("jour", DossierSearchPort.Kind.TEXT, FieldCondition.Op.EQ, List.of("2026-01-05")),
                new FieldPredicate("actif", DossierSearchPort.Kind.BOOLEAN, FieldCondition.Op.EQ, List.of(true))),
                current.id());
        useCase.search(current.id(), List.of(new Criterion.FromValue("canal", "WEB"), new Criterion.FromValue("rang", 1.50d)));
        verify(search).findOpenMatches(TENANT, TYPE, List.of(
                new FieldPredicate("canal", DossierSearchPort.Kind.TEXT, FieldCondition.Op.EQ, List.of("WEB")),
                new FieldPredicate("rang", DossierSearchPort.Kind.NUMBER, FieldCondition.Op.EQ, List.of(new BigDecimal("1.5")))),
                current.id());
    }

    @Test
    void anEmptyOrUnmatchableValueFindsNothingWithoutQuerying_R6() {
        for (Object none : new Object[] {null, "", "  ", "pas un nombre"}) {
            assertThat(useCase.search(current.id(), List.of(
                    new Criterion.FromValue("email", "a@x.fr"), new Criterion.FromValue("rang", none))))
                    .isEqualTo(MatchSummary.none());
        }
        assertThat(useCase.search(current.id(), List.of(new Criterion.FromValue("jour", "05/01/2026")))).isEqualTo(MatchSummary.none());
        assertThat(useCase.search(current.id(), List.of(new Criterion.FromValue("email", "a\u0000b")))).isEqualTo(MatchSummary.none());
        assertThat(useCase.search(current.id(), List.of(new Criterion.FromDossierField("email", "jour")))).isEqualTo(MatchSummary.none());
        verify(search, never()).findOpenMatches(any(), any(), anyList(), any());
    }

    /** Revue adversariale : une option retirée du type reste stockée dans d'anciens dossiers ; elle se cherche telle quelle. */
    @Test
    void aSelectValueIsSearchedAsIsEvenWhenItIsNoLongerAnOption() {
        useCase.search(current.id(), List.of(new Criterion.FromValue("canal", "COURRIER")));
        verify(search).findOpenMatches(TENANT, TYPE,
                List.of(new FieldPredicate("canal", DossierSearchPort.Kind.TEXT, FieldCondition.Op.EQ, List.of("COURRIER"))),
                current.id());
    }

    @Test
    void aFieldThatIsNotSearchableIsADesignErrorNotAnEmptyResult_R5() {
        for (String field : new String[] {"libre", "secret", "inconnu"}) {
            assertThatThrownBy(() -> useCase.search(current.id(), List.of(new Criterion.FromValue(field, "x"))))
                    .as(field).isInstanceOf(InvalidDossierSearchException.class).hasMessageContaining("not searchable");
        }
    }

    @Test
    void aSensitiveOrUndeclaredSourceFieldIsRefusedSoTheCountIsNeverAnOracle_R8() {
        assertThatThrownBy(() -> useCase.search(current.id(), List.of(new Criterion.FromDossierField("email", "secret"))))
                .isInstanceOf(InvalidDossierSearchException.class).hasMessageContaining("sensitive");
        assertThatThrownBy(() -> useCase.search(current.id(), List.of(new Criterion.FromDossierField("email", "absent"))))
                .isInstanceOf(InvalidDossierSearchException.class).hasMessageContaining("not declared");
        verify(search, never()).findOpenMatches(eq(TENANT), any(), anyList(), any());
    }

    @Test
    void aStructuredVariableValueIsADesignError() {
        assertThatThrownBy(() -> useCase.search(current.id(), List.of(new Criterion.FromValue("email", Map.of("a", 1)))))
                .isInstanceOf(InvalidDossierSearchException.class).hasMessageContaining("scalar");
    }

    @Test
    void criteriaAreBounded() {
        assertThatThrownBy(() -> useCase.search(current.id(), List.of()))
                .isInstanceOf(InvalidDossierSearchException.class);
        assertThatThrownBy(() -> useCase.search(current.id(), List.of(new Criterion.FromValue("email", "a"),
                new Criterion.FromValue("rang", 1), new Criterion.FromValue("jour", "2026-01-01"),
                new Criterion.FromValue("actif", true)))).isInstanceOf(InvalidDossierSearchException.class);
    }
}
