package com.flowforge.infrastructure.web.portal;

import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.workflow.portal.port.in.GetPortalTaskUseCase.PortalTaskView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * TODO §68 D-PORTAL-DATA — contrat public, volontairement minimal. Les champs du formulaire ne
 * reprennent <strong>pas</strong> {@code defaultValue} (valeur pré-remplie potentiellement
 * sensible, lecture restreinte par {@code ArchitectureRulesTest}) ni le drapeau {@code sensitive}.
 */
record PortalTaskResponse(
        String dossierReference,
        String taskName,
        String instructions,
        Form form,
        Instant expiresAt,
        List<Attachment> attachments) {

    static PortalTaskResponse from(PortalTaskView view) {
        return new PortalTaskResponse(
                view.dossierReference(),
                view.taskName(),
                view.instructions(),
                new Form(view.formLabel(), view.fields().stream().map(Field::from).toList()),
                view.expiresAt(),
                view.ownAttachments().stream().map(Attachment::from).toList());
    }

    record Form(String label, List<Field> fields) {}

    record Field(String name, String label, String type, boolean required, List<String> options, String group) {
        static Field from(FieldDefinition definition) {
            return new Field(definition.name(), definition.label(), definition.type().name(), definition.required(),
                    definition.options(), definition.group());
        }
    }

    record Attachment(UUID id, String label, String filename, Object sizeBytes, Instant addedAt) {
        static Attachment from(DossierElement element) {
            Object filename = element.metadata().get("filename");
            return new Attachment(element.id(), element.label(), filename == null ? "" : filename.toString(),
                    element.metadata().get("sizeBytes"), element.addedAt());
        }
    }
}
