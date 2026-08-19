import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import {
  activateDocumentTemplate,
  archiveDocumentTemplate,
  createDocumentTemplate,
  listConnectorDefinitions,
  listDocumentTemplates,
  newDocumentTemplateVersion,
  updateDocumentTemplate,
} from './admin'
import { toProblemDetail, resolveApiBaseUrl } from './http'
import { asPage } from './page'

vi.mock('axios')

describe('phase 8 api gaps', () => {
  beforeEach(() => {
    vi.mocked(axios.get).mockReset().mockResolvedValue({ data: asPage([]) })
    vi.mocked(axios.post).mockReset().mockResolvedValue({ data: { id: '1' } })
    vi.mocked(axios.patch).mockReset().mockResolvedValue({ data: { id: '1' } })
  })

  it('covers template lifecycle endpoints', async () => {
    await listDocumentTemplates('k', true)
    await createDocumentTemplate({ templateKey: 'k', label: 'L', htmlContent: '<p/>' })
    await updateDocumentTemplate('1', { label: 'L2' })
    await activateDocumentTemplate('1')
    await archiveDocumentTemplate('1')
    await newDocumentTemplateVersion('1')
    expect(axios.post).toHaveBeenCalledWith('/api/document-templates/1/activate')
    expect(axios.post).toHaveBeenCalledWith('/api/document-templates/1/archive')
    expect(axios.post).toHaveBeenCalledWith('/api/document-templates/1/new-version')
  })

  it('lists connector definitions', async () => {
    await listConnectorDefinitions()
    expect(axios.get).toHaveBeenCalledWith('/api/connector-definitions')
  })

  it('maps unknown errors to ProblemDetail fallback', () => {
    expect(toProblemDetail(new Error('boom')).detail).toBe('boom')
    expect(resolveApiBaseUrl('http://localhost/')).toBe('')
    expect(resolveApiBaseUrl('http://localhost/tenant_a/app/')).toBe('/tenant_a')
  })
})
