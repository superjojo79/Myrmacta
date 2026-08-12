import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import { listMyTasks, completeTask } from './tasks'
import { listDossierTypes, grantDossierTypeAcl } from './dossierTypes'
import { getWorkflowDiagram, listWorkflowTypeConfigs } from './workflows'
import { listConnectors, listDocumentTemplates } from './admin'
import { downloadElementContent, elementContentUrl } from './elements'
import { toProblemDetail, configureHttpClient, resolveApiBaseUrl } from './http'

vi.mock('axios')

describe('api modules', () => {
  beforeEach(() => {
    vi.mocked(axios.get).mockReset()
    vi.mocked(axios.post).mockReset()
  })

  it('tasks client calls mine and complete', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: [] })
    vi.mocked(axios.post).mockResolvedValue({})
    await listMyTasks()
    await completeTask('t1', {})
    expect(axios.get).toHaveBeenCalledWith('/api/workflow-tasks/mine')
    expect(axios.post).toHaveBeenCalledWith('/api/workflow-tasks/t1/complete', { variables: {} })
  })

  it('dossierTypes and workflows clients', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: [] })
    vi.mocked(axios.post).mockResolvedValue({})
    await listDossierTypes()
    await grantDossierTypeAcl('CLAIM', 'u1', 'VIEW')
    await getWorkflowDiagram('l1')
    await listWorkflowTypeConfigs()
    expect(axios.get).toHaveBeenCalledWith('/api/dossier-types')
    expect(axios.get).toHaveBeenCalledWith('/api/workflow-links/l1/diagram')
  })

  it('admin and elements clients', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: [] })
    await listConnectors()
    await listDocumentTemplates()
    vi.mocked(axios.get).mockResolvedValue({ data: new Blob() })
    await downloadElementContent('e1')
    expect(elementContentUrl('e1')).toContain('/api/dossier-elements/e1/content')
  })

  it('maps ProblemDetail and configures http', () => {
    configureHttpClient()
    expect(axios.defaults.withCredentials).toBe(true)
    expect(toProblemDetail({ response: { data: { title: 'x', status: 400 }, status: 400 } })).toEqual({
      title: 'x',
      status: 400,
    })
    expect(resolveApiBaseUrl('http://localhost/tenant_dev/')).toBe('/tenant_dev')
  })
})
