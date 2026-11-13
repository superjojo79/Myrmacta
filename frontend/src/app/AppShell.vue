<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import NotificationDrawer from '@/components/notifications/NotificationDrawer.vue'
import LocaleSwitcher from '@/i18n/LocaleSwitcher.vue'
import { useI18n } from 'vue-i18n'
import logoDark from '@/assets/brand/myrmacta-logo-dark.svg'
import { useShellNavCollapse } from '@/composables/useShellNavCollapse'

const { t } = useI18n()
const auth = useAuthStore()
const { user, isTenantAdmin, isWorkflowAdmin, canSeeReports } = storeToRefs(auth)
const { isNarrow, navOpen, toggleNav, closeNav } = useShellNavCollapse()

const shellClass = computed(() => ({
  'app-shell--collapsed': !navOpen.value,
  'app-shell--narrow': isNarrow.value,
  'app-shell--drawer-open': isNarrow.value && navOpen.value,
}))

const toggleLabel = computed(() =>
  navOpen.value ? t('shell.toggleClose') : t('shell.toggleOpen'),
)

function onNavLinkClick() {
  if (isNarrow.value) {
    closeNav()
  }
}
</script>

<template>
  <div class="app-shell" data-testid="app-shell" :class="shellClass">
    <div
      v-if="isNarrow && navOpen"
      class="app-shell__backdrop"
      data-testid="shell-nav-backdrop"
      @click="closeNav()"
    />
    <aside id="app-shell-nav" class="app-shell__nav" :aria-label="t('shell.navLabel')">
      <button
        type="button"
        class="app-shell__burger"
        data-testid="shell-nav-toggle"
        :aria-expanded="navOpen"
        aria-controls="app-shell-nav-body"
        :aria-label="toggleLabel"
        @click="toggleNav()"
      >
        <span class="app-shell__burger-icon" aria-hidden="true" />
      </button>
      <div
        v-show="navOpen"
        id="app-shell-nav-body"
        class="app-shell__nav-body"
        data-testid="shell-nav-body"
      >
        <div class="app-shell__brand">
          <img class="app-shell__logo" :src="logoDark" :alt="t('shell.brand')" />
          <p class="app-shell__tagline">{{ t('shell.tagline') }}</p>
        </div>
        <p class="app-shell__section">{{ t('shell.sections.pilotage') }}</p>
        <RouterLink class="app-shell__link" to="/supervision" @click="onNavLinkClick">
          {{ t('shell.nav.supervision') }}
        </RouterLink>
        <RouterLink class="app-shell__link" to="/tasks" @click="onNavLinkClick">
          {{ t('shell.nav.tasks') }}
        </RouterLink>
        <RouterLink class="app-shell__link" to="/signatures" @click="onNavLinkClick">
          {{ t('shell.nav.signatures') }}
        </RouterLink>
        <RouterLink
          v-if="canSeeReports"
          class="app-shell__link"
          to="/rapports"
          @click="onNavLinkClick"
        >
          {{ t('shell.nav.reports') }}
        </RouterLink>
        <RouterLink class="app-shell__link" to="/dossiers/new" @click="onNavLinkClick">
          {{ t('shell.nav.newDossier') }}
        </RouterLink>
        <RouterLink class="app-shell__link" to="/mail-deposit" @click="onNavLinkClick">
          {{ t('shell.nav.mailDeposit') }}
        </RouterLink>
        <template v-if="isTenantAdmin || isWorkflowAdmin">
          <p class="app-shell__section">{{ t('shell.sections.admin') }}</p>
          <RouterLink
            v-if="isTenantAdmin"
            class="app-shell__link"
            to="/admin/dossier-types"
            @click="onNavLinkClick"
          >
            {{ t('shell.nav.types') }}
          </RouterLink>
          <RouterLink
            v-if="isTenantAdmin"
            class="app-shell__link"
            to="/admin/templates"
            @click="onNavLinkClick"
          >
            {{ t('shell.nav.templates') }}
          </RouterLink>
          <RouterLink
            v-if="isTenantAdmin"
            class="app-shell__link"
            to="/admin/notification-templates"
            @click="onNavLinkClick"
          >
            {{ t('shell.nav.notificationTemplates') }}
          </RouterLink>
          <RouterLink
            v-if="isTenantAdmin"
            class="app-shell__link"
            to="/admin/connectors"
            @click="onNavLinkClick"
          >
            {{ t('shell.nav.connectors') }}
          </RouterLink>
          <RouterLink
            v-if="isTenantAdmin"
            class="app-shell__link"
            to="/admin/configuration"
            @click="onNavLinkClick"
          >
            {{ t('shell.nav.promotion') }}
          </RouterLink>
          <RouterLink
            v-if="isTenantAdmin"
            class="app-shell__link"
            to="/admin/purge-outbox"
            @click="onNavLinkClick"
          >
            {{ t('shell.nav.purgeQueue') }}
          </RouterLink>
          <RouterLink
            v-if="isTenantAdmin"
            class="app-shell__link"
            to="/admin/observabilite"
            @click="onNavLinkClick"
          >
            {{ t('shell.nav.observability') }}
          </RouterLink>
          <RouterLink
            v-if="isTenantAdmin"
            class="app-shell__link"
            to="/admin/user-groups"
            @click="onNavLinkClick"
          >
            {{ t('shell.nav.userGroups') }}
          </RouterLink>
          <RouterLink
            v-if="isTenantAdmin"
            class="app-shell__link"
            to="/admin/notification-webhooks"
            @click="onNavLinkClick"
          >
            {{ t('shell.nav.webhooks') }}
          </RouterLink>
          <RouterLink
            v-if="isTenantAdmin || isWorkflowAdmin"
            class="app-shell__link"
            to="/admin/assistant"
            @click="onNavLinkClick"
          >
            {{ t('shell.nav.assistant') }}
          </RouterLink>
          <RouterLink
            v-if="isTenantAdmin || isWorkflowAdmin"
            class="app-shell__link"
            to="/admin/parcours"
            @click="onNavLinkClick"
          >
            {{ t('shell.nav.parcours') }}
          </RouterLink>
          <RouterLink
            v-if="isTenantAdmin || isWorkflowAdmin"
            class="app-shell__link"
            to="/admin/drafts"
            @click="onNavLinkClick"
          >
            {{ t('shell.nav.drafts') }}
          </RouterLink>
          <RouterLink
            v-if="isTenantAdmin || isWorkflowAdmin"
            class="app-shell__link"
            to="/admin/incidents"
            @click="onNavLinkClick"
          >
            {{ t('shell.nav.incidents') }}
          </RouterLink>
          <RouterLink
            v-if="isTenantAdmin || isWorkflowAdmin"
            class="app-shell__link"
            to="/admin/workflow-types"
            @click="onNavLinkClick"
          >
            {{ t('shell.nav.workflows') }}
          </RouterLink>
          <RouterLink
            v-if="isTenantAdmin || isWorkflowAdmin"
            class="app-shell__link"
            to="/admin/task-question-schemas"
            @click="onNavLinkClick"
          >
            {{ t('shell.nav.taskQuestions') }}
          </RouterLink>
          <RouterLink
            v-if="isTenantAdmin || isWorkflowAdmin"
            class="app-shell__link"
            to="/admin/dmn-editor"
            @click="onNavLinkClick"
          >
            {{ t('shell.nav.decisionTables') }}
          </RouterLink>
        </template>
      </div>
    </aside>
    <div class="app-shell__content">
      <header class="app-shell__header">
        <NotificationDrawer />
        <span>{{ user?.name || user?.email || t('shell.session') }}</span>
        <LocaleSwitcher />
        <button type="button" class="app-shell__logout" @click="auth.logout()">
          {{ t('shell.logout') }}
        </button>
      </header>
      <main class="app-shell__main">
        <RouterView />
      </main>
    </div>
  </div>
</template>
