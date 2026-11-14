<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDate } from '@/i18n/format'
import { rejectionMessageKey, signedDocumentFilename } from '@/lib/signedUpload'
import {
  declineSignature,
  fetchSignatureContext,
  fetchSignatureDocument,
  navigateTo,
  PortalApiError,
  signDocument,
  uploadSignedDocument,
  type SignatureContext,
} from './api'

// TODO §72 — écran signataire externe. Tout refus serveur (404) donne le même écran neutre : la page
// ne distingue jamais lien invalide, expiré, révoqué ou demande close.
type State = 'loading' | 'denied' | 'ready' | 'signed' | 'declined' | 'unavailable'

const props = defineProps<{ token: string }>()

const { t } = useI18n()
const state = ref<State>('loading')
const context = ref<SignatureContext | null>(null)
const documentUrl = ref<string | null>(null)
const busy = ref(false)
const declining = ref(false)
const reason = ref('')
const error = ref('')
// Issue #10 — signature apportée : le PDF signé hors de la page, choisi puis envoyé.
const fileInput = ref<HTMLInputElement | null>(null)
const chosenFile = ref<File | null>(null)

const deadline = computed(() =>
  context.value?.deadlineAt ? formatDate(context.value.deadlineAt, { day: 'numeric', month: 'long', year: 'numeric' }) : '',
)
const hosted = computed(() => context.value?.hosted === true)
const signedUpload = computed(() => context.value?.signedUpload === true)
const downloadName = computed(() => signedDocumentFilename(context.value?.documentLabel ?? ''))
const awaitingLink = computed(() => hosted.value && !context.value?.signingUrl)

function handleError(e: unknown): string {
  if (e instanceof PortalApiError) {
    if (e.status === 404) {
      state.value = 'denied'
      return ''
    }
    if (e.status === 429) return t('portal.errors.tooMany')
    if (e.status === 413) return t('portal.errors.tooLarge')
    return e.detail || t('portal.errors.generic')
  }
  state.value = 'unavailable'
  return ''
}

onMounted(async () => {
  try {
    context.value = await fetchSignatureContext(props.token)
    state.value = 'ready'
    if (context.value.hosted && context.value.signingUrl) {
      navigateTo(context.value.signingUrl)
      return
    }
    documentUrl.value = URL.createObjectURL(await fetchSignatureDocument(props.token))
  } catch (e) {
    handleError(e)
    if (state.value === 'loading') state.value = 'unavailable'
  }
})

onBeforeUnmount(() => {
  if (documentUrl.value) URL.revokeObjectURL(documentUrl.value)
})

async function onSign() {
  busy.value = true
  error.value = ''
  try {
    await signDocument(props.token)
    state.value = 'signed'
  } catch (e) {
    error.value = handleError(e)
  } finally {
    busy.value = false
  }
}

function onFileChange(event: Event) {
  chosenFile.value = (event.target as HTMLInputElement).files?.[0] ?? null
}

/** Remplace le document de travail (nouveau lien de téléchargement) : l'ancien lien est libéré. */
async function loadDocument() {
  const previous = documentUrl.value
  documentUrl.value = URL.createObjectURL(await fetchSignatureDocument(props.token))
  if (previous) URL.revokeObjectURL(previous)
}

async function onUpload() {
  const file = chosenFile.value
  if (!file) return
  busy.value = true
  error.value = ''
  try {
    await uploadSignedDocument(props.token, file)
    state.value = 'signed'
  } catch (e) {
    if (e instanceof PortalApiError && e.status === 422) {
      error.value = t(rejectionMessageKey('portal.signature.upload', e.code))
      if (e.code === 'STALE_DOCUMENT') await refreshAfterStale()
    } else {
      error.value = handleError(e)
    }
  } finally {
    busy.value = false
  }
}

/** Le document a changé entre-temps : le fichier choisi ne vaut plus, le nouveau document est rechargé. */
async function refreshAfterStale() {
  chosenFile.value = null
  if (fileInput.value) fileInput.value.value = ''
  try {
    await loadDocument()
  } catch (e) {
    const message = handleError(e)
    if (message) error.value = message
  }
}

