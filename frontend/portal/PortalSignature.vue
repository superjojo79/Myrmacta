<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDate } from '@/i18n/format'
import {
  declineSignature,
  fetchSignatureContext,
  fetchSignatureDocument,
  PortalApiError,
  signDocument,
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

const deadline = computed(() =>
  context.value?.deadlineAt ? formatDate(context.value.deadlineAt, { day: 'numeric', month: 'long', year: 'numeric' }) : '',
)

function handleError(e: unknown): string {
  if (e instanceof PortalApiError) {
    if (e.status === 404) {
      state.value = 'denied'
      return ''
    }
    if (e.status === 429) return t('portal.errors.tooMany')
    return e.detail || t('portal.errors.generic')
  }
  state.value = 'unavailable'
  return ''
}

onMounted(async () => {
  try {
    context.value = await fetchSignatureContext(props.token)
    state.value = 'ready'
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
      <p>
        <a v-if="documentUrl" :href="documentUrl" target="_blank" rel="noopener" data-testid="signature-document">
          {{ t('portal.signature.view') }}
        </a>
        <span v-else class="portal__muted">{{ t('portal.signature.documentLoading') }}</span>
      </p>
    </section>

    <section v-if="declining" class="portal__card">
      <label for="signature-reason">{{ t('portal.signature.reason') }}</label>
      <textarea id="signature-reason" v-model="reason" maxlength="500" rows="3" :disabled="busy" />
    </section>

    <section class="portal__actions">
      <p v-if="error" class="portal__error" role="alert" data-testid="signature-error">{{ error }}</p>
      <template v-if="!declining">
        <button type="button" class="portal__primary" :disabled="busy" @click="onSign">
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
