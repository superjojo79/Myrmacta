<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import NotificationDrawer from '@/components/notifications/NotificationDrawer.vue'
import LocaleSwitcher from '@/i18n/LocaleSwitcher.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const auth = useAuthStore()
const { user, isTenantAdmin, isWorkflowAdmin, canSeeReports } = storeToRefs(auth)
</script>

<template>
  <div class="app-shell">
    <aside class="app-shell__nav" :aria-label="t('shell.navLabel')">
      <div class="app-shell__brand">{{ t('shell.brand') }}</div>
      <p class="app-shell__section">{{ t('shell.sections.pilotage') }}</p>
      <RouterLink class="app-shell__link" to="/supervision">{{ t('shell.nav.supervision') }}</RouterLink>
      <RouterLink class="app-shell__link" to="/tasks">{{ t('shell.nav.tasks') }}</RouterLink>
      <RouterLink class="app-shell__link" to="/signatures">{{ t('shell.nav.signatures') }}</RouterLink>
      <RouterLink v-if="canSeeReports" class="app-shell__link" to="/rapports">{{ t('shell.nav.reports') }}</RouterLink>
      <RouterLink class="app-shell__link" to="/dossiers/new">{{ t('shell.nav.newDossier') }}</RouterLink>
      <template v-if="isTenantAdmin || isWorkflowAdmin">
        <p class="app-shell__section">{{ t('shell.sections.admin') }}</p>
        <RouterLink v-if="isTenantAdmin" class="app-shell__link" to="/admin/dossier-types">
          {{ t('shell.nav.types') }}
        </RouterLink>
        <RouterLink v-if="isTenantAdmin" class="app-shell__link" to="/admin/templates">
          {{ t('shell.nav.templates') }}
        </RouterLink>
        <RouterLink v-if="isTenantAdmin" class="app-shell__link" to="/admin/connectors">
          {{ t('shell.nav.connectors') }}
        </RouterLink>
        <RouterLink v-if="isTenantAdmin" class="app-shell__link" to="/admin/configuration">
          {{ t('shell.nav.promotion') }}
        </RouterLink>
        <RouterLink v-if="isTenantAdmin" class="app-shell__link" to="/admin/purge-outbox">
          {{ t('shell.nav.purgeQueue') }}
        </RouterLink>
        <RouterLink v-if="isTenantAdmin" class="app-shell__link" to="/admin/observabilite">
          {{ t('shell.nav.observability') }}
        </RouterLink>
        <RouterLink v-if="isTenantAdmin" class="app-shell__link" to="/admin/notification-webhooks">
          {{ t('shell.nav.webhooks') }}
        </RouterLink>
        <RouterLink
          v-if="isTenantAdmin || isWorkflowAdmin"
          class="app-shell__link"
          to="/admin/assistant"
        >
          {{ t('shell.nav.assistant') }}
        </RouterLink>
        <RouterLink
          v-if="isTenantAdmin || isWorkflowAdmin"
          class="app-shell__link"
          to="/admin/parcours"
        >
          {{ t('shell.nav.parcours') }}
        </RouterLink>
        <RouterLink
          v-if="isTenantAdmin || isWorkflowAdmin"
          class="app-shell__link"
          to="/admin/drafts"
        >
          {{ t('shell.nav.drafts') }}
        </RouterLink>
        <RouterLink
          v-if="isTenantAdmin || isWorkflowAdmin"
          class="app-shell__link"
          to="/admin/workflow-types"
        >
          {{ t('shell.nav.workflows') }}
        </RouterLink>
        <RouterLink
          v-if="isTenantAdmin || isWorkflowAdmin"
          class="app-shell__link"
          to="/admin/task-question-schemas"
        >
          {{ t('shell.nav.taskQuestions') }}
        </RouterLink>
        <RouterLink
          v-if="isTenantAdmin || isWorkflowAdmin"
          class="app-shell__link"
          to="/admin/dmn-editor"
        >
          {{ t('shell.nav.decisionTables') }}
        </RouterLink>
      </template>
    </aside>
    <div class="app-shell__content">
      <header class="app-shell__header">
        <NotificationDrawer />
        <span>{{ user?.name || user?.email || t('shell.session') }}</span>
        <LocaleSwitcher />
        <button type="button" class="app-shell__logout" @click="auth.logout()">{{ t('shell.logout') }}</button>
      </header>
      <main class="app-shell__main">
        <RouterView />
      </main>
    </div>
  </div>
</template>
