package be.myrmacta.domain.reporting.model;

import java.util.UUID;

public record ActiveTaskRow(UUID dossierId, String assigneeId, String typeCode, DossierSlaRow dossier) {}
