<script setup lang="ts">
import { ref } from 'vue'
import BpmnCanvas from './BpmnCanvas.vue'
import { deployBpmn, getBpmnDeploymentInfo } from '@/api/workflows'
import type { BpmnDeploymentResult, ProcessDeploymentInfo } from '@/api/types'
import { toProblemDetail } from '@/api/http'

const emit = defineEmits<{ deployed: [result: BpmnDeploymentResult] }>()

const selectedFile = ref<File | null>(null)
const bpmnXml = ref<string | null>(null)
const processDefinitionKey = ref<string | null>(null)
const deploymentInfo = ref<ProcessDeploymentInfo | null>(null)
const readError = ref<string | null>(null)
const deployError = ref<string | null>(null)
const deploying = ref(false)
const deployedResult = ref<BpmnDeploymentResult | null>(null)

function extractProcessDefinitionKey(xml: string): string | null {
  const match = xml.match(/<(?:[\w-]+:)?process\b[^>]*\bid="([^"]+)"/)
  return match ? match[1] : null
}

function reset() {
  bpmnXml.value = null
  processDefinitionKey.value = null
  deploymentInfo.value = null
  readError.value = null
  deployError.value = null
  deployedResult.value = null
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
  try {
    deploymentInfo.value = await getBpmnDeploymentInfo(key)
  } catch {
    deploymentInfo.value = null
  }
}

async function confirm() {
  if (!selectedFile.value || !bpmnXml.value) return
  deploying.value = true
  deployError.value = null
  try {
    const result = await deployBpmn(selectedFile.value)
    deployedResult.value = result
    emit('deployed', result)
  } catch (err) {
    deployError.value = toProblemDetail(err).detail ?? 'Déploiement impossible'
  } finally {
    deploying.value = false
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
      <p v-if="deploymentInfo">
        Version actuelle déployée : {{ deploymentInfo.latestVersion }} —
        instances actives : {{ deploymentInfo.activeInstanceCount }}
      </p>
      <p v-else>Aucune version déployée pour cette clé pour l'instant.</p>

      <p v-if="deployError" class="bpmn-upload-preview__error">{{ deployError }}</p>
      <p v-if="deployedResult" class="bpmn-upload-preview__success">
        Déployé : version {{ deployedResult.deployedVersion }} (dépôt {{ deployedResult.deploymentId }}).
      </p>

      <button type="button" :disabled="deploying" @click="confirm">
        {{ deploying ? 'Déploiement en cours…' : 'Confirmer le déploiement' }}
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
