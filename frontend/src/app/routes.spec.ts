import { describe, expect, it } from 'vitest'
import { routes } from './routes'

describe('routes', () => {
  it('guards /admin/bpmn-editor to TENANT_ADMIN and WORKFLOW_ADMIN, same as /admin/workflow-types', () => {
    const editorRoute = routes.find((r) => r.path === '/admin/bpmn-editor')
    const workflowTypesRoute = routes.find((r) => r.path === '/admin/workflow-types')

    expect(editorRoute?.meta?.roles).toEqual(['TENANT_ADMIN', 'WORKFLOW_ADMIN'])
    expect(editorRoute?.meta?.roles).toEqual(workflowTypesRoute?.meta?.roles)
  })

  it('guards /admin/dmn-editor to TENANT_ADMIN and WORKFLOW_ADMIN, same as /admin/bpmn-editor', () => {
    const dmnEditorRoute = routes.find((r) => r.path === '/admin/dmn-editor')
    const bpmnEditorRoute = routes.find((r) => r.path === '/admin/bpmn-editor')

    expect(dmnEditorRoute?.meta?.roles).toEqual(['TENANT_ADMIN', 'WORKFLOW_ADMIN'])
    expect(dmnEditorRoute?.meta?.roles).toEqual(bpmnEditorRoute?.meta?.roles)
  })
})
