import type { BlockParameter, BuildingBlock } from './types'

// Catalogue minimal de test, miroir des marqueurs du catalogue serveur.
export function param(over: Partial<BlockParameter> & { name: string }): BlockParameter {
  return {
    label: over.name,
    type: 'TEXT',
    binding: 'ATTRIBUTE',
    required: false,
    valueSource: null,
    enumValues: [],
    maxLength: 200,
    min: null,
    max: null,
    help: null,
    ...over,
  }
}

function block(
  id: string,
  element: BuildingBlock['element'],
  marker: Partial<BuildingBlock['marker']>,
  parameters: BlockParameter[] = [],
): BuildingBlock {
  return {
    id,
    label: id,
    description: id,
    category: 'DOSSIER',
    element,
    marker: { delegate: null, serviceTaskType: null, formKeyPrefix: null, portalRecipient: false, ...marker },
    parameters,
  }
}

export const TEST_BLOCKS: BuildingBlock[] = [
  block('generate-document', 'SERVICE_TASK', { delegate: 'documentGenerationTask' }, [
    param({ name: 'documentType', type: 'REFERENCE', valueSource: 'documentTemplates', required: true }),
    param({ name: 'documentVariant' }),
  ]),
  block('attach-document', 'SERVICE_TASK', { delegate: 'attachGeneratedDocumentTask' }),
  block('call-connector', 'SERVICE_TASK', { delegate: 'connectorServiceTask' }, [
    param({ name: 'connectorInstanceId', type: 'REFERENCE', valueSource: 'connectorInstances', required: true }),
  ]),
  block('notify', 'SERVICE_TASK', { delegate: 'notificationDispatchTask' }, [
    param({ name: 'notificationChannels', type: 'ENUM_MULTI', enumValues: ['EMAIL', 'WEBHOOK'], required: true }),
    param({ name: 'notificationTo', maxLength: 254 }),
  ]),
  block('create-child-dossier', 'SERVICE_TASK', { delegate: 'createChildDossierTask' }, [
    param({ name: 'childDossierTypeCode', type: 'REFERENCE', valueSource: 'dossierTypes', required: true }),
    param({ name: 'workflowProcessKey', type: 'REFERENCE', valueSource: 'workflowTypes', required: true }),
  ]),
  block('set-dossier-status', 'SERVICE_TASK', { delegate: 'updateDossierStatusTask' }, [
    param({ name: 'targetDossierStatus', type: 'ENUM', enumValues: ['DRAFT', 'OUVERT', 'EN_COURS', 'SUSPENDU', 'CLOTURE', 'ARCHIVE'], required: true }),
  ]),
  block('set-workflow-status', 'SERVICE_TASK', { delegate: 'updateWorkflowLinkStatusTask' }, [
    param({ name: 'targetWorkflowStatus', type: 'REFERENCE', valueSource: 'workflowStatuses', required: true }),
  ]),
  block('dmn-decision', 'SERVICE_TASK', { serviceTaskType: 'dmn' }, [
    param({
      name: 'decisionTableReferenceKey',
      type: 'REFERENCE',
      binding: 'FIELD_ELEMENT',
      valueSource: 'dmnTables',
      required: true,
    }),
  ]),
  block('request-signature', 'SERVICE_TASK', { delegate: 'signatureRequestTask' }, [
    param({ name: 'signatureDocument', type: 'SOURCE_REF', required: true }),
    param({ name: 'signatureMode', type: 'ENUM', enumValues: ['PARALLEL', 'SEQUENTIAL'] }),
    param({ name: 'signatureDeadlineDays', type: 'INTEGER', min: 1, max: 365 }),
    param({ name: 'signers', type: 'SIGNERS', binding: 'SIGNER_ELEMENTS', required: true }),
  ]),
  block('user-question', 'USER_TASK', { formKeyPrefix: 'user-question:' }, [
    param({
      name: 'schemaKey',
      type: 'REFERENCE',
      binding: 'FORM_KEY_SUFFIX',
      valueSource: 'questionSchemas',
      required: true,
    }),
  ]),
  block('external-task', 'USER_TASK', { formKeyPrefix: 'user-question:', portalRecipient: true }, [
    param({
      name: 'schemaKey',
      type: 'REFERENCE',
      binding: 'FORM_KEY_SUFFIX',
      valueSource: 'questionSchemas',
      required: true,
    }),
    param({ name: 'portalRecipient', required: true }),
  ]),
]
