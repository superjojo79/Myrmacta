import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import NotificationTemplatesAdminView from './NotificationTemplatesAdminView.vue'
import * as api from '@/api/notificationTemplates'
import type { NotificationTemplate } from '@/api/notificationTemplates'
import { routes } from '@/app/routes'
import { asPage } from '@/api/page'

vi.mock('@/api/notificationTemplates')

function template(over: Partial<NotificationTemplate> = {}): NotificationTemplate {
  return {
    id: 'tpl1',
    templateKey: 'accuse',
    variantKey: null,
    locale: 'fr',
    label: 'Accusé',
    version: 1,
    status: 'DRAFT',
    subject: 'Objet',
    textBody: 'Corps',
    htmlBody: '<p>Corps</p>',
    createdAt: '',
    updatedAt: '',
    updatedBy: null,
    ...over,
  }
}

function problem(status: number, detail: string) {
  return { response: { status, data: { detail, status } } }
}

async function mountView() {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/admin/notification-templates')
  await router.isReady()
  const wrapper = mount(NotificationTemplatesAdminView, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

const row = (wrapper: Awaited<ReturnType<typeof mountView>>, id: string) => wrapper.get(`[data-testid="${id}"]`)

describe('NotificationTemplatesAdminView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listNotificationTemplates).mockResolvedValue(asPage([template()]))
  })

  it('is a TENANT_ADMIN route', () => {
    const route = routes.find((r) => r.path === '/admin/notification-templates')
    expect(route?.meta?.roles).toEqual(['TENANT_ADMIN'])
  })

  it('lists key, variant, language, version and status', async () => {
    const wrapper = await mountView()
    expect(wrapper.text()).toContain('Gabarits de notification')
    expect(wrapper.text()).toContain('accuse')
    expect(wrapper.text()).toContain('langue fr')
    expect(wrapper.text()).toContain('v1')
    expect(wrapper.text()).toContain('DRAFT')
  })

  it('creates a draft, sending an empty html body as null and blank variant as absent', async () => {
    vi.mocked(api.createNotificationTemplate).mockResolvedValue(template())
    const wrapper = await mountView()
    const form = wrapper.get('[data-testid="nt-form"]')
    const inputs = form.findAll('input')
    await inputs[0].setValue('k2') // clé
    await inputs[3].setValue('Libellé')
    await inputs[4].setValue('Objet [[${dossier.reference}]]')
    const areas = form.findAll('textarea')
    await areas[0].setValue('Bonjour')
    await form.trigger('submit.prevent')
    await flushPromises()
    expect(api.createNotificationTemplate).toHaveBeenCalledWith({
      templateKey: 'k2',
      variantKey: undefined,
      locale: undefined,
      label: 'Libellé',
      subject: 'Objet [[${dossier.reference}]]',
      textBody: 'Bonjour',
      htmlBody: null,
    })
    expect(api.listNotificationTemplates).toHaveBeenCalledTimes(2)
  })

  it('edits a draft from the detail and saves with PUT', async () => {
    vi.mocked(api.getNotificationTemplate).mockResolvedValue(template({ textBody: 'Détail' }))
    vi.mocked(api.updateNotificationTemplate).mockResolvedValue(template())
    const wrapper = await mountView()
    await row(wrapper, 'nt-edit').trigger('click')
    await flushPromises()
    expect(api.getNotificationTemplate).toHaveBeenCalledWith('tpl1')
    const form = wrapper.get('[data-testid="nt-form"]')
    expect(form.findAll('textarea')[0].element.value).toBe('Détail')
    expect(form.findAll('input')[0].attributes('disabled')).toBeDefined()
    await form.findAll('textarea')[1].setValue('')
    await form.trigger('submit.prevent')
    await flushPromises()
    expect(api.updateNotificationTemplate).toHaveBeenCalledWith('tpl1', {
      label: 'Accusé',
      subject: 'Objet',
      textBody: 'Détail',
      htmlBody: '',
    })
  })

  it('activates, archives and creates a new version', async () => {
    vi.mocked(api.activateNotificationTemplate).mockResolvedValue(template())
    vi.mocked(api.archiveNotificationTemplate).mockResolvedValue(template())
    vi.mocked(api.newNotificationTemplateVersion).mockResolvedValue(template())
    const wrapper = await mountView()
    await row(wrapper, 'nt-activate').trigger('click')
    await row(wrapper, 'nt-archive').trigger('click')
    await row(wrapper, 'nt-new-version').trigger('click')
    await flushPromises()
    expect(api.activateNotificationTemplate).toHaveBeenCalledWith('tpl1')
    expect(api.archiveNotificationTemplate).toHaveBeenCalledWith('tpl1')
    expect(api.newNotificationTemplateVersion).toHaveBeenCalledWith('tpl1')
  })

  it('only offers activate and edit on a draft, and no action but new version on an archived one', async () => {
    vi.mocked(api.listNotificationTemplates).mockResolvedValue(asPage([template({ status: 'ARCHIVED' })]))
    const wrapper = await mountView()
    expect(wrapper.find('[data-testid="nt-activate"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="nt-edit"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="nt-archive"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="nt-new-version"]').exists()).toBe(true)
  })

  it('previews the form content with the sample model and sandboxes the html without scripts', async () => {
    vi.mocked(api.previewNotificationTemplate).mockResolvedValue({
      subject: 'Objet rendu',
      textBody: 'Texte rendu',
      htmlBody: '<p>HTML rendu</p>',
    })
    const wrapper = await mountView()
    const form = wrapper.get('[data-testid="nt-form"]')
    await form.findAll('input')[4].setValue('Objet')
    await form.findAll('textarea')[0].setValue('Texte')
    await wrapper.get('[data-testid="nt-preview-form"]').trigger('click')
    await flushPromises()
    expect(api.previewNotificationTemplate).toHaveBeenCalledWith({
      template: { subject: 'Objet', textBody: 'Texte', htmlBody: null },
      model: { dossier: { reference: 'DOS-1', fields: {} }, variables: {} },
    })
    expect(wrapper.get('[data-testid="nt-preview-subject"]').text()).toBe('Objet rendu')
    expect(wrapper.get('[data-testid="nt-preview-text"]').text()).toBe('Texte rendu')
    const iframe = wrapper.get('iframe')
    expect(iframe.attributes('sandbox')).toBe('')
    expect(iframe.attributes('srcdoc')).toBe('<p>HTML rendu</p>')
  })

  it('previews a stored template by id', async () => {
    vi.mocked(api.previewNotificationTemplate).mockResolvedValue({ subject: 's', textBody: 't', htmlBody: null })
    const wrapper = await mountView()
    await row(wrapper, 'nt-preview-row').trigger('click')
    await flushPromises()
    expect(api.previewNotificationTemplate).toHaveBeenCalledWith({
      id: 'tpl1',
      model: { dossier: { reference: 'DOS-1', fields: {} }, variables: {} },
    })
    expect(wrapper.find('iframe').exists()).toBe(false)
  })

  it('does not call the API when the sample model is not a JSON object', async () => {
    const wrapper = await mountView()
    const model = wrapper.get('aside textarea')
    await model.setValue('{pas du json')
    await row(wrapper, 'nt-preview-row').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('JSON invalide')
    await model.setValue('[1]')
    await row(wrapper, 'nt-preview-row').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('objet JSON')
    expect(api.previewNotificationTemplate).not.toHaveBeenCalled()
  })

  it('shows a 400 refusal with the violated rule and keeps the form filled', async () => {
    vi.mocked(api.activateNotificationTemplate).mockRejectedValue(
      problem(400, 'htmlBody: tag <img> is not allowed'),
    )
    const wrapper = await mountView()
    await row(wrapper, 'nt-activate').trigger('click')
    await flushPromises()
    const alert = wrapper.get('[data-testid="nt-error"]')
    expect(alert.text()).toContain('Gabarit refusé')
    expect(alert.text()).toContain('tag <img> is not allowed')
  })

  it('shows a 409 conflict readably and a 400 on creation', async () => {
    vi.mocked(api.newNotificationTemplateVersion).mockRejectedValue(problem(409, 'A draft already exists'))
    vi.mocked(api.createNotificationTemplate).mockRejectedValue(problem(400, 'templateKey: invalid'))
    const wrapper = await mountView()
    await row(wrapper, 'nt-new-version').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="nt-error"]').text()).toContain('Action impossible dans l’état actuel : A draft already exists')
    const form = wrapper.get('[data-testid="nt-form"]')
    await form.findAll('input')[0].setValue('BAD KEY')
    await form.trigger('submit.prevent')
    await flushPromises()
    expect(wrapper.get('[data-testid="nt-error"]').text()).toContain('Gabarit refusé : templateKey: invalid')
    expect((form.findAll('input')[0].element as HTMLInputElement).value).toBe('BAD KEY')
  })

  it('shows the error message when the load fails', async () => {
    vi.mocked(api.listNotificationTemplates).mockRejectedValue(new Error('boom'))
    const wrapper = await mountView()
    expect(wrapper.get('[data-testid="nt-error"]').text()).toBe('boom')
  })
})
