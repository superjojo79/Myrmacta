<script setup lang="ts">
import { computed, onMounted, ref, watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import LocaleSwitcher from '@/i18n/LocaleSwitcher.vue'
import { formatDate } from '@/i18n/format'
import PortalQuestionForm from './PortalQuestionForm.vue'
import PortalSignature from './PortalSignature.vue'
import {
  fetchTask,
  navigateTo,
  PortalApiError,
  startIdentity,
  submitAnswers,
  uploadAttachment,
  type PortalAttachment,
  type PortalCredential,
  type PortalTask,
} from './api'

// TODO §68 — états de la page publique. Tout refus serveur (404) produit le même écran
// (US-3) : la page ne cherche jamais à distinguer lien expiré, révoqué ou inconnu.
// TODO §104 — `identity-required` : tâche à authentification forte, seul le choix du fournisseur est montré (T3b) ;
// `identity-refused` : retour du fournisseur refusé, écran neutre (T10).
type State =
  | 'no-token'
  | 'loading'
  | 'denied'
  | 'ready'
  | 'done'
  | 'unavailable'
  | 'identity-required'
  | 'identity-refused'

const props = withDefaults(
  defineProps<{ token: string | null; kind?: 'task' | 'signature' | 'identity' | 'identity-refused' }>(),
  { kind: 'task' },
)

const { t } = useI18n()
watchEffect(() => {
  document.title = t('portal.pageTitle')
})
const state = ref<State>(props.kind === 'identity-refused' ? 'identity-refused' : props.token ? 'loading' : 'no-token')
const isSignature = computed(() => props.kind === 'signature' && !!props.token)
/** D-BEAUTH-PROOF — après le retour du fournisseur, la preuve d'identité remplace le jeton du lien. */
const credential = computed<PortalCredential | null>(() =>
  !props.token ? null : props.kind === 'identity' ? { proof: props.token } : props.token,
)
const redirecting = ref(false)
const identityError = ref('')
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
  if (!credential.value || isSignature.value) return
  try {
    task.value = await fetchTask(credential.value)
    attachments.value = task.value.attachments
    state.value = task.value.identity && !task.value.identity.verified ? 'identity-required' : 'ready'
  } catch (error) {
    handleError(error)
    if (state.value === 'loading') state.value = 'unavailable'
  }
})

async function onIdentify(provider: string) {
  if (!props.token || props.kind !== 'task') return
  redirecting.value = true
  identityError.value = ''
  try {
    navigateTo(await startIdentity(props.token, provider))
  } catch (error) {
    identityError.value = handleError(error)
    redirecting.value = false
  }
}

async function onUpload() {
  const file = fileInput.value?.files?.[0]
  if (!credential.value || !file) {
    uploadError.value = t('portal.chooseFile')
    return
  }
  uploading.value = true
  uploadError.value = ''
  try {
    attachments.value = [...attachments.value, await uploadAttachment(credential.value, file, uploadLabel.value)]
    uploadLabel.value = ''
    if (fileInput.value) fileInput.value.value = ''
  } catch (error) {
    uploadError.value = handleError(error)
  } finally {
    uploading.value = false
  }
}

async function onSubmit() {
  if (!credential.value) return
  submitting.value = true
  formError.value = ''
  try {
    await submitAnswers(credential.value, answers.value)
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

    <section v-else-if="state === 'identity-refused'" class="portal__card" data-testid="portal-identity-refused">
      <h1>{{ t('portal.identity.refused.title') }}</h1>
      <p>{{ t('portal.identity.refused.body') }}</p>
    </section>

    <section v-else-if="state === 'identity-required' && task" class="portal__card" data-testid="portal-identity-required">
      <h1>{{ task.taskName || t('portal.defaultTaskName') }}</h1>
      <p>{{ t('portal.identity.body') }}</p>
      <div class="portal__providers">
        <button
          v-for="provider in task.identity?.providers ?? []"
          :key="provider.id"
          type="button"
          class="portal__primary"
          :data-testid="`portal-identity-${provider.id}`"
          :disabled="redirecting"
          @click="onIdentify(provider.id)"
        >
          {{ provider.label }}
        </button>
      </div>
      <p v-if="redirecting" class="portal__muted" aria-live="polite">{{ t('portal.identity.redirecting') }}</p>
      <p v-if="identityError" class="portal__error" role="alert">{{ identityError }}</p>
    </section>

    <section v-else-if="state === 'done'" class="portal__card" data-testid="portal-done" aria-live="polite">
      <h1>{{ t('portal.done.title') }}</h1>
      <p>{{ t('portal.done.body') }}</p>
    </section>

    <template v-else-if="task">
      <section class="portal__card">
        <h1>{{ task.taskName || t('portal.defaultTaskName') }}</h1>
        <p v-if="task.identity?.verified" class="portal__verified" data-testid="portal-identity-verified">
          {{ task.identity.name ? t('portal.identity.verifiedAs', { name: task.identity.name }) : t('portal.identity.verified') }}
        </p>
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
