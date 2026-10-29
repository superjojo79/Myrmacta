package be.myrmacta.domain.workflow.model;

/**
 * TODO §111 (D-START-DECLARED) — variable de démarrage déclarée dans le BPMN déployé.
 * {@code name} est le nom de la variable de process, {@code label} le libellé montré à l'agent,
 * {@code required} indique qu'une valeur texte non vide est exigée au démarrage manuel.
 */
public record StartVariableDeclaration(String name, String label, boolean required) {}
