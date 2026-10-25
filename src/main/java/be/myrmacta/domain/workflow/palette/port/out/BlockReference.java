package be.myrmacta.domain.workflow.palette.port.out;

/** Valeur proposée dans une liste de la palette : uniquement la valeur écrite dans le BPMN et son libellé. */
public record BlockReference(String value, String label) {}