async function onDecline() {
  busy.value = true
  error.value = ''
  try {
    await declineSignature(props.token, reason.value)
    state.value = 'declined'
  } catch (e) {
    error.value = handleError(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section v-if="state === 'loading'" class="portal__card" aria-live="polite">
    <p>{{ t('portal.signature.loading') }}</p>
  </section>

  <section v-else-if="state === 'denied'" class="portal__card" data-testid="portal-denied">
    <h1>{{ t('portal.denied.title') }}</h1>
    <p>{{ t('portal.denied.body') }}</p>
  </section>

  <section v-else-if="state === 'unavailable'" class="portal__card" data-testid="portal-unavailable">
    <h1>{{ t('portal.unavailable.title') }}</h1>
    <p>{{ t('portal.unavailable.body') }}</p>
  </section>

  <section v-else-if="state === 'signed'" class="portal__card" data-testid="signature-signed" aria-live="polite">
    <h1>{{ t('portal.signature.signed.title') }}</h1>
    <p>{{ t('portal.done.body') }}</p>
  </section>

  <section v-else-if="state === 'declined'" class="portal__card" data-testid="signature-declined" aria-live="polite">
    <h1>{{ t('portal.signature.declined.title') }}</h1>
    <p>{{ t('portal.done.body') }}</p>
  </section>

  <template v-else-if="context">
    <p v-if="context.simulated" class="portal__banner" role="status" data-testid="signature-simulation">
      {{ t('portal.signature.simulation') }}
    </p>
    <section class="portal__card">
      <h1>{{ t('portal.signature.title', { document: context.documentLabel }) }}</h1>
      <p>{{ t('portal.signature.intro', { reference: context.dossierReference, name: context.signerName }) }}</p>
      <p v-if="deadline" class="portal__expiry">{{ t('portal.signature.deadline', { date: deadline }) }}</p>
      <p v-if="!context.required" class="portal__muted">{{ t('portal.signature.optional') }}</p>
      <p v-if="awaitingLink" data-testid="signature-waiting">{{ t('portal.signature.waiting') }}</p>
      <p v-if="!signedUpload">
        <a v-if="documentUrl" :href="documentUrl" target="_blank" rel="noopener" data-testid="signature-document">
          {{ t('portal.signature.view') }}
        </a>
        <span v-else class="portal__muted">{{ t('portal.signature.documentLoading') }}</span>
      </p>
    </section>

    <section v-if="signedUpload && !declining" class="portal__card" data-testid="signature-upload" :aria-busy="busy">
      <h2>{{ t('portal.signature.upload.steps') }}</h2>
      <ol class="portal__steps">
        <li>
          <a v-if="documentUrl" :href="documentUrl" :download="downloadName" data-testid="signature-download">
            {{ t('portal.signature.upload.download') }}
          </a>
          <span v-else class="portal__muted">{{ t('portal.signature.documentLoading') }}</span>
        </li>
        <li>{{ t('portal.signature.upload.signStep') }}</li>
        <li>
          <p>{{ t('portal.signature.upload.sendStep') }}</p>
          <label for="signature-file">{{ t('portal.signature.upload.file') }}</label>
          <input
            id="signature-file"
            ref="fileInput"
            type="file"
            accept="application/pdf,.pdf"
            data-testid="signature-file"
            :disabled="busy"
            @change="onFileChange"
          />
          <button
            type="button"
            class="portal__primary"
            data-testid="signature-send"
            :disabled="busy || !chosenFile"
            @click="onUpload"
          >
            {{ busy ? t('portal.signature.upload.sending') : t('portal.signature.upload.send') }}
          </button>
        </li>
      </ol>
    </section>

    <section v-if="declining" class="portal__card">
      <label for="signature-reason">{{ t('portal.signature.reason') }}</label>
      <textarea id="signature-reason" v-model="reason" maxlength="500" rows="3" :disabled="busy" />
    </section>

    <section class="portal__actions">
      <p v-if="error" class="portal__error" role="alert" data-testid="signature-error">{{ error }}</p>
      <template v-if="!declining">
        <button v-if="!hosted && !signedUpload" type="button" class="portal__primary" :disabled="busy" @click="onSign">
          {{ busy ? t('portal.submitting') : t('portal.signature.sign') }}
        </button>
        <button type="button" class="portal__secondary" :disabled="busy" @click="declining = true">{{ t('portal.signature.decline') }}</button>
      </template>
      <template v-else>
        <button type="button" class="portal__primary" :disabled="busy" @click="onDecline">{{ t('portal.signature.confirmDecline') }}</button>
        <button type="button" class="portal__secondary" :disabled="busy" @click="declining = false">{{ t('portal.signature.cancel') }}</button>
      </template>
    </section>
  </template>
</template>
