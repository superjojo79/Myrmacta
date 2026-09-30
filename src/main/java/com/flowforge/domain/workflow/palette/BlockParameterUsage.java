package com.flowforge.domain.workflow.palette;

/** Référence à une ressource du tenant posée par une brique dans un BPMN, à vérifier au déploiement. */
public record BlockParameterUsage(String activityId, String blockId, String parameter, String valueSource, String value) {}
