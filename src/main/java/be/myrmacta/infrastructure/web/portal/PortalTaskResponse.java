package be.myrmacta.infrastructure.web.portal;

import com.fasterxml.jackson.annotation.JsonInclude;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.workflow.portal.port.in.GetPortalTaskUseCase.PortalTaskView;
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
        List<Attachment> attachments,
        // Absent pour une tâche sans authentification forte : le contrat minimal de D-PORTAL-DATA ne change pas.
        @JsonInclude(JsonInclude.Include.NON_NULL) Identity identity,
        // TODO §105 (D-PIECE-PORTAL) — absent si la tâche ne propose aucun type de pièce (contrat minimal inchangé).
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<PieceTypeOption> pieceTypes) {

    static PortalTaskResponse from(PortalTaskView view) {
        return new PortalTaskResponse(
                view.dossierReference(),
                view.taskName(),
                view.instructions(),
                new Form(view.formLabel(), view.fields().stream().map(Field::from).toList()),
                view.expiresAt(),
                view.ownAttachments().stream().map(Attachment::from).toList(),
                view.identityRequired() ? new Identity(view.verifiedName() != null, view.verifiedName(),
                        view.identityProviders().stream().map(p -> new Provider(p.id(), p.label())).toList()) : null,
                view.pieceTypes().stream().map(type -> new PieceTypeOption(type.key(), type.label())).toList());
    }

    /** Type de pièce proposé au tiers : clé et libellé seulement (jamais l'indicateur {@code sensitive}). */
    record PieceTypeOption(String key, String label) {}

    /**
     * TODO §104 (T3b) — présent seulement pour une tâche à authentification forte : {@code verified} faux, la page
     * montre le choix du fournisseur et rien d'autre ; vrai, le nom de la personne authentifiée.
     */
    record Identity(boolean verified, String name, List<Provider> providers) {}

    record Provider(String id, String label) {}

    record Form(String label, List<Field> fields) {}

    record Field(String name, String label, String type, boolean required, List<String> options, String group,
                 String helpText, String width) {
        static Field from(FieldDefinition definition) {
            return new Field(definition.name(), definition.label(), definition.type().name(), definition.required(),
                    definition.options(), definition.group(), definition.helpText(), definition.width());
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
