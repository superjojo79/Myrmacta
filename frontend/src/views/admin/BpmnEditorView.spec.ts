import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import * as workflowsApi from '@/api/workflows'
import { routes } from '@/app/routes'
import BpmnEditorView from './BpmnEditorView.vue'

vi.mock('@/api/workflows')

const importXML = vi.fn().mockResolvedValue(undefined)
const saveXML = vi.fn().mockResolvedValue({ xml: '<definitions><process id="editedProcess"/></definitions>' })
const zoom = vi.fn()
const destroy = vi.fn()

vi.mock('bpmn-js/lib/Modeler', () => ({
  default: class {
    importXML = importXML
    saveXML = saveXML
    destroy = destroy
    get = (name: string) => {
      if (name === 'canvas') return { zoom }
      if (name === 'eventBus') return { on: vi.fn() }
      return {}
    }
  },
}))

vi.mock('bpmn-js-properties-panel', () => ({
  BpmnPropertiesPanelModule: {},
  BpmnPropertiesProviderModule: {},
  useService: vi.fn(),
}))

vi.mock('@/components/workflow/bpmn-properties/flowablePropertiesProvider', () => ({
  FlowablePropertiesProviderModule: {},
}))

async function mountView(path: string) {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(path)
  await router.isReady()
  return mount(BpmnEditorView, { global: { plugins: [router] } })
}

describe('BpmnEditorView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    importXML.mockResolvedValue(undefined)
    saveXML.mockResolvedValue({ xml: '<definitions><process id="editedProcess"/></definitions>' })
  })

  it('shows a blank canvas when no processDefinitionKey query param is given', async () => {
    await mountView('/admin/bpmn-editor')
    await flushPromises()

    expect(workflowsApi.getBpmnDraft).not.toHaveBeenCalled()
    expect(workflowsApi.getDeployedBpmnXml).not.toHaveBeenCalled()
    expect(importXML).toHaveBeenCalledWith(expect.stringContaining('Process_1'))
  })

  it('loads the existing draft when processDefinitionKey is given and a draft exists', async () => {
    vi.mocked(workflowsApi.getBpmnDraft).mockResolvedValue({
      processDefinitionKey: 'wf',
      xml: '<definitions><process id="wf"/></definitions>',
      updatedAt: '2026-01-01T00:00:00Z',
      updatedBy: 'admin',
    })

    await mountView('/admin/bpmn-editor?processDefinitionKey=wf')
    await flushPromises()

    expect(workflowsApi.getBpmnDraft).toHaveBeenCalledWith('wf')
    expect(importXML).toHaveBeenCalledWith('<definitions><process id="wf"/></definitions>')
  })

  it('falls back to the deployed diagram when no draft exists for the key', async () => {
    vi.mocked(workflowsApi.getBpmnDraft).mockResolvedValue(null)
    vi.mocked(workflowsApi.getDeployedBpmnXml).mockResolvedValue('<definitions><process id="wf"/></definitions>')

    await mountView('/admin/bpmn-editor?processDefinitionKey=wf')
    await flushPromises()

    expect(workflowsApi.getDeployedBpmnXml).toHaveBeenCalledWith('wf')
    expect(importXML).toHaveBeenCalledWith('<definitions><process id="wf"/></definitions>')
  })

  it('shows an error message and still mounts a blank canvas when loading the draft fails', async () => {
    vi.mocked(workflowsApi.getBpmnDraft).mockRejectedValue(new Error('network down'))

    const wrapper = await mountView('/admin/bpmn-editor?processDefinitionKey=wf')
    await flushPromises()

    expect(wrapper.get('.bpmn-editor__error').text()).toContain('network down')
    expect(importXML).toHaveBeenCalledWith(expect.stringContaining('Process_1'))
  })

  it('loads a locally selected file into the canvas', async () => {
    const wrapper = await mountView('/admin/bpmn-editor')
    await flushPromises()
    importXML.mockClear()

    const file = new File(
      ['<definitions><process id="importedProcess"/></definitions>'],
      'diagram.bpmn20.xml',
      { type: 'application/xml' },
    )
    const fileInput = wrapper.get('input[type="file"]').element as HTMLInputElement
    Object.defineProperty(fileInput, 'files', { value: [file], configurable: true })
    await wrapper.get('input[type="file"]').trigger('change')
    await flushPromises()

    expect(importXML).toHaveBeenCalledWith('<definitions><process id="importedProcess"/></definitions>')
  })

  it('the "Enregistrer le brouillon" button calls useBpmnDraft.save with the canvas XML', async () => {
    vi.mocked(workflowsApi.saveBpmnDraft).mockResolvedValue({
      processDefinitionKey: 'editedProcess',
      xml: '<definitions><process id="editedProcess"/></definitions>',
      updatedAt: '2026-01-01T00:00:00Z',
      updatedBy: 'admin',
    })

    const wrapper = await mountView('/admin/bpmn-editor')
    await flushPromises()

    await wrapper.get('button.bpmn-editor__save').trigger('click')
    await flushPromises()

    expect(workflowsApi.saveBpmnDraft).toHaveBeenCalledWith(
      'editedProcess',
      '<definitions><process id="editedProcess"/></definitions>',
    )
  })

  it('the "Déployer" button reuses useBpmnDeployment (info then confirm)', async () => {
    vi.mocked(workflowsApi.getBpmnDeploymentInfo).mockResolvedValue({
      processDefinitionKey: 'editedProcess',
      latestVersion: 1,
      activeInstanceCount: 0,
    })
    vi.mocked(workflowsApi.deployBpmn).mockResolvedValue({
      processDefinitionKey: 'editedProcess',
      deployedVersion: 2,
      deploymentId: 'dep-2',
    })

    const wrapper = await mountView('/admin/bpmn-editor')
    await flushPromises()

    await wrapper.get('button.bpmn-editor__deploy').trigger('click')
    await flushPromises()

    expect(workflowsApi.getBpmnDeploymentInfo).toHaveBeenCalledWith('editedProcess')

    await wrapper.get('button.bpmn-editor__confirm-deploy').trigger('click')
    await flushPromises()

    expect(workflowsApi.deployBpmn).toHaveBeenCalled()
    const deployedFile = vi.mocked(workflowsApi.deployBpmn).mock.calls[0][0]
    expect(deployedFile).toBeInstanceOf(File)
  })
})
