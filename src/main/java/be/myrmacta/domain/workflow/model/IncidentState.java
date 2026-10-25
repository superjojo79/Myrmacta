package be.myrmacta.domain.workflow.model;

/**
 * TODO §94 — état d'un job Flowable en échec : {@code RETRYING} tant que le moteur a encore des tentatives automatiques,
 * {@code INCIDENT} quand le job est en <i>dead letter</i> (plus aucune tentative ; seul un rejeu le relance).
 */
public enum IncidentState {
    RETRYING,
    INCIDENT
}
