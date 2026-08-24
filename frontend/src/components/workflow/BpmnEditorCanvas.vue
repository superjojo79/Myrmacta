<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps<{
  bpmnXml: string
}>()

const emit = defineEmits<{ dirty: [] }>()

// Blank starter diagram (bpmn.io's standard "new diagram" template) used
// when the parent has no XML to load yet (brand-new diagram entry point).
const EMPTY_BPMN_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="Process_1" isExecutable="false">
    <bpmn:startEvent id="StartEvent_1"/>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
      <bpmndi:BPMNShape id="BPMNShape_StartEvent_1" bpmnElement="StartEvent_1">
        <dc:Bounds x="173" y="102" width="36" height="36"/>
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`

const host = ref<HTMLDivElement | null>(null)
const propertiesHost = ref<HTMLDivElement | null>(null)

interface ModelerInstance {
  importXML: (xml: string) => Promise<unknown>
  saveXML: (options?: { format?: boolean }) => Promise<{ xml: string }>
  destroy: () => void
  get: (name: string) => unknown
}

let modeler: ModelerInstance | null = null

async function render() {
  if (!host.value) return
  const xml = props.bpmnXml || EMPTY_BPMN_XML

  if (!modeler) {
    const [{ default: ModelerCtor }, propertiesPanel, { FlowablePropertiesProviderModule }] = await Promise.all([
      import('bpmn-js/lib/Modeler'),
      import('bpmn-js-properties-panel'),
      import('./bpmn-properties/flowablePropertiesProvider'),
    ])
    const { BpmnPropertiesPanelModule, BpmnPropertiesProviderModule } = propertiesPanel
    const Modeler = ModelerCtor as unknown as new (options: Record<string, unknown>) => ModelerInstance

    modeler = new Modeler({
      container: host.value,
      propertiesPanel: { parent: propertiesHost.value },
      additionalModules: [BpmnPropertiesPanelModule, BpmnPropertiesProviderModule, FlowablePropertiesProviderModule],
    })

    const commandStack = modeler.get('commandStack') as { on: (event: string, handler: () => void) => void }
    commandStack.on('commandStack.changed', () => {
      emit('dirty')
    })
  }

  await modeler.importXML(xml)
  const canvas = modeler.get('canvas') as { zoom: (level: string) => void }
  canvas.zoom('fit-viewport')
}

onMounted(() => {
  void render()
})

watch(
  () => props.bpmnXml,
  () => {
    void render()
  },
)

onBeforeUnmount(() => {
  modeler?.destroy()
  modeler = null
})

async function saveXML(): Promise<string> {
  if (!modeler) return ''
  const { xml } = await modeler.saveXML({ format: false })
  return xml
}

defineExpose({ saveXML })
</script>

<template>
  <div class="bpmn-editor-canvas">
    <div ref="host" class="bpmn-editor-canvas__diagram" />
    <div ref="propertiesHost" class="bpmn-editor-canvas__properties" />
  </div>
</template>

<style scoped>
.bpmn-editor-canvas {
  display: flex;
  height: 560px;
  border: 1px solid #d0d0d3;
  background: #fff;
}
.bpmn-editor-canvas__diagram {
  flex: 1 1 auto;
  min-width: 0;
}
.bpmn-editor-canvas__properties {
  flex: 0 0 300px;
  border-left: 1px solid #d0d0d3;
  overflow-y: auto;
}
</style>
