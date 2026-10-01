<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ref } from 'vue'
import BpmnCanvas from './BpmnCanvas.vue'
import type { BpmnDeploymentResult } from '@/api/types'
import { useBpmnDeployment } from '@/composables/useBpmnDeployment'

const emit = defineEmits<{ deployed: [result: BpmnDeploymentResult] }>()

const { t } = useI18n()
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
    readError.value = t('workflow.upload.errors.unreadable')
    return
  }

  const key = extractProcessDefinitionKey(text)
  if (!key) {
    readError.value = t('workflow.upload.errors.notBpmn')
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
    <h3>{{ t('workflow.upload.title') }}</h3>
    <input type="file" accept=".xml,.bpmn20.xml,.bpmn" @change="onFileChange" />

    <p v-if="readError" class="bpmn-upload-preview__error">{{ readError }}</p>

    <template v-if="bpmnXml && processDefinitionKey">
      <BpmnCanvas :bpmn-xml="bpmnXml" />
      <p>{{ t('workflow.upload.processKey') }} <strong>{{ processDefinitionKey }}</strong></p>
      <p v-if="deployment.deploymentInfo.value">
        {{ t('workflow.upload.currentVersion', { version: deployment.deploymentInfo.value.latestVersion, count: deployment.deploymentInfo.value.activeInstanceCount }) }}
      </p>
      <p v-else>{{ t('workflow.upload.none') }}</p>

      <p v-if="deployment.deployError.value" class="bpmn-upload-preview__error">
        {{ deployment.deployError.value }}
      </p>
      <p v-if="deployment.deployedResult.value" class="bpmn-upload-preview__success">
        {{ t('workflow.upload.deployed', { version: deployment.deployedResult.value.deployedVersion, id: deployment.deployedResult.value.deploymentId }) }}
      </p>

      <button type="button" :disabled="deployment.deploying.value" @click="confirm">
        {{ deployment.deploying.value ? t('workflow.upload.deploying') : t('workflow.upload.confirm') }}
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
