package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.deadline.model.BusinessCalendar;
import be.myrmacta.domain.deadline.port.out.TenantCalendars;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TODO §115 (D-DEADLINE-TIMER) — échéance tirée d'un champ date ou d'un délai, instants de minuteur en UTC. */
class ComputeDeadlineDelegateTest {

    private static final String TENANT = "tenant_dev";

    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final DossierTypeRepository types = mock(DossierTypeRepository.class);
    private final DelegateExecution execution = mock(DelegateExecution.class);
    private final Map<String, Object> written = new HashMap<>();
    private final ServiceTask task = new ServiceTask();
    private final UUID dossierId = UUID.randomUUID();
    private final Map<String, Object> fields = new HashMap<>();
    private ComputeDeadlineDelegate delegate;

    @BeforeEach
    void setUp() {
        TenantCalendars calendars = mock(TenantCalendars.class);
        when(calendars.get(TENANT)).thenReturn(BusinessCalendar.defaults());
        delegate = new ComputeDeadlineDelegate(dossiers, types, calendars, new TenantProperties(TENANT),
                Clock.fixed(Instant.parse("2026-10-31T15:00:00Z"), ZoneOffset.UTC));
        when(execution.getCurrentFlowElement()).thenReturn(task);
        when(execution.getCurrentActivityId()).thenReturn("calculer");
        when(execution.getVariable("dossierId")).thenReturn(dossierId.toString());
        org.mockito.Mockito.doAnswer(call -> written.put(call.getArgument(0), call.getArgument(1)))
                .when(execution).setVariable(anyString(), any());
        when(dossiers.findById(dossierId)).thenAnswer(call -> Optional.of(new Dossier(dossierId, TENANT, "DOS-1", "FACTURE", "x",
                DossierStatus.EN_COURS, Map.copyOf(fields), null, 0L, "u", Instant.EPOCH, "u", Instant.EPOCH, null, null, null)));
        when(types.findByCode(TENANT, "FACTURE")).thenReturn(Optional.of(new DossierType(UUID.randomUUID(), TENANT, "FACTURE", "Facture",
                "wf", true, false, List.of(field("echeance", FieldType.DATE, false), field("objet", FieldType.STRING, false),
                        field("secret", FieldType.DATE, true)), null, false, null, List.of(), List.of(), "s", "s")));
    }

    @Test
    void theDeadlineFieldGivesTheDate_theReminderAndTheDayAfterAtMidnightUtc() {
        fields.put("echeance", "2026-11-30");
        attribute("champEcheance", "echeance");
        attribute("rappelJours", "5");

        delegate.execute(execution);

        assertThat(written).containsEntry("echeance", "2026-11-30")
                .containsEntry("echeanceRappel", "2026-11-25T00:00:00Z")
                .containsEntry("echeanceDepassee", "2026-12-01T00:00:00Z");
    }

    @Test
    void anEmptyFieldFallsBackOnTheDelay_withoutReminderTheReminderIsTheDayItself() {
        attribute("champEcheance", "echeance");
        attribute("delaiJours", "10");

        delegate.execute(execution);

        assertThat(written).containsEntry("echeance", "2026-11-10")
                .containsEntry("echeanceRappel", "2026-11-10T00:00:00Z")
                .containsEntry("echeanceDepassee", "2026-11-11T00:00:00Z");
    }

    @Test
    void aBusinessDayDelaySkipsTheWeekend_andTheOverdueInstantStaysTheNextCalendarDay() {
        attribute("delaiJours", "1");
        attribute("delaiOuvrable", "OUVRABLE");

        delegate.execute(execution);

        assertThat(written).containsEntry("echeance", "2026-11-02")
                .containsEntry("echeanceDepassee", "2026-11-03T00:00:00Z");
    }

    @Test
    void withoutADateNorADelayTheBlockFails_ratherThanATimerThatNeverFires() {
        attribute("champEcheance", "echeance");

        assertThatThrownBy(() -> delegate.execute(execution)).isInstanceOf(FlowableException.class)
                .hasMessageContaining("champ echeance vide et pas de délai");
        assertThat(written).isEmpty();
    }

    @Test
    void onlyANonSensitiveDateFieldOfTheTypeIsRead() {
        fields.put("objet", "2026-11-30");
        attribute("champEcheance", "objet");
        assertThatThrownBy(() -> delegate.execute(execution)).hasMessageContaining("n'est pas un champ date non sensible");

        task.getAttributes().clear();
        attribute("champEcheance", "secret");
        assertThatThrownBy(() -> delegate.execute(execution)).hasMessageContaining("n'est pas un champ date non sensible");

        task.getAttributes().clear();
        attribute("champEcheance", "inconnu");
        assertThatThrownBy(() -> delegate.execute(execution)).hasMessageContaining("champ inconnu du type FACTURE");
        assertThat(written).isEmpty();
    }

    @Test
    void aProcessVariableOfTheSameNameIsNeverRead() {
        when(execution.getVariable("delaiJours")).thenReturn("3");

        assertThatThrownBy(() -> delegate.execute(execution)).hasMessageContaining("ni champ ni délai");
    }

    private void attribute(String name, String value) {
        ExtensionAttribute attribute = new ExtensionAttribute();
        attribute.setNamespace(DeployedActivityParameters.FLOWABLE_NAMESPACE);
        attribute.setNamespacePrefix("flowable");
        attribute.setName(name);
        attribute.setValue(value);
        task.addAttribute(attribute);
    }

    private static FieldDefinition field(String name, FieldType type, boolean sensitive) {
        return new FieldDefinition(name, name, type, false, null, List.of(), null, sensitive, null, null, null, null, null, null, null,
                null, null, null);
    }
}
