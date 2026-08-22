import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import axios from 'axios'
import BpmnUploadPreview from './BpmnUploadPreview.vue'

vi.mock('axios')

vi.mock('bpmn-js/lib/NavigatedViewer', () => ({
  default: class {
    importXML = vi.fn().mockResolvedValue(undefined)
    destroy = vi.fn()
    get = () => ({ zoom: vi.fn(), addMarker: vi.fn() })
  },
}))

const VALID_BPMN = '<?xml version="1.0"?><definitions><process id="testProcess"><startEvent id="s"/></process></definitions>'
const VALID_BPMN_SINGLE_QUOTES = "<?xml version='1.0'?><definitions><process id='testProcess'><startEvent id='s'/></process></definitions>"
const NON_BPMN_XML = '<?xml version="1.0"?><root><child/></root>'

async function selectFile(wrapper: ReturnType<typeof mount>, file: File) {
  const fileInput = wrapper.get('input[type="file"]').element as HTMLInputElement
  Object.defineProperty(fileInput, 'files', { value: [file], configurable: true })
  await wrapper.get('input[type="file"]').trigger('change')
  await flushPromises()
}

describe('BpmnUploadPreview', () => {
  beforeEach(() => {
    vi.mocked(axios.get).mockReset()
    vi.mocked(axios.post).mockReset()
  })

  it('reads the file locally and renders a preview without calling deploy before confirmation', async () => {
    vi.mocked(axios.get).mockResolvedValue({ status: 204, data: '' })
    const wrapper = mount(BpmnUploadPreview)
    const file = new File([VALID_BPMN], 'diagram.bpmn20.xml', { type: 'application/xml' })

    await selectFile(wrapper, file)

    expect(wrapper.text()).toContain('testProcess')
    expect(axios.post).not.toHaveBeenCalled()
  })

  it('fetches deployment info for the extracted key after selection', async () => {
    vi.mocked(axios.get).mockResolvedValue({
      status: 200,
      data: { processDefinitionKey: 'testProcess', latestVersion: 3, activeInstanceCount: 12 },
    })
    const wrapper = mount(BpmnUploadPreview)
    const file = new File([VALID_BPMN], 'diagram.bpmn20.xml', { type: 'application/xml' })

    await selectFile(wrapper, file)

    expect(axios.get).toHaveBeenCalledWith('/api/workflow-bpmn-deployments/info', {
      params: { processDefinitionKey: 'testProcess' },
    })
    expect(wrapper.text()).toContain('Version actuelle déployée : 3')
    expect(wrapper.text()).toContain('instances actives : 12')
  })

  it('extracts the process id when the XML uses single-quoted attributes', async () => {
    vi.mocked(axios.get).mockResolvedValue({ status: 204, data: '' })
    const wrapper = mount(BpmnUploadPreview)
    const file = new File([VALID_BPMN_SINGLE_QUOTES], 'diagram.bpmn20.xml', { type: 'application/xml' })

    await selectFile(wrapper, file)

    expect(wrapper.text()).toContain('testProcess')
    expect(axios.get).toHaveBeenCalledWith('/api/workflow-bpmn-deployments/info', {
      params: { processDefinitionKey: 'testProcess' },
    })
  })

  it('shows a client-side error for a non-BPMN file and never attempts deployment', async () => {
    const wrapper = mount(BpmnUploadPreview)
    const file = new File([NON_BPMN_XML], 'not-bpmn.xml', { type: 'application/xml' })

    await selectFile(wrapper, file)

    expect(wrapper.text()).toContain('non reconnu comme un diagramme BPMN valide')
    expect(wrapper.find('button').exists()).toBe(false)
    expect(axios.get).not.toHaveBeenCalled()
    expect(axios.post).not.toHaveBeenCalled()
  })

  it('deploys on confirmation and surfaces a server error message', async () => {
    vi.mocked(axios.get).mockResolvedValue({ status: 204, data: '' })
    vi.mocked(axios.post).mockRejectedValue({
      isAxiosError: true,
      response: { status: 404, data: { detail: 'No WorkflowTypeConfig found for key: testProcess' } },
    })
    const wrapper = mount(BpmnUploadPreview)
    const file = new File([VALID_BPMN], 'diagram.bpmn20.xml', { type: 'application/xml' })
    await selectFile(wrapper, file)

    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(axios.post).toHaveBeenCalledWith(
      '/api/workflow-bpmn-deployments',
      expect.any(FormData),
      { headers: { 'Content-Type': 'multipart/form-data' } },
    )
    expect(wrapper.text()).toContain('No WorkflowTypeConfig found for key: testProcess')
    expect(wrapper.emitted('deployed')).toBeUndefined()
  })
})
