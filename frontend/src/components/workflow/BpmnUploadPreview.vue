<script setup lang="ts">
import { ref } from 'vue'
import BpmnCanvas from './BpmnCanvas.vue'
import type { BpmnDeploymentResult } from '@/api/types'
import { useBpmnDeployment } from '@/composables/useBpmnDeployment'

const emit = defineEmits<{ deployed: [result: BpmnDeploymentResult] }>()

const deployment = useBpmnDeployment()

const selectedFile = ref<File | null>(null)
const bpmnXml = ref<string | null>(null)
const processDefinitionKey = ref<string | null>(null)
const readError = ref<string | null>(null)

function extractProcessDefinitionKey(xml: string): string | null {
  const match = xml.match(/<(?:[\w-]+:)?process\b[^>]*\bid\s*=\s*["']([^"']+)["']/)
  return match ? match[1] : null
}

function reset() {
  bpmnXml.value = null
  processDefinitionKey.value = null
  readError.value = null
  deployment.reset()
}

async function onFileChange(event: Event) {
  reset()
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  selectedFile.value = file
  if (!file) return

  let text: string
  try {
    text = await file.text()
  } catch {
    readError.value = 'Fichier illisible.'
    return
  }

  const key = extractProcessDefinitionKey(text)
  if (!key) {
    readError.value = "Fichier non reconnu comme un diagramme BPMN valide (id de processus introuvable)."
    return
  }

  bpmnXml.value = text
  processDefinitionKey.value = key
  await deployment.loadInfo(key)
}

async function confirm() {
  if (!selectedFile.value || !bpmnXml.value) return
  const result = await deployment.deploy(selectedFile.value)
  if (result) {
    emit('deployed', result)
  }
}
</script>

<template>
  <section class="bpmn-upload-preview">
    <h3>Déployer un diagramme BPMN</h3>
    <input type="file" accept=".xml,.bpmn20.xml,.bpmn" @change="onFileChange" />

    <p v-if="readError" class="bpmn-upload-preview__error">{{ readError }}</p>

    <template v-if="bpmnXml && processDefinitionKey">
      <BpmnCanvas :bpmn-xml="bpmnXml" />
      <p>Clé du processus : <strong>{{ processDefinitionKey }}</strong></p>
      <p v-if="deployment.deploymentInfo.value">
        Version actuelle déployée : {{ deployment.deploymentInfo.value.latestVersion }} —
        instances actives : {{ deployment.deploymentInfo.value.activeInstanceCount }}
      </p>
      <p v-else>Aucune version déployée pour cette clé pour l'instant.</p>

      <p v-if="deployment.deployError.value" class="bpmn-upload-preview__error">
        {{ deployment.deployError.value }}
      </p>
      <p v-if="deployment.deployedResult.value" class="bpmn-upload-preview__success">
        Déployé : version {{ deployment.deployedResult.value.deployedVersion }} (dépôt
        {{ deployment.deployedResult.value.deploymentId }}).
      </p>

      <button type="button" :disabled="deployment.deploying.value" @click="confirm">
        {{ deployment.deploying.value ? 'Déploiement en cours…' : 'Confirmer le déploiement' }}
      </button>
    </template>
  </section>
</template>

<style scoped>
.bpmn-upload-preview__error {
  color: #c92a2a;
  background: #fff0f0;
  padding: 0.5rem;
  border-radius: 4px;
}
.bpmn-upload-preview__success {
  color: #2f9e44;
}
</style>
