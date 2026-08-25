<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import BpmnEditorCanvas from '@/components/workflow/BpmnEditorCanvas.vue'
import { useBpmnDraft } from '@/composables/useBpmnDraft'
import { useBpmnDeployment } from '@/composables/useBpmnDeployment'

const route = useRoute()
const draft = useBpmnDraft()
const deployment = useBpmnDeployment()

const canvasRef = ref<InstanceType<typeof BpmnEditorCanvas> | null>(null)
const importedXml = ref<string | null>(null)
const readError = ref<string | null>(null)
const saveError = ref<string | null>(null)
const deployPending = ref(false)
// Only mount the canvas once the initial content (draft/deployed/blank) is
// known, instead of mounting it eagerly and re-importing once the async
// load resolves — avoids a blank-then-reimport flicker/race.
const ready = ref(false)

const processDefinitionKey = computed(() => {
  const raw = route.query.processDefinitionKey
  return typeof raw === 'string' && raw.length > 0 ? raw : null
})

const canvasXml = computed(() => importedXml.value ?? draft.xml.value ?? '')

onMounted(async () => {
  if (processDefinitionKey.value) {
    await draft.load(processDefinitionKey.value)
  }
  ready.value = true
})

function extractProcessDefinitionKey(xml: string): string | null {
  const match = xml.match(/<(?:[\w-]+:)?process\b[^>]*\bid\s*=\s*["']([^"']+)["']/)
  return match ? match[1] : null
}

async function onFileChange(event: Event) {
  readError.value = null
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  if (!file) return

  let text: string
  try {
    text = await file.text()
  } catch {
    readError.value = 'Fichier illisible.'
    return
  }

  importedXml.value = text
  draft.markDirty()
}

function onCanvasDirty() {
  draft.markDirty()
}

async function saveDraft() {
  if (!canvasRef.value) return
  saveError.value = null
  const xml = await canvasRef.value.saveXML()
  const key = extractProcessDefinitionKey(xml)
  if (!key) {
    saveError.value = "Diagramme non reconnu comme un diagramme BPMN valide (id de processus introuvable)."
    return
  }
  await draft.save(key, xml)
}

async function prepareDeploy() {
  if (!canvasRef.value) return
  saveError.value = null
  const xml = await canvasRef.value.saveXML()
  const key = extractProcessDefinitionKey(xml)
  if (!key) {
    saveError.value = "Diagramme non reconnu comme un diagramme BPMN valide (id de processus introuvable)."
    return
  }
  deployPending.value = true
  await deployment.loadInfo(key)
}

async function confirmDeploy() {
  if (!canvasRef.value) return
  const xml = await canvasRef.value.saveXML()
  const key = extractProcessDefinitionKey(xml)
  if (!key) return
  const file = new File([xml], `${key}.bpmn20.xml`, { type: 'application/xml' })
  await deployment.deploy(file)
}
</script>

<template>
  <section class="bpmn-editor">
    <h1>Éditeur de diagramme BPMN</h1>

    <div class="bpmn-editor__toolbar">
      <label>
        Importer un fichier local
        <input type="file" accept=".xml,.bpmn20.xml,.bpmn" @change="onFileChange" />
      </label>
      <button type="button" class="bpmn-editor__save" @click="saveDraft">Enregistrer le brouillon</button>
      <button type="button" class="bpmn-editor__deploy" @click="prepareDeploy">Déployer</button>
    </div>

    <p v-if="draft.loadError.value" class="bpmn-editor__error">
      Impossible de charger le diagramme : {{ draft.loadError.value }}
    </p>
    <p v-if="readError" class="bpmn-editor__error">{{ readError }}</p>
    <p v-if="saveError" class="bpmn-editor__error">{{ saveError }}</p>
    <p v-if="draft.hasUnsavedChanges.value" class="bpmn-editor__unsaved">Modifications non enregistrées.</p>

    <BpmnEditorCanvas v-if="ready" ref="canvasRef" :bpmn-xml="canvasXml" @dirty="onCanvasDirty" />

    <section v-if="deployPending" class="bpmn-editor__deploy-panel">
      <p v-if="deployment.deploymentInfo.value">
        Version actuelle déployée : {{ deployment.deploymentInfo.value.latestVersion }} —
        instances actives : {{ deployment.deploymentInfo.value.activeInstanceCount }}
      </p>
      <p v-else>Aucune version déployée pour cette clé pour l'instant.</p>

      <p v-if="deployment.deployError.value" class="bpmn-editor__error">{{ deployment.deployError.value }}</p>
      <p v-if="deployment.deployedResult.value" class="bpmn-editor__success">
        Déployé : version {{ deployment.deployedResult.value.deployedVersion }} (dépôt
        {{ deployment.deployedResult.value.deploymentId }}).
      </p>

      <button
        type="button"
        class="bpmn-editor__confirm-deploy"
        :disabled="deployment.deploying.value"
        @click="confirmDeploy"
      >
        {{ deployment.deploying.value ? 'Déploiement en cours…' : 'Confirmer le déploiement' }}
      </button>
    </section>
  </section>
</template>

<style scoped>
.bpmn-editor__toolbar {
  display: flex;
  gap: 1rem;
  align-items: center;
  margin-bottom: 1rem;
}
.bpmn-editor__error {
  color: #c92a2a;
  background: #fff0f0;
  padding: 0.5rem;
  border-radius: 4px;
}
.bpmn-editor__success {
  color: #2f9e44;
}
.bpmn-editor__unsaved {
  color: #8a4b08;
}
</style>
