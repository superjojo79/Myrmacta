<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { WorkflowLink } from '@/api/types'
import AdminPager from '@/components/admin/AdminPager.vue'

const props = defineProps<{
  workflows: WorkflowLink[]
  selectedId?: string | null
  totalPages?: number
}>()

const emit = defineEmits<{
  select: [linkId: string]
}>()

const page = defineModel<number>('page', { default: 0 })

const { t } = useI18n()

function onSelect(link: WorkflowLink): void {
  if (!link.processInstanceId) return
  emit('select', link.id)
}
</script>

<template>
  <section class="workflow-links-list">
    <h2>{{ t('dossier.workflows.title') }}</h2>
    <p v-if="workflows.length === 0" class="workflow-links-list__empty">{{ t('dossier.workflows.empty') }}</p>
    <ul v-else>
      <li v-for="link in workflows" :key="link.id">
        <button
          type="button"
          class="workflow-links-list__item"
          :class="{
            'workflow-links-list__item--selected': props.selectedId === link.id,
            'workflow-links-list__item--disabled': !link.processInstanceId,
          }"
          :data-testid="`workflow-link-${link.id}`"
          :disabled="!link.processInstanceId"
          :aria-pressed="props.selectedId === link.id"
          :aria-label="t('dossier.workflows.select', { key: link.processDefinitionKey, sequence: link.sequence })"
          @click="onSelect(link)"
        >
          <strong>{{ link.processDefinitionKey }}</strong>
          <span class="workflow-links-list__meta">
            #{{ link.sequence }} · {{ link.status }} · {{ link.startedAt }} →
            {{ link.endedAt || t('dossier.workflows.ongoing') }}
          </span>
        </button>
      </li>
    </ul>
    <AdminPager v-if="(totalPages ?? 1) > 1" v-model="page" :total-pages="totalPages ?? 1" />
  </section>
</template>

<style scoped>
.workflow-links-list ul {
  list-style: none;
  padding: 0;
  margin: 0;
}
.workflow-links-list__item {
  display: block;
  width: 100%;
  text-align: left;
  padding: 0.65rem 0.5rem;
  margin: 0;
  border: none;
  border-bottom: 1px solid #ddd;
  border-left: 3px solid transparent;
  background: transparent;
  cursor: pointer;
  font: inherit;
  color: inherit;
}
.workflow-links-list__item:hover:not(:disabled) {
  background: color-mix(in srgb, currentColor 6%, transparent);
}
.workflow-links-list__item--selected {
  border-left-color: currentColor;
  background: color-mix(in srgb, currentColor 8%, transparent);
}
.workflow-links-list__item--disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
.workflow-links-list__meta {
  display: block;
  font-size: 0.85rem;
  opacity: 0.7;
}
.workflow-links-list__empty {
  opacity: 0.7;
}
</style>
