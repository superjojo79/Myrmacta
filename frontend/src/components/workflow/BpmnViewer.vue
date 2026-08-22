<script setup lang="ts">
import type { WorkflowDiagram } from '@/api/types'
import BpmnCanvas from './BpmnCanvas.vue'

defineProps<{
  diagram: WorkflowDiagram | null
  unavailable?: boolean
}>()
</script>

<template>
  <section class="bpmn-viewer">
    <h2>Diagramme</h2>
    <p v-if="unavailable" class="bpmn-viewer__warn">
      Diagramme indisponible (processus non démarré ou lien sans instance).
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
