<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps<{
  dmnXml: string
}>()

const emit = defineEmits<{ dirty: [] }>()

// D2 (docs/ai/design/2026-09-11-feature-introduction-dmn.md) : dmn-js Modeler seul, sans
// provider de propriétés custom -- le .dmn est du DMN 1.3 standard, contrairement au BPMN
// FlowForge qui porte des extensions flowable:* (voir BpmnEditorCanvas.vue).
const EMPTY_DMN_XML = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" id="Definitions_1" name="DRD" namespace="http://camunda.org/schema/1.0/dmn">
  <decision id="Decision_1" name="Decision 1">
    <decisionTable id="DecisionTable_1">
      <input id="Input_1">
        <inputExpression id="InputExpression_1" typeRef="string">
          <text></text>
        </inputExpression>
      </input>
      <output id="Output_1" typeRef="string" />
    </decisionTable>
  </decision>
</definitions>`

const host = ref<HTMLDivElement | null>(null)

interface ViewerInstance {
  on: (event: string, handler: () => void) => void
}

interface ModelerInstance {
  importXML: (xml: string) => Promise<{ warnings: string[] }>
  saveXML: (options?: { format?: boolean }) => Promise<{ xml: string }>
  destroy: () => void
  getActiveViewer: () => ViewerInstance | null
  on: (event: string, handler: (...args: unknown[]) => void) => void
}

let modeler: ModelerInstance | null = null
let attachedViewer: ViewerInstance | null = null

function attachDirtyListener() {
  if (!modeler) return
  const viewer = modeler.getActiveViewer()
  if (!viewer || viewer === attachedViewer) return
  attachedViewer = viewer
  // Every dmn-js sub-view (DRD / decision table / literal expression) implements the same
  // View.on()/off() contract (dmn-js-shared/lib/base/View.js) and reuses diagram-js's
  // CommandStack -- 'commandStack.changed' fires uniformly across view types.
  viewer.on('commandStack.changed', () => {
    emit('dirty')
  })
}

async function render() {
  if (!host.value) return
  const xml = props.dmnXml || EMPTY_DMN_XML

  if (!modeler) {
    const { default: DmnModelerCtor } = await import('dmn-js/lib/Modeler')
    const Modeler = DmnModelerCtor as unknown as new (options: Record<string, unknown>) => ModelerInstance

    modeler = new Modeler({
      container: host.value,
      keyboard: { bindTo: window },
    })
    modeler.on('viewer.created', () => {
      attachDirtyListener()
    })
  }

  await modeler.importXML(xml)
  attachDirtyListener()
}

onMounted(() => {
  void render()
})

watch(
  () => props.dmnXml,
  () => {
    void render()
  },
)

onBeforeUnmount(() => {
  modeler?.destroy()
  modeler = null
  attachedViewer = null
})

async function saveXML(): Promise<string> {
  if (!modeler) return ''
  const { xml } = await modeler.saveXML({ format: false })
  return xml
}

defineExpose({ saveXML })
</script>

<template>
  <div class="dmn-editor-canvas">
    <div ref="host" class="dmn-editor-canvas__diagram" />
  </div>
</template>

<style scoped>
.dmn-editor-canvas {
  display: flex;
  height: 560px;
  border: 1px solid #d0d0d3;
  background: #fff;
}
.dmn-editor-canvas__diagram {
  flex: 1 1 auto;
  min-width: 0;
}
</style>
