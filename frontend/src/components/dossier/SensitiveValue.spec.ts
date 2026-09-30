import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import SensitiveValue from './SensitiveValue.vue'
import { useAuthStore } from '@/stores/auth'

describe('SensitiveValue', () => {
  it('masks sensitive values for non tenant admins without reveal control', () => {
    setActivePinia(createPinia())
    const auth = useAuthStore()
    auth.user = { id: 'u1', name: 'Ada', email: null, roles: ['DOSSIER_MANAGER'] }
    const wrapper = mount(SensitiveValue, {
      props: { value: 'secret', sensitive: true },
    })
    expect(wrapper.text()).toBe('••••')
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it('shows clear value for tenant admin', () => {
    setActivePinia(createPinia())
    const auth = useAuthStore()
    auth.user = { id: 'u1', name: 'Admin', email: null, roles: ['TENANT_ADMIN'] }
    const wrapper = mount(SensitiveValue, {
      props: { value: 'secret', sensitive: true },
    })
    expect(wrapper.text()).toContain('secret')
  })

  it('shows non-sensitive values as-is', () => {
    setActivePinia(createPinia())
    const wrapper = mount(SensitiveValue, {
      props: { value: 'plain', sensitive: false },
    })
    expect(wrapper.text()).toContain('plain')
  })
})

describe('SensitiveValue — slot (TODO §76, D1)', () => {
  it('renders the slot only when the value is revealed', () => {
    setActivePinia(createPinia())
    const auth = useAuthStore()
    auth.user = { id: 'u1', name: 'Ada', email: null, roles: ['DOSSIER_MANAGER'] }
    const hidden = mount(SensitiveValue, {
      props: { value: '2 lignes', sensitive: true },
      slots: { default: '<table><tr><td>CONFIDENTIEL</td></tr></table>' },
    })
    expect(hidden.text()).toBe('••••')
    expect(hidden.html()).not.toContain('CONFIDENTIEL')

    auth.user = { id: 'u2', name: 'Admin', email: null, roles: ['TENANT_ADMIN'] }
    const revealed = mount(SensitiveValue, {
      props: { value: '2 lignes', sensitive: true },
      slots: { default: '<table><tr><td>CONFIDENTIEL</td></tr></table>' },
    })
    expect(revealed.text()).toContain('CONFIDENTIEL')

    const plain = mount(SensitiveValue, { props: { value: 'x', sensitive: false }, slots: { default: '<b>slot</b>' } })
    expect(plain.text()).toBe('slot')
  })
})
