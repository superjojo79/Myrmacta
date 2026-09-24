import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import * as workflowsApi from '@/api/workflows'
import { routes } from '@/app/routes'
import DmnEditorView from './DmnEditorView.vue'

vi.mock('@/api/workflows')

const importXML = vi.fn().mockResolvedValue({ warnings: [] })
const saveXML = vi.fn().mockResolvedValue({ xml: '<definitions><decision id="editedDecision"/></definitions>' })
const destroy = vi.fn()

vi.mock('dmn-js/lib/Modeler', () => ({
  default: class {
    importXML = importXML
    saveXML = saveXML
    destroy = destroy
    getActiveViewer = () => ({ on: vi.fn() })
    on = vi.fn()
  },
}))

async function mountView(path: string) {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(path)
  await router.isReady()
  return mount(DmnEditorView, { global: { plugins: [router] } })
}

describe('DmnEditorView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    importXML.mockResolvedValue({ warnings: [] })
    saveXML.mockResolvedValue({ xml: '<definitions><decision id="editedDecision"/></definitions>' })
  })

  it('shows a blank canvas when no decisionKey query param is given', async () => {
    await mountView('/admin/dmn-editor')
    await flushPromises()

    expect(workflowsApi.getDmnDraft).not.toHaveBeenCalled()
    expect(workflowsApi.getDeployedDmnXml).not.toHaveBeenCalled()
    expect(importXML).toHaveBeenCalledWith(expect.stringContaining('Decision_1'))
  })

  it('loads the existing draft when decisionKey is given and a draft exists', async () => {
    vi.mocked(workflowsApi.getDmnDraft).mockResolvedValue({
      decisionKey: 'grilleEligibilite',
      xml: '<definitions><decision id="grilleEligibilite"/></definitions>',
      updatedAt: '2026-09-11T00:00:00Z',
      updatedBy: 'admin',
    })

    await mountView('/admin/dmn-editor?decisionKey=grilleEligibilite')
    await flushPromises()

    expect(workflowsApi.getDmnDraft).toHaveBeenCalledWith('grilleEligibilite')
    expect(importXML).toHaveBeenCalledWith('<definitions><decision id="grilleEligibilite"/></definitions>')
  })

  it('falls back to the deployed table when no draft exists for the key', async () => {
    vi.mocked(workflowsApi.getDmnDraft).mockResolvedValue(null)
    vi.mocked(workflowsApi.getDeployedDmnXml).mockResolvedValue(
      '<definitions><decision id="grilleEligibilite"/></definitions>',
    )

    await mountView('/admin/dmn-editor?decisionKey=grilleEligibilite')
    await flushPromises()

    expect(workflowsApi.getDeployedDmnXml).toHaveBeenCalledWith('grilleEligibilite')
    expect(importXML).toHaveBeenCalledWith('<definitions><decision id="grilleEligibilite"/></definitions>')
  })

  it('shows an error message and still mounts a blank canvas when loading the draft fails', async () => {
    vi.mocked(workflowsApi.getDmnDraft).mockRejectedValue(new Error('network down'))

    const wrapper = await mountView('/admin/dmn-editor?decisionKey=grilleEligibilite')
    await flushPromises()

    expect(wrapper.get('.dmn-editor__error').text()).toContain('network down')
    expect(importXML).toHaveBeenCalledWith(expect.stringContaining('Decision_1'))
  })

  it('loads a locally selected file into the canvas', async () => {
    const wrapper = await mountView('/admin/dmn-editor')
    await flushPromises()
    importXML.mockClear()

    const file = new File(
      ['<definitions><decision id="importedDecision"/></definitions>'],
      'table.dmn',
      { type: 'application/xml' },
    )
    const fileInput = wrapper.get('input[type="file"]').element as HTMLInputElement
    Object.defineProperty(fileInput, 'files', { value: [file], configurable: true })
    await wrapper.get('input[type="file"]').trigger('change')
    await flushPromises()

    expect(importXML).toHaveBeenCalledWith('<definitions><decision id="importedDecision"/></definitions>')
  })

  it('the "Charger" button loads an existing table by key typed into the field (Phase 7 gap fix)', async () => {
    vi.mocked(workflowsApi.getDmnDraft).mockResolvedValue(null)
    vi.mocked(workflowsApi.getDeployedDmnXml).mockResolvedValue(
      '<definitions><decision id="manuallyLoaded"/></definitions>',
    )

    const wrapper = await mountView('/admin/dmn-editor')
    await flushPromises()
    importXML.mockClear()

    await wrapper.get('input[type="text"]').setValue('manuallyLoaded')
    await wrapper.get('button.dmn-editor__load').trigger('click')
    await flushPromises()

    expect(workflowsApi.getDmnDraft).toHaveBeenCalledWith('manuallyLoaded')
    expect(importXML).toHaveBeenCalledWith('<definitions><decision id="manuallyLoaded"/></definitions>')
  })

  it('the "Enregistrer le brouillon" button calls useDmnDraft.save with the canvas XML', async () => {
    vi.mocked(workflowsApi.saveDmnDraft).mockResolvedValue({
      decisionKey: 'editedDecision',
      xml: '<definitions><decision id="editedDecision"/></definitions>',
      updatedAt: '2026-09-11T00:00:00Z',
      updatedBy: 'admin',
    })

    const wrapper = await mountView('/admin/dmn-editor')
    await flushPromises()

    await wrapper.get('button.dmn-editor__save').trigger('click')
    await flushPromises()

    expect(workflowsApi.saveDmnDraft).toHaveBeenCalledWith(
      'editedDecision',
      '<definitions><decision id="editedDecision"/></definitions>',
    )
  })

  it('the "Déployer" button reuses useDmnDeployment (info then confirm)', async () => {
    vi.mocked(workflowsApi.getDecisionTableDeploymentInfo).mockResolvedValue({
      decisionKey: 'editedDecision',
      latestVersion: 1,
    })
    vi.mocked(workflowsApi.deployDecisionTable).mockResolvedValue({
      decisionKey: 'editedDecision',
      deployedVersion: 2,
      deploymentId: 'dep-2',
    })

    const wrapper = await mountView('/admin/dmn-editor')
    await flushPromises()

    await wrapper.get('button.dmn-editor__deploy').trigger('click')
    await flushPromises()

    expect(workflowsApi.getDecisionTableDeploymentInfo).toHaveBeenCalledWith('editedDecision')

    await wrapper.get('button.dmn-editor__confirm-deploy').trigger('click')
    await flushPromises()

    expect(workflowsApi.deployDecisionTable).toHaveBeenCalled()
    const deployedFile = vi.mocked(workflowsApi.deployDecisionTable).mock.calls[0][0]
    expect(deployedFile).toBeInstanceOf(File)
  })

  it('does not call evaluation endpoints until a tooling panel is opened', async () => {
    await mountView('/admin/dmn-editor')
    await flushPromises()

    expect(workflowsApi.listDecisionTableVersions).not.toHaveBeenCalled()
    expect(workflowsApi.evaluateDecision).not.toHaveBeenCalled()
    expect(workflowsApi.testDecisionJson).not.toHaveBeenCalled()
    expect(workflowsApi.redeployDecisionTable).not.toHaveBeenCalled()
  })

  it('evaluates the JSON inputs of the selected version', async () => {
    vi.mocked(workflowsApi.listDecisionTableVersions).mockResolvedValue([
      { decisionKey: 'editedDecision', version: 1, deploymentId: 'dep-1', deployedAt: '2026-09-01T00:00:00Z' },
      { decisionKey: 'editedDecision', version: 2, deploymentId: 'dep-2', deployedAt: '2026-09-02T00:00:00Z' },
    ])
    vi.mocked(workflowsApi.evaluateDecision).mockResolvedValue({
      decisionKey: 'editedDecision',
      version: 1,
      rows: [{ result: 'ok' }],
    })

    const wrapper = await mountView('/admin/dmn-editor')
    await flushPromises()
    await wrapper.get('button.dmn-editor__tester').trigger('click')
    await flushPromises()

    expect(workflowsApi.listDecisionTableVersions).toHaveBeenCalledWith('editedDecision')
    await wrapper.get('textarea.dmn-editor__inputs').setValue('{"customerCat":"BRONZE"}')
    await wrapper.get('select.dmn-editor__version').setValue('1')
    await wrapper.get('button.dmn-editor__evaluate').trigger('click')
    await flushPromises()

    expect(workflowsApi.evaluateDecision).toHaveBeenCalledWith(
      'editedDecision',
      { customerCat: 'BRONZE' },
      1,
    )
    expect(wrapper.get('.dmn-editor__rows').text()).toContain('ok')
  })

  it('rejects a JSON input that is not an object and shows an evaluation error', async () => {
    vi.mocked(workflowsApi.listDecisionTableVersions).mockResolvedValue([])
    vi.mocked(workflowsApi.evaluateDecision).mockRejectedValue({
      response: { data: { title: 'Bad request', detail: 'type incompatible', status: 400 } },
    })

    const wrapper = await mountView('/admin/dmn-editor')
    await flushPromises()
    await wrapper.get('button.dmn-editor__tester').trigger('click')
    await flushPromises()

    await wrapper.get('textarea.dmn-editor__inputs').setValue('[1]')
    await wrapper.get('button.dmn-editor__evaluate').trigger('click')
    await flushPromises()
    expect(workflowsApi.evaluateDecision).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Les entrées doivent être un objet JSON.')

    await wrapper.get('textarea.dmn-editor__inputs').setValue('{"customerCat":"BRONZE"}')
    await wrapper.get('button.dmn-editor__evaluate').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('type incompatible')
  })

  it('uploads a JSON suite and a CSV suite', async () => {
    vi.mocked(workflowsApi.listDecisionTableVersions).mockResolvedValue([])
    vi.mocked(workflowsApi.testDecisionJson).mockResolvedValue({
      decisionKey: 'editedDecision',
      version: 1,
      results: [{ index: 0, passed: true, actualRows: [], failureMessage: null }],
    })
    vi.mocked(workflowsApi.testDecisionCsv).mockResolvedValue({
      decisionKey: 'editedDecision',
      version: 1,
      results: [{ index: 0, passed: false, actualRows: [], failureMessage: 'mismatch' }],
    })

    const wrapper = await mountView('/admin/dmn-editor')
    await flushPromises()
    await wrapper.get('button.dmn-editor__suite').trigger('click')
    await flushPromises()

    const jsonFile = new File(['{"cases":[]}'], 'suite.json', { type: 'application/json' })
    const fileInput = wrapper.get('input.dmn-editor__suite-file').element as HTMLInputElement
    Object.defineProperty(fileInput, 'files', { value: [jsonFile], configurable: true })
    await wrapper.get('input.dmn-editor__suite-file').trigger('change')
    await flushPromises()
    expect(workflowsApi.testDecisionJson).toHaveBeenCalled()
    expect(wrapper.text()).toContain('réussi')

    const csvFile = new File(['input.a,expected.b\n1,2\n'], 'suite.csv', { type: 'text/csv' })
    Object.defineProperty(fileInput, 'files', { value: [csvFile], configurable: true })
    await wrapper.get('input.dmn-editor__suite-file').trigger('change')
    await flushPromises()
    expect(workflowsApi.testDecisionCsv).toHaveBeenCalled()
    expect(wrapper.text()).toContain('échec')
  })

  it('asks for confirmation before redeploying and cancel does not call the API', async () => {
    vi.mocked(workflowsApi.listDecisionTableVersions).mockResolvedValue([
      { decisionKey: 'editedDecision', version: 1, deploymentId: 'dep-1', deployedAt: '2026-09-01T00:00:00Z' },
    ])
    vi.mocked(workflowsApi.redeployDecisionTable).mockResolvedValue({
      decisionKey: 'editedDecision',
      deployedVersion: 2,
      deploymentId: 'dep-2',
    })

    const wrapper = await mountView('/admin/dmn-editor')
    await flushPromises()
    await wrapper.get('button.dmn-editor__history').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Version 1')
    await wrapper.get('.dmn-editor__history-panel button').trigger('click')
    expect(wrapper.text()).toContain('Redéployer la version 1')
    await wrapper.get('button.dmn-editor__cancel-redeploy').trigger('click')
    expect(workflowsApi.redeployDecisionTable).not.toHaveBeenCalled()

    await wrapper.get('.dmn-editor__history-panel button').trigger('click')
    await wrapper.get('button.dmn-editor__confirm-redeploy').trigger('click')
    await flushPromises()
    expect(workflowsApi.redeployDecisionTable).toHaveBeenCalledWith('editedDecision', 1)
    expect(wrapper.text()).toContain('Redéployé : version 2')
  })
})
