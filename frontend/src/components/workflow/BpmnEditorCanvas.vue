<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { loadBlockCatalog } from './bpmn-blocks/catalogClient'
import { setBlockCatalog, setOpenProcessKey } from './bpmn-blocks/blockCatalogHolder'

const props = defineProps<{
  bpmnXml: string
}>()

const emit = defineEmits<{ dirty: [] }>()

// Blank starter diagram (bpmn.io's standard "new diagram" template) used
// when the parent has no XML to load yet (brand-new diagram entry point).
//
// D-FLOWABLE-NS (TODO §52, found while proving flowable:formKey round-trips to saveXML() in a
// real E2E, not just get/set unit tests): no moddleExtensions are registered for the "flowable"
// namespace in this app (see flowablePropertiesProvider.ts header) -- setting a flowable:*
// attribute via the Modeling API only lands in the moddle element's generic $attrs bag, and
// moddle-xml's writer can only re-emit it if it already knows a URI for the "flowable" prefix
// from the parsed document. Without this xmlns:flowable declaration, the very first
// async/delegateExpression/expression/scope/formKey ever set on a brand-new diagram round-trips
// correctly through getValue()/setValue() (confirmed by reselecting the element) but is silently
// dropped by saveXML() -- moddle-xml swallows the failed prefix resolution. Pre-declaring the
// namespace here (harmless on an otherwise-unused prefix) fixes every diagram authored from
// scratch in this editor. A diagram imported from an external tool that never used any
// flowable:* attribute keeps the same residual risk this repo already had for
// async/delegateExpression/expression/scope before this fix -- out of scope here.
const EMPTY_BPMN_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:flowable="http://flowable.org/bpmn" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
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

function processKeyOf(xml: string): string | null {
  const match = xml.match(/<(?:[\w-]+:)?process\b[^>]*\bid\s*=\s*["']([^"']+)["']/)
  return match ? match[1] : null
}

async function render() {
  if (!host.value) return
  const xml = props.bpmnXml || EMPTY_BPMN_XML
  // TODO §73 : les statuts de workflow proposés par les briques viennent de la configuration de ce process.
  setOpenProcessKey(processKeyOf(xml))

  if (!modeler) {
    // TODO §73 : le catalogue est chargé avant la création du Modeler (ses modules s'instancient de façon
    // synchrone). Indisponible ⇒ palette et panneau natifs seulement (D-PALETTE-FALLBACK).
    const [
      { default: ModelerCtor },
      propertiesPanel,
      { FlowablePropertiesProviderModule },
      catalog,
      { BlockPaletteModule },
      { BlockPropertiesProviderModule },
      { BlockMarkersModule },
    ] = await Promise.all([
      import('bpmn-js/lib/Modeler'),
      import('bpmn-js-properties-panel'),
      import('./bpmn-properties/flowablePropertiesProvider'),
      loadBlockCatalog(),
      import('./bpmn-blocks/blockPaletteProvider'),
      import('./bpmn-blocks/blockPropertiesProvider'),
      import('./bpmn-blocks/blockMarkers'),
      // Feuilles de style de bpmn-js / diagram-js / panneau de propriétés : sans elles la palette (police d'icônes)
      // et le panneau sont invisibles ou non mis en forme. Chargées avec l'éditeur seulement.
      import('bpmn-js/dist/assets/diagram-js.css'),
      import('bpmn-js/dist/assets/bpmn-js.css'),
      import('bpmn-js/dist/assets/bpmn-font/css/bpmn.css'),
      import('@bpmn-io/properties-panel/assets/properties-panel.css'),
    ])
    const blocks = catalog?.blocks ?? []
    setBlockCatalog(blocks)
    const { BpmnPropertiesPanelModule, BpmnPropertiesProviderModule } = propertiesPanel
    const Modeler = ModelerCtor as unknown as new (options: Record<string, unknown>) => ModelerInstance

    modeler = new Modeler({
      container: host.value,
      propertiesPanel: { parent: propertiesHost.value },
      additionalModules: [
        BpmnPropertiesPanelModule,
        BpmnPropertiesProviderModule,
        FlowablePropertiesProviderModule,
        ...(blocks.length > 0 ? [BlockPaletteModule, BlockPropertiesProviderModule, BlockMarkersModule] : []),
      ],
    })

    // diagram-js's CommandStack has no .on() of its own — events are fired
    // through the eventBus (real bpmn-js behaviour, not reflected by a
    // mocked commandStack in unit tests).
    const eventBus = modeler.get('eventBus') as { on: (event: string, handler: () => void) => void }
    eventBus.on('commandStack.changed', () => {
      emit('dirty')
      // Revue M7 : l'id du process peut être renommé dans l'éditeur ; les statuts proposés suivent la clé courante.
      const root = (modeler?.get('canvas') as { getRootElement?: () => { businessObject?: { $type?: string; id?: string } } })
        .getRootElement?.()?.businessObject
      if (root?.$type === 'bpmn:Process' && root.id) setOpenProcessKey(root.id)
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
/* TODO §73 : brique dont un paramètre appelle un avertissement (non bloquant). */
.bpmn-editor-canvas__diagram :deep(.fb-block-warning .djs-visual > rect) {
  stroke: #d97706 !important;
  stroke-width: 3px !important;
}
:deep(.fb-palette-block) {
  position: relative;
}
:deep(.fb-palette-block::after) {
  content: '';
  position: absolute;
  right: 2px;
  bottom: 2px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #2563eb;
}
</style>
