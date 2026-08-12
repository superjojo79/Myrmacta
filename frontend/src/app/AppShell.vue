<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const { user, isTenantAdmin, isWorkflowAdmin } = storeToRefs(auth)
</script>

<template>
  <div class="app-shell">
    <aside class="app-shell__nav" aria-label="Pilotage">
      <div class="app-shell__brand">FlowForge</div>
      <p class="app-shell__section">Pilotage</p>
      <RouterLink class="app-shell__link" to="/supervision">Supervision</RouterLink>
      <RouterLink class="app-shell__link" to="/tasks">Tâches</RouterLink>
      <template v-if="isTenantAdmin || isWorkflowAdmin">
        <p class="app-shell__section">Admin</p>
        <span class="app-shell__link app-shell__link--muted">Types (vague 2)</span>
      </template>
    </aside>
    <div class="app-shell__content">
      <header class="app-shell__header">
        <span>{{ user?.name || user?.email || 'Session' }}</span>
        <button type="button" class="app-shell__logout" @click="auth.logout()">Déconnexion</button>
      </header>
      <main class="app-shell__main">
        <RouterView />
      </main>
    </div>
  </div>
</template>
