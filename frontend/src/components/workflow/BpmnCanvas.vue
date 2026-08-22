<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps<{
  bpmnXml: string
  activeIds?: string[]
  completedIds?: string[]
  failedIds?: string[]
}>()

const host = ref<HTMLDivElement | null>(null)
let viewer: { importXML: (xml: string) => Promise<unknown>; destroy: () => void; get: (name: string) => unknown } | null =
  null

async function render() {
  if (!host.value || !props.bpmnXml) return
  if (!viewer) {
    const { default: BpmnViewer } = await import('bpmn-js/lib/NavigatedViewer')
    viewer = new BpmnViewer({ container: host.value })
  }
  await viewer.importXML(props.bpmnXml)
  const canvas = viewer.get('canvas') as { zoom: (level: string) => void; addMarker: (id: string, cls: string) => void }
  canvas.zoom('fit-viewport')
  for (const id of props.completedIds ?? []) canvas.addMarker(id, 'highlight-completed')
  for (const id of props.activeIds ?? []) canvas.addMarker(id, 'highlight-active')
  for (const id of props.failedIds ?? []) canvas.addMarker(id, 'highlight-failed')
}

onMounted(() => {
  void render()
})

watch(
  () => [props.bpmnXml, props.activeIds, props.completedIds, props.failedIds],
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
  <div ref="host" class="bpmn-canvas" />
</template>

<style scoped>
.bpmn-canvas {
  height: 360px;
  border: 1px solid #d0d0d3;
  background: #fff;
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
