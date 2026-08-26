<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import NotificationDrawer from '@/components/notifications/NotificationDrawer.vue'

const auth = useAuthStore()
const { user, isTenantAdmin, isWorkflowAdmin, canSeeReports } = storeToRefs(auth)
</script>

<template>
  <div class="app-shell">
    <aside class="app-shell__nav" aria-label="Pilotage">
      <div class="app-shell__brand">FlowForge</div>
      <p class="app-shell__section">Pilotage</p>
      <RouterLink class="app-shell__link" to="/supervision">Supervision</RouterLink>
      <RouterLink class="app-shell__link" to="/tasks">Tâches</RouterLink>
      <RouterLink v-if="canSeeReports" class="app-shell__link" to="/rapports">Rapports &amp; SLA</RouterLink>
      <RouterLink class="app-shell__link" to="/dossiers/new">Nouveau dossier</RouterLink>
      <template v-if="isTenantAdmin || isWorkflowAdmin">
        <p class="app-shell__section">Admin</p>
        <RouterLink v-if="isTenantAdmin" class="app-shell__link" to="/admin/dossier-types">
          Types
        </RouterLink>
        <RouterLink v-if="isTenantAdmin" class="app-shell__link" to="/admin/templates">
          Templates
        </RouterLink>
        <RouterLink v-if="isTenantAdmin" class="app-shell__link" to="/admin/connectors">
          Connecteurs
        </RouterLink>
        <RouterLink v-if="isTenantAdmin" class="app-shell__link" to="/admin/purge-outbox">
          File purge
        </RouterLink>
        <RouterLink v-if="isTenantAdmin" class="app-shell__link" to="/admin/observabilite">
          Observabilité
        </RouterLink>
        <RouterLink v-if="isTenantAdmin" class="app-shell__link" to="/admin/notification-webhooks">
          Webhooks
        </RouterLink>
        <RouterLink
          v-if="isTenantAdmin || isWorkflowAdmin"
          class="app-shell__link"
          to="/admin/workflow-types"
        >
          Workflows
        </RouterLink>
      </template>
    </aside>
    <div class="app-shell__content">
      <header class="app-shell__header">
        <NotificationDrawer />
        <span>{{ user?.name || user?.email || 'Session' }}</span>
        <button type="button" class="app-shell__logout" @click="auth.logout()">Déconnexion</button>
      </header>
      <main class="app-shell__main">
        <RouterView />
      </main>
    </div>
  </div>
</template>
