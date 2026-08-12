import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import axios from 'axios'
import type { AuthUser } from '@/api/types'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUser | null>(null)
  const authenticated = computed(() => user.value != null)

  const roles = computed(() => user.value?.roles ?? [])
  const isTenantAdmin = computed(() => roles.value.includes('TENANT_ADMIN'))
  const isWorkflowAdmin = computed(
    () => roles.value.includes('WORKFLOW_ADMIN') || roles.value.includes('TENANT_ADMIN'),
  )

  async function init(): Promise<void> {
    try {
      const { data } = await axios.get<AuthUser>('/api/auth/me')
      user.value = data
    } catch {
      user.value = null
      window.location.href = '/oauth2/authorization/oidc'
    }
  }

  async function logout(): Promise<void> {
    try {
      await axios.post('/api/auth/logout')
    } finally {
      user.value = null
      window.location.href = '/'
    }
  }

  return { user, authenticated, roles, isTenantAdmin, isWorkflowAdmin, init, logout }
})
