<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { onMounted, ref, watch } from 'vue'
import {
  clearReplyWorkflow,
  getReplyWorkflow,
  setReplyWorkflow,
  type ReplyWorkflowMode,
} from '@/api/dossierTypes'
import { listAllWorkflowTypeConfigs } from '@/api/workflows'
import { toProblemDetail } from '@/api/http'

/** TODO §113 (D-MAILROOM-REPLY-WORKFLOW) — workflow « réponse » du type édité. */
const props = defineProps<{
  typeCode: string
  /** Types de dossier proposés comme type enfant. */
  childTypeCodes: string[]
}>()

const { t } = useI18n()
const mode = ref<'' | ReplyWorkflowMode>('')
const workflowKey = ref('')
const childTypeCode = ref('')
const workflowKeys = ref<string[]>([])
const error = ref<string | null>(null)
const saved = ref(false)

async function load() {
  error.value = null
  saved.value = false
  try {
    const [current, configs] = await Promise.all([getReplyWorkflow(props.typeCode), listAllWorkflowTypeConfigs()])
    workflowKeys.value = configs.map((config) => config.workflowKey).sort()
    mode.value = current?.mode ?? ''
    workflowKey.value = current?.workflowKey ?? ''
    childTypeCode.value = current?.childTypeCode ?? ''
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.types.reply.errors.load')
  }
}

onMounted(load)
watch(() => props.typeCode, load)

async function save() {
  error.value = null
  saved.value = false
  try {
    if (mode.value === '') {
      await clearReplyWorkflow(props.typeCode)
    } else {
      await setReplyWorkflow(props.typeCode, {
        mode: mode.value,
        workflowKey: workflowKey.value,
        childTypeCode: mode.value === 'DOSSIER_ENFANT' ? childTypeCode.value || null : null,
      })
    }
    saved.value = true
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.types.reply.errors.save')
  }
}
</script>

<template>
  <fieldset class="reply-workflow" data-testid="reply-workflow-panel">
    <legend>{{ t('admin.types.reply.legend') }}</legend>
    <p class="hint">{{ t('admin.types.reply.hint') }}</p>
    <p v-if="error" class="error" role="alert" data-testid="reply-workflow-error">{{ error }}</p>
    <label>
      {{ t('admin.types.reply.mode') }}
      <select v-model="mode" data-testid="reply-workflow-mode">
        <option value="">{{ t('admin.types.reply.none') }}</option>
        <option value="SUR_DOSSIER">{{ t('admin.types.reply.sameDossier') }}</option>
        <option value="DOSSIER_ENFANT">{{ t('admin.types.reply.childDossier') }}</option>
      </select>
    </label>
    <label v-if="mode">
      {{ t('admin.types.reply.workflow') }}
      <select v-model="workflowKey" data-testid="reply-workflow-key">
        <option value="" disabled>{{ t('admin.types.reply.choose') }}</option>
        <option v-for="key in workflowKeys" :key="key" :value="key">{{ key }}</option>
      </select>
    </label>
    <label v-if="mode === 'DOSSIER_ENFANT'">
      {{ t('admin.types.reply.childType') }}
      <select v-model="childTypeCode" data-testid="reply-workflow-child-type">
        <option value="" disabled>{{ t('admin.types.reply.choose') }}</option>
        <option v-for="code in childTypeCodes" :key="code" :value="code">{{ code }}</option>
      </select>
    </label>
    <button type="button" data-testid="reply-workflow-save" @click="save">{{ t('admin.types.reply.save') }}</button>
    <span v-if="saved" class="hint" data-testid="reply-workflow-saved">{{ t('admin.types.reply.saved') }}</span>
  </fieldset>
</template>

<style scoped>
.reply-workflow {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  align-items: center;
}
.reply-workflow legend {
  font-weight: 600;
}
.hint {
  font-size: 0.85rem;
  opacity: 0.75;
  width: 100%;
}
.error {
  color: #c92a2a;
  width: 100%;
}
</style>
