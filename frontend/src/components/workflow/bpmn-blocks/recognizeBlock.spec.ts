import { describe, expect, it } from 'vitest'
import { recognizeBlock } from './recognizeBlock'
import { TEST_BLOCKS } from './testCatalog'

function bo(type: string, attrs: Record<string, unknown> = {}) {
  return { $type: type, get: (name: string) => attrs[name] }
}

describe('recognizeBlock (S-FE-1)', () => {
  it.each([
    ['generate-document', 'bpmn:ServiceTask', { 'flowable:delegateExpression': '${documentGenerationTask}' }],
    ['attach-document', 'bpmn:ServiceTask', { 'flowable:delegateExpression': '${attachGeneratedDocumentTask}' }],
    ['call-connector', 'bpmn:ServiceTask', { 'flowable:delegateExpression': '${connectorServiceTask}' }],
    ['notify', 'bpmn:ServiceTask', { 'flowable:delegateExpression': '${notificationDispatchTask}' }],
    ['set-dossier-status', 'bpmn:ServiceTask', { 'flowable:delegateExpression': '${updateDossierStatusTask}' }],
    ['set-workflow-status', 'bpmn:ServiceTask', { 'flowable:delegateExpression': '${updateWorkflowLinkStatusTask}' }],
    ['request-signature', 'bpmn:ServiceTask', { 'flowable:delegateExpression': '${signatureRequestTask}' }],
    ['dmn-decision', 'bpmn:ServiceTask', { 'flowable:type': 'dmn' }],
    ['user-question', 'bpmn:UserTask', { 'flowable:formKey': 'user-question:demande' }],
    [
      'external-task',
      'bpmn:UserTask',
      { 'flowable:formKey': 'user-question:demande', 'flowable:portalRecipient': 'variable:mail' },
    ],
  ])('reconnaît %s', (id, type, attrs) => {
    expect(recognizeBlock(bo(type, attrs), TEST_BLOCKS)?.id).toBe(id)
  })

  it('renvoie null pour une tâche brute ou inconnue', () => {
    expect(recognizeBlock(bo('bpmn:ServiceTask'), TEST_BLOCKS)).toBeNull()
    expect(recognizeBlock(bo('bpmn:ServiceTask', { 'flowable:delegateExpression': '${autre}' }), TEST_BLOCKS)).toBeNull()
    expect(recognizeBlock(bo('bpmn:UserTask', { 'flowable:formKey': 'autre:x' }), TEST_BLOCKS)).toBeNull()
    expect(recognizeBlock(bo('bpmn:UserTask'), TEST_BLOCKS)).toBeNull()
    expect(recognizeBlock(bo('bpmn:StartEvent'), TEST_BLOCKS)).toBeNull()
    expect(recognizeBlock(undefined, TEST_BLOCKS)).toBeNull()
  })

  it('une tâche externe fraîchement créée (portalRecipient vide) reste une tâche externe', () => {
    const el = bo('bpmn:UserTask', { 'flowable:formKey': 'user-question:', 'flowable:portalRecipient': '' })
    expect(recognizeBlock(el, TEST_BLOCKS)?.id).toBe('external-task')
  })

  it('le délégué l’emporte sur flowable:type', () => {
    const el = bo('bpmn:ServiceTask', {
      'flowable:delegateExpression': '${connectorServiceTask}',
      'flowable:type': 'dmn',
    })
    expect(recognizeBlock(el, TEST_BLOCKS)?.id).toBe('call-connector')
  })
})
