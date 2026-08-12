<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { WorkflowDiagram } from '@/api/types'

const props = defineProps<{
  diagram: WorkflowDiagram | null
  unavailable?: boolean
}>()

const host = ref<HTMLDivElement | null>(null)
let viewer: { importXML: (xml: string) => Promise<unknown>; destroy: () => void; get: (name: string) => unknown } | null =
  null

async function render() {
  if (!host.value || !props.diagram) return
  if (!viewer) {
    const { default: BpmnViewer } = await import('bpmn-js/lib/NavigatedViewer')
    viewer = new BpmnViewer({ container: host.value })
  }
  await viewer.importXML(props.diagram.bpmnXml)
  const canvas = viewer.get('canvas') as { zoom: (level: string) => void; addMarker: (id: string, cls: string) => void }
  canvas.zoom('fit-viewport')
  for (const id of props.diagram.completedIds) canvas.addMarker(id, 'highlight-completed')
  for (const id of props.diagram.activeIds) canvas.addMarker(id, 'highlight-active')
  for (const id of props.diagram.failedIds) canvas.addMarker(id, 'highlight-failed')
}

onMounted(() => {
  void render()
})

watch(
  () => props.diagram,
  () => {
    void render()
  },
)

onBeforeUnmount(() => {
  viewer?.destroy()
  viewer = null
})
</script>

<template>
  <section class="bpmn-viewer">
    <h2>Diagramme</h2>
    <p v-if="unavailable" class="bpmn-viewer__warn">
      Diagramme indisponible (processus non démarré ou lien sans instance).
    </p>
    <div v-else ref="host" class="bpmn-viewer__canvas" />
  </section>
</template>

<style scoped>
.bpmn-viewer__canvas {
  height: 360px;
  border: 1px solid #d0d0d3;
  background: #fff;
}
.bpmn-viewer__warn {
  color: #8a4b08;
  background: #fff4e5;
  padding: 0.75rem;
  border-radius: 4px;
}
:deep(.highlight-active:not(.djs-connection) .djs-visual > :nth-child(1)) {
  stroke: #2f6fed !important;
}
:deep(.highlight-completed:not(.djs-connection) .djs-visual > :nth-child(1)) {
  stroke: #2f9e44 !important;
}
:deep(.highlight-failed:not(.djs-connection) .djs-visual > :nth-child(1)) {
  stroke: #c92a2a !important;
}
</style>
