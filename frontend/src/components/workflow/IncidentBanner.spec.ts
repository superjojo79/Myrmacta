import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import IncidentBanner from './IncidentBanner.vue'
import type { WorkflowIncident } from '@/api/types'

const incident: WorkflowIncident = {
  jobId: 'job-1',
  activityId: 'mailRefus',
  activityName: 'E-mail : refus',
  state: 'INCIDENT',
  since: '2026-10-18T18:51:26Z',
  retriesLeft: 0,
  message: 'notificationTo source dossierField:email is not a single e-mail address',
}
const retrying: WorkflowIncident = {
  jobId: 'job-2',
  activityId: 'svc',
  activityName: 'Appel ERP',
  state: 'RETRYING',
  since: '2026-10-18T18:50:00Z',
  retriesLeft: 2,
}

describe('IncidentBanner (TODO §94)', () => {
  it('ne rend rien sans échec', () => {
    const wrapper = mount(IncidentBanner, { props: { incidents: [], canRetry: true } })
    expect(wrapper.find('[data-testid="incident-banner"]').exists()).toBe(false)
  })

  it('affiche une ligne par échec avec son état, son étape et le message quand il est fourni', () => {
    const wrapper = mount(IncidentBanner, {
      props: { incidents: [incident, retrying], canRetry: false },
    })
    const rows = wrapper.findAll('[data-testid="incident-row"]')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('E-mail : refus')
    expect(rows[0].text()).toContain('Incident')
    expect(rows[0].find('[data-testid="incident-message"]').text()).toContain(
      'single e-mail address',
    )
    expect(rows[1].text()).toContain('Appel ERP')
    expect(rows[1].text()).toContain('2')
    expect(rows[1].find('[data-testid="incident-message"]').exists()).toBe(false)
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
  })

  it('propose « Rejouer » seulement sur un incident et pour qui peut rejouer', async () => {
    const admin = mount(IncidentBanner, {
      props: { incidents: [incident, retrying], canRetry: true },
    })
    const buttons = admin.findAll('[data-testid="incident-retry"]')
    expect(buttons).toHaveLength(1)
    await buttons[0].trigger('click')
    expect(admin.emitted('retry')?.[0]).toEqual(['job-1'])

    const reader = mount(IncidentBanner, { props: { incidents: [incident], canRetry: false } })
    expect(reader.find('[data-testid="incident-retry"]').exists()).toBe(false)
  })

  it('désactive le bouton pendant un rejeu en cours', () => {
    const wrapper = mount(IncidentBanner, {
      props: { incidents: [incident], canRetry: true, retryingJobId: 'job-1' },
    })
    expect(wrapper.find('[data-testid="incident-retry"]').attributes('disabled')).toBeDefined()
  })
})
