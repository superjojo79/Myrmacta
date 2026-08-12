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
