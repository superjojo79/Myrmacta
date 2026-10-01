<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import LocaleSwitcher from '@/i18n/LocaleSwitcher.vue'
import { formatDate } from '@/i18n/format'
import PortalQuestionForm from './PortalQuestionForm.vue'
import PortalSignature from './PortalSignature.vue'
import {
  fetchTask,
  PortalApiError,
  submitAnswers,
  uploadAttachment,
  type PortalAttachment,
  type PortalTask,
} from './api'

// TODO §68 — états de la page publique. Tout refus serveur (404) produit le même écran
// (US-3) : la page ne cherche jamais à distinguer lien expiré, révoqué ou inconnu.
type State = 'no-token' | 'loading' | 'denied' | 'ready' | 'done' | 'unavailable'

const props = withDefaults(defineProps<{ token: string | null; kind?: 'task' | 'signature' }>(), { kind: 'task' })

const { t } = useI18n()
const state = ref<State>(props.token ? 'loading' : 'no-token')
const isSignature = computed(() => props.kind === 'signature' && !!props.token)
const task = ref<PortalTask | null>(null)
const answers = ref<Record<string, unknown>>({})
const attachments = ref<PortalAttachment[]>([])
const submitting = ref(false)
const uploading = ref(false)
const formError = ref('')
const uploadError = ref('')
const uploadLabel = ref('')
const fileInput = ref<HTMLInputElement | null>(null)

const expiresOn = computed(() =>
  task.value ? formatDate(task.value.expiresAt, { day: 'numeric', month: 'long', year: 'numeric' }) : '',
)

function handleError(error: unknown): string {
  if (error instanceof PortalApiError) {
    if (error.status === 404) {
      state.value = 'denied'
      return ''
    }
    if (error.status === 429) {
      return t('portal.errors.tooMany')
    }
    if (error.status === 413) {
      return t('portal.errors.tooLarge')
    }
    return error.detail || t('portal.errors.generic')
  }
  state.value = 'unavailable'
  return ''
}

onMounted(async () => {
  if (!props.token || isSignature.value) return
  try {
    task.value = await fetchTask(props.token)
    attachments.value = task.value.attachments
    state.value = 'ready'
  } catch (error) {
    handleError(error)
    if (state.value === 'loading') state.value = 'unavailable'
  }
})

async function onUpload() {
  const file = fileInput.value?.files?.[0]
  if (!props.token || !file) {
    uploadError.value = t('portal.chooseFile')
    return
  }
  uploading.value = true
  uploadError.value = ''
  try {
    attachments.value = [...attachments.value, await uploadAttachment(props.token, file, uploadLabel.value)]
    uploadLabel.value = ''
    if (fileInput.value) fileInput.value.value = ''
  } catch (error) {
    uploadError.value = handleError(error)
  } finally {
    uploading.value = false
  }
}

async function onSubmit() {
  if (!props.token) return
  submitting.value = true
  formError.value = ''
  try {
    await submitAnswers(props.token, answers.value)
    state.value = 'done'
  } catch (error) {
    formError.value = handleError(error)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="portal">
    <header class="portal__header">
      <span class="portal__brand">FlowForge</span>
      <span v-if="task" class="portal__reference">{{ t('portal.dossier', { reference: task.dossierReference }) }}</span>
      <LocaleSwitcher class="portal__locale" />
    </header>

    <PortalSignature v-if="isSignature" :token="props.token as string" />

    <section v-else-if="state === 'loading'" class="portal__card" aria-live="polite">
      <p>{{ t('portal.loadingRequest') }}</p>
    </section>

    <section v-else-if="state === 'no-token'" class="portal__card" data-testid="portal-no-token">
      <h1>{{ t('portal.noToken.title') }}</h1>
      <p>{{ t('portal.noToken.body') }}</p>
    </section>

    <section v-else-if="state === 'denied'" class="portal__card" data-testid="portal-denied">
      <h1>{{ t('portal.denied.title') }}</h1>
      <p>{{ t('portal.denied.body') }}</p>
    </section>

    <section v-else-if="state === 'unavailable'" class="portal__card" data-testid="portal-unavailable">
      <h1>{{ t('portal.unavailable.title') }}</h1>
      <p>{{ t('portal.unavailable.body') }}</p>
    </section>

    <section v-else-if="state === 'done'" class="portal__card" data-testid="portal-done" aria-live="polite">
      <h1>{{ t('portal.done.title') }}</h1>
      <p>{{ t('portal.done.body') }}</p>
    </section>

    <template v-else-if="task">
      <section class="portal__card">
        <h1>{{ task.taskName || t('portal.defaultTaskName') }}</h1>
        <p v-if="task.instructions" class="portal__instructions">{{ task.instructions }}</p>
        <p class="portal__expiry">{{ t('portal.expiry', { date: expiresOn }) }}</p>
      </section>

      <section class="portal__card">
        <h2>{{ task.form.label }}</h2>
        <PortalQuestionForm v-model="answers" :fields="task.form.fields" :disabled="submitting" />
      </section>

      <section class="portal__card">
        <h2>{{ t('portal.attachments') }}</h2>
        <ul v-if="attachments.length" class="portal__attachments" data-testid="portal-attachments">
          <li v-for="attachment in attachments" :key="attachment.id">
            {{ attachment.label }} <span class="portal__muted">({{ attachment.filename }})</span>
          </li>
        </ul>
        <p v-else class="portal__muted">{{ t('portal.noAttachment') }}</p>
        <div class="portal__upload">
          <label for="portal-file">{{ t('portal.file') }}</label>
          <input id="portal-file" ref="fileInput" type="file" :disabled="uploading || submitting" />
          <label for="portal-file-label">{{ t('portal.fileLabel') }}</label>
          <input id="portal-file-label" v-model="uploadLabel" type="text" maxlength="200" :disabled="uploading" />
          <button type="button" class="portal__secondary" :disabled="uploading || submitting" @click="onUpload">
            {{ uploading ? t('portal.uploading') : t('portal.upload') }}
          </button>
          <p v-if="uploadError" class="portal__error" role="alert">{{ uploadError }}</p>
        </div>
      </section>

      <section class="portal__actions">
        <p v-if="formError" class="portal__error" role="alert" data-testid="portal-form-error">{{ formError }}</p>
        <button type="button" class="portal__primary" :disabled="submitting" @click="onSubmit">
          {{ submitting ? t('portal.submitting') : t('portal.submit') }}
        </button>
      </section>
    </template>
  </main>
</template>
