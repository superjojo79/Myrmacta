<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { WorkflowIncident } from '@/api/types'
import { formatDateTime } from '@/i18n/format'

/**
 * TODO §94 — étapes de l'exécution dont le job est en échec. « Rejouer » ne concerne que les incidents (dead letter) et
 * les administrateurs ; pas de `window.confirm` : l'action est sûre, et les fenêtres natives sont bloquées par certains
 * navigateurs (recette du 2026-10-18).
 */
defineProps<{
  incidents: WorkflowIncident[]
  canRetry: boolean
  /** Job dont le rejeu est en cours (bouton désactivé). */
  retryingJobId?: string | null
}>()

const emit = defineEmits<{ retry: [jobId: string] }>()

const { t } = useI18n()
</script>

<template>
  <section
    v-if="incidents.length > 0"
    class="incident-banner"
    role="region"
    aria-labelledby="incident-banner-title"
    data-testid="incident-banner"
  >
    <h2 id="incident-banner-title">{{ t('workflow.incidents.title') }}</h2>
    <ul>
      <li
        v-for="incident in incidents"
        :key="incident.jobId"
        data-testid="incident-row"
        class="incident-banner__row"
      >
        <span
          class="incident-banner__state"
          :class="`incident-banner__state--${incident.state.toLowerCase()}`"
        >
          {{
            incident.state === 'INCIDENT'
              ? t('workflow.incidents.state.INCIDENT')
              : t('workflow.incidents.state.RETRYING', incident.retriesLeft)
          }}
        </span>
        <strong>{{ incident.activityName }}</strong>
        <span v-if="incident.since" class="incident-banner__since">
          {{ t('workflow.incidents.since', { date: formatDateTime(incident.since) }) }}
        </span>
        <code
          v-if="incident.message"
          class="incident-banner__message"
          data-testid="incident-message"
          >{{ incident.message }}</code
        >
        <button
          v-if="canRetry && incident.state === 'INCIDENT'"
          type="button"
          data-testid="incident-retry"
          :disabled="retryingJobId === incident.jobId"
          :aria-label="t('workflow.incidents.retryStep', { step: incident.activityName })"
          @click="emit('retry', incident.jobId)"
        >
          {{ t('workflow.incidents.retry') }}
        </button>
      </li>
    </ul>
    <p class="incident-banner__hint">
      {{ canRetry ? t('workflow.incidents.hintAdmin') : t('workflow.incidents.hintReader') }}
    </p>
  </section>
</template>

<style scoped>
.incident-banner {
  border: 1px solid #c9302c;
  background: #fdf1f0;
  padding: 0.75rem 1rem;
  border-radius: 6px;
  margin: 1rem 0;
}
.incident-banner h2 {
  margin: 0 0 0.5rem;
  font-size: 1rem;
  color: #a12a2a;
}
.incident-banner ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.5rem;
}
.incident-banner__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}
.incident-banner__state {
  font-size: 0.8rem;
  padding: 0.1rem 0.4rem;
  border-radius: 4px;
  background: #f6d6d3;
}
.incident-banner__state--retrying {
  background: #fbe9c9;
}
.incident-banner__since,
.incident-banner__hint {
  font-size: 0.85rem;
  color: #55595f;
}
.incident-banner__message {
  flex-basis: 100%;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 0.8rem;
}
.incident-banner__hint {
  margin: 0.5rem 0 0;
}
button {
  border: 1px solid #b7b7ba;
  background: white;
  padding: 0.25rem 0.6rem;
  cursor: pointer;
}
</style>
