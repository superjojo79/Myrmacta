package com.flowforge.domain.workflow.palette;

/**
 * TODO §92 (D-DRAFT-REF-STATUS) — ressource référencée par un brouillon qui existe mais n'est pas encore active : la
 * publication du brouillon sera refusée tant qu'elle ne l'est pas.
 */
public record InactiveBlockReference(String activityId, String parameter, String source, String value) {}
