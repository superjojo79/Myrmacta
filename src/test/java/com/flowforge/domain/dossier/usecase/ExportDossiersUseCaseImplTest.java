package com.flowforge.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.ExportDossiersQuery;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import com.flowforge.domain.dossier.port.in.ExportDossiersUseCase;
import com.flowforge.domain.dossier.port.out.DossierExportPort;
import com.flowforge.domain.dossier.port.out.DossierSearchPort;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExportDossiersUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final List<FieldDefinition> FIELD_DEFINITIONS = List.of(
            new FieldDefinition("priority", "Priority", FieldType.STRING, false, null, List.of(), null, false),
            new FieldDefinition("ssn", "SSN", FieldType.STRING, false, null, List.of(), null, true));

    private DossierTypeRepository typeRepository;
    private DossierExportPort exportPort;
    private DossierAccessPolicy accessPolicy;
    private ExportDossiersUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        typeRepository = org.mockito.Mockito.mock(DossierTypeRepository.class);
        exportPort = org.mockito.Mockito.mock(DossierExportPort.class);
        accessPolicy = org.mockito.Mockito.mock(DossierAccessPolicy.class);
        useCase = new ExportDossiersUseCaseImpl(typeRepository, exportPort, accessPolicy);
    }

    @Test
    void export_deniedByAccessPolicy_neverCallsPort() {
        AccessContext.User actor = AccessContext.user("u1", Set.of("DOSSIER_VIEWER"));
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(accessPolicy).assertCanViewType(TENANT, "FAC", actor);

        assertThatThrownBy(() -> useCase.export(TENANT, new ExportDossiersQuery("FAC", null, null), actor))
                .isInstanceOf(AccessDeniedException.class);

        verify(exportPort, never()).streamForExport(any(), any());
    }

    @Test
    void export_unknownDossierType_throwsNotFound() {
        AccessContext.User actor = AccessContext.user("admin", Set.of("TENANT_ADMIN"));
        when(typeRepository.findByCode(TENANT, "GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.export(TENANT, new ExportDossiersQuery("GHOST", null, null), actor))
                .isInstanceOf(DossierNotFoundException.class);

        verify(exportPort, never()).streamForExport(any(), any());
    }

    @Test
    void export_admin_seesSensitiveFieldsInClear() {
        AccessContext.User admin = AccessContext.user("admin", Set.of("TENANT_ADMIN"));
        when(typeRepository.findByCode(TENANT, "FAC")).thenReturn(Optional.of(type()));
        Dossier dossier = dossier(Map.of("priority", "HIGH", "ssn", "123-45-6789"));
        when(exportPort.streamForExport(eq(TENANT), any())).thenReturn(Stream.of(dossier));

        ExportDossiersUseCase.ExportResult result =
                useCase.export(TENANT, new ExportDossiersQuery("FAC", null, null), admin);

        List<Dossier> content = result.dossiers().toList();
        assertThat(content).singleElement().satisfies(d -> assertThat(d.fields()).containsEntry("ssn", "123-45-6789"));
        assertThat(result.fieldDefinitions()).isEqualTo(FIELD_DEFINITIONS);
    }

    @Test
    void export_nonAdmin_masksSensitiveFields() {
        AccessContext.User manager = AccessContext.user("u1", Set.of("DOSSIER_MANAGER"));
        when(typeRepository.findByCode(TENANT, "FAC")).thenReturn(Optional.of(type()));
        Dossier dossier = dossier(Map.of("priority", "HIGH", "ssn", "123-45-6789"));
        when(exportPort.streamForExport(eq(TENANT), any())).thenReturn(Stream.of(dossier));

        ExportDossiersUseCase.ExportResult result =
                useCase.export(TENANT, new ExportDossiersQuery("FAC", null, null), manager);

        List<Dossier> content = result.dossiers().toList();
        assertThat(content).singleElement().satisfies(d -> {
            assertThat(d.fields()).containsEntry("priority", "HIGH");
            assertThat(d.fields()).doesNotContainKey("ssn");
        });
    }

    @Test
    void export_passesStatusAndReferencePrefixToPortFilter() {
        AccessContext.User admin = AccessContext.user("admin", Set.of("TENANT_ADMIN"));
        when(typeRepository.findByCode(TENANT, "FAC")).thenReturn(Optional.of(type()));
        when(exportPort.streamForExport(eq(TENANT), any())).thenReturn(Stream.empty());

        useCase.export(TENANT, new ExportDossiersQuery("FAC", DossierStatus.OUVERT, "DOS-"), admin);

        ArgumentCaptor<DossierSearchPort.Filter> filter = ArgumentCaptor.forClass(DossierSearchPort.Filter.class);
        verify(exportPort).streamForExport(eq(TENANT), filter.capture());
        assertThat(filter.getValue().status()).isEqualTo(DossierStatus.OUVERT);
        assertThat(filter.getValue().dossierTypeCodes()).containsExactly("FAC");
        assertThat(filter.getValue().referencePrefix()).isEqualTo("DOS-");
    }

    @Test
    void export_typeWithNoDossiers_returnsEmptyStreamWithoutError() {
        AccessContext.User admin = AccessContext.user("admin", Set.of("TENANT_ADMIN"));
        when(typeRepository.findByCode(TENANT, "FAC")).thenReturn(Optional.of(type()));
        when(exportPort.streamForExport(eq(TENANT), any())).thenReturn(Stream.empty());

        ExportDossiersUseCase.ExportResult result =
                useCase.export(TENANT, new ExportDossiersQuery("FAC", null, null), admin);

        assertThat(result.dossiers().toList()).isEmpty();
    }

    private static DossierType type() {
        return DossierType.inherit(
                UUID.randomUUID(), TENANT, "FAC", "Facture", "wf", true, false, FIELD_DEFINITIONS);
    }

    private static Dossier dossier(Map<String, Object> fields) {
        Instant now = Instant.parse("2026-08-21T10:00:00Z");
        return new Dossier(
                UUID.randomUUID(), TENANT, "DOS-1", "FAC", "Dossier", DossierStatus.OUVERT,
                fields, null, 0, "creator", now, "creator", now, null, null, null);
    }
}
