import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import axios, { type AxiosError } from 'axios'
import type { AuthUser } from '@/api/types'
import { resolveApiBaseUrl } from '@/api/http'
import { useNotificationsStore } from '@/stores/notifications'

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`))
  return match ? decodeURIComponent(match[1]) : null
}

function tenantPath(path: string): string {
  const prefix = resolveApiBaseUrl()
  return `${prefix}${path.startsWith('/') ? path : `/${path}`}`
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUser | null>(null)
  const bootError = ref<string | null>(null)
  const authenticated = computed(() => user.value != null)

  const roles = computed(() => user.value?.roles ?? [])
  const isTenantAdmin = computed(() => roles.value.includes('TENANT_ADMIN'))
  const isWorkflowAdmin = computed(
    () => roles.value.includes('WORKFLOW_ADMIN') || roles.value.includes('TENANT_ADMIN'),
  )
  const canSeeReports = computed(
    () => isWorkflowAdmin.value || roles.value.includes('DOSSIER_MANAGER'),
  )

  async function init(): Promise<void> {
    bootError.value = null
    try {
      const { data } = await axios.get<AuthUser>('/api/auth/me')
      user.value = data
    } catch (err) {
      user.value = null
      const status = (err as AxiosError)?.response?.status
      if (status === 401 || status === 403) {
        window.location.href = tenantPath('/oauth2/authorization/oidc')
        return
      }
      bootError.value = 'Impossible de charger la session'
    }
  }

  function logout(): void {
    try {
      useNotificationsStore().disconnect()
    } catch {
      /* pinia may be unavailable in isolated tests */
    }
    user.value = null
    const form = document.createElement('form')
    form.method = 'POST'
    form.action = tenantPath('/api/auth/logout')
    const csrf = readCookie('XSRF-TOKEN')
    if (csrf) {
      const input = document.createElement('input')
      input.type = 'hidden'
      input.name = '_csrf'
      input.value = csrf
      form.appendChild(input)
    }
    document.body.appendChild(form)
    form.submit()
  }

  return {
    user,
    bootError,
    authenticated,
    roles,
    isTenantAdmin,
    isWorkflowAdmin,
    canSeeReports,
    init,
    logout,
  }
})
