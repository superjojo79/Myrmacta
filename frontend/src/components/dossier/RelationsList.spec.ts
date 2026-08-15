import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import RelationsList from './RelationsList.vue'
import type { DossierRelation } from '@/api/types'

const relation: DossierRelation = {
  id: 'r1',
  type: 'PARENT_CHILD',
  direction: 'INCOMING',
  relatedDossier: {
    id: 'd2',
    reference: 'DOS-2',
    label: 'Parent Acme',
    dossierTypeCode: 'FAC',
    dossierTypeLabel: 'Facture',
    status: 'OUVERT',
    createdAt: '2026-08-15T00:00:00Z',
    updatedAt: '2026-08-15T00:00:00Z',
  },
  createdBy: 'u',
  createdAt: '2026-08-15T00:00:00Z',
}

describe('RelationsList', () => {
  it('shows inverted label and link to related dossier', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/dossiers/:id', component: { template: '<div />' } }],
    })
    const wrapper = mount(RelationsList, {
      props: { relations: [relation] },
      global: { plugins: [router] },
    })
    expect(wrapper.text()).toContain('Parent')
    expect(wrapper.text()).toContain('DOS-2')
    expect(wrapper.get('a').attributes('href')).toBe('/dossiers/d2')
  })

  it('shows empty state', () => {
    const wrapper = mount(RelationsList, { props: { relations: [] } })
    expect(wrapper.text()).toContain('Aucun dossier lié')
  })
})
