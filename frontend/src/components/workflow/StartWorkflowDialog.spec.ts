import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import StartWorkflowDialog from './StartWorkflowDialog.vue'

vi.mock('@/api/workflows', () => ({
  listAllWorkflowTypeConfigs: vi.fn().mockResolvedValue([
    {
      id: '1',
      tenantId: 't',
      workflowKey: 'other',
      label: 'Other',
      statusDefinitions: [],
      allowedDossierStatuses: [],
      onStartDossierStatus: null,
      observerUserIds: [],
    },
  ]),
}))

describe('StartWorkflowDialog', () => {
  it('emits default workflow key', async () => {
    const wrapper = mount(StartWorkflowDialog, {
      props: { open: true, defaultWorkflowKey: 'default-wf' },
    })
    await wrapper.find('button.primary').trigger('click')
    expect(wrapper.emitted('start')?.[0]).toEqual(['default-wf', {}])
  })

  describe('variables de démarrage (TODO §93)', () => {
    async function addRow(wrapper: ReturnType<typeof mount>, name: string, value: string) {
      await wrapper.find('[data-testid="start-variables-add"]').trigger('click')
      const rows = wrapper.findAll('[data-testid="start-variable-row"]')
      const row = rows[rows.length - 1]
      await row.find('[data-testid="start-variable-name"]').setValue(name)
      await row.find('[data-testid="start-variable-value"]').setValue(value)
    }

    it('la section est repliée par défaut et sans ligne', () => {
      const wrapper = mount(StartWorkflowDialog, { props: { open: true, defaultWorkflowKey: 'default-wf' } })
      const details = wrapper.find('[data-testid="start-variables"]')
      expect(details.exists()).toBe(true)
      expect((details.element as HTMLDetailsElement).open).toBe(false)
      expect(wrapper.findAll('[data-testid="start-variable-row"]')).toHaveLength(0)
    })

    it('émet les variables saisies avec la clé', async () => {
      const wrapper = mount(StartWorkflowDialog, { props: { open: true, defaultWorkflowKey: 'default-wf' } })
      await addRow(wrapper, 'priorite', 'haute')
      await addRow(wrapper, 'notificationTo', 'a@example.org')
      await wrapper.find('button.primary').trigger('click')
      expect(wrapper.emitted('start')?.[0]).toEqual(['default-wf', { priorite: 'haute', notificationTo: 'a@example.org' }])
    })

    it('une ligne supprimée n’est pas envoyée', async () => {
      const wrapper = mount(StartWorkflowDialog, { props: { open: true, defaultWorkflowKey: 'default-wf' } })
      await addRow(wrapper, 'priorite', 'haute')
      await wrapper.find('[data-testid="start-variable-remove"]').trigger('click')
      await wrapper.find('button.primary').trigger('click')
      expect(wrapper.emitted('start')?.[0]).toEqual(['default-wf', {}])
    })

    it('un nom réservé est signalé et rien n’est émis', async () => {
      const wrapper = mount(StartWorkflowDialog, { props: { open: true, defaultWorkflowKey: 'default-wf' } })
      await addRow(wrapper, 'dossierId', 'x')
      await wrapper.find('button.primary').trigger('click')
      expect(wrapper.emitted('start')).toBeUndefined()
      expect(wrapper.find('[data-testid="start-variable-error"]').text()).toContain('dossierId')
      expect((wrapper.find('[data-testid="start-variables"]').element as HTMLDetailsElement).open).toBe(true)
    })

    it('la réouverture du dialogue efface les lignes précédentes', async () => {
      const wrapper = mount(StartWorkflowDialog, { props: { open: true, defaultWorkflowKey: 'default-wf' } })
      await addRow(wrapper, 'priorite', 'haute')
      await wrapper.setProps({ open: false })
      await wrapper.setProps({ open: true })
      expect(wrapper.findAll('[data-testid="start-variable-row"]')).toHaveLength(0)
    })
  })
})
