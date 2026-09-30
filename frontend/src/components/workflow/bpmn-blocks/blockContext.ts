import type { DiagnosticContext } from './blockDiagnostics'
import { getOpenProcessKey } from './blockCatalogHolder'
import { peekValueSource } from './valueSourceResolver'

interface Registry {
  filter(fn: (element: { businessObject?: { $type: string; eventDefinitions?: unknown[] } }) => boolean): unknown[]
  getAll(): Array<{ businessObject?: unknown }>
}

interface MessageEventDefinition {
  $type: string
  messageRef?: { name?: string }
}

/** Y a-t-il, dans le diagramme, une attente du message `signatureCompleted` (compagnon de la brique signature) ? */
export function hasSignatureCompletedCatch(registry: Pick<Registry, 'getAll'>): boolean {
  return registry.getAll().some((element) => {
    const bo = element.businessObject as { $type?: string; eventDefinitions?: MessageEventDefinition[] } | undefined
    if (bo?.$type !== 'bpmn:IntermediateCatchEvent' && bo?.$type !== 'bpmn:BoundaryEvent') return false
    return (bo.eventDefinitions ?? []).some(
      (def) => def.$type === 'bpmn:MessageEventDefinition' && def.messageRef?.name === 'signatureCompleted',
    )
  })
}

export function diagnosticContext(registry: Pick<Registry, 'getAll'>): DiagnosticContext {
  const processKey = getOpenProcessKey()
  return {
    options: (source) => peekValueSource(source, { processKey }),
    hasSignatureCompletedCatch: hasSignatureCompletedCatch(registry),
    processKey,
  }
}
