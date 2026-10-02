<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { WorkflowDiagram } from '@/api/types'
import BpmnCanvas from './BpmnCanvas.vue'

defineProps<{
  diagram: WorkflowDiagram | null
  unavailable?: boolean
}>()

const { t } = useI18n()
</script>

<template>
  <section class="bpmn-viewer">
    <h2>{{ t('workflow.diagram.title') }}</h2>
    <p v-if="unavailable" class="bpmn-viewer__warn">
      {{ t('workflow.diagram.unavailable') }}
    </p>
    <BpmnCanvas
      v-else
      :bpmn-xml="diagram?.bpmnXml ?? ''"
      :active-ids="diagram?.activeIds"
      :completed-ids="diagram?.completedIds"
      :failed-ids="diagram?.failedIds"
    />
  </section>
</template>

<style scoped>
.bpmn-viewer__warn {
  color: #8a4b08;
  background: #fff4e5;
  padding: 0.75rem;
  border-radius: 4px;
}
</style>
