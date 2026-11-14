<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDate } from '@/i18n/format'
import CardChoiceForm from '@/components/CardChoiceForm.vue'
import { CardBridgeError } from '@/lib/cardBridge'
import { cardErrorMessage, noBridgeMessage, useCardSignature } from '@/lib/cardBridge/useCardSignature'
import { rejectionMessageKey, signedDocumentFilename } from '@/lib/signedUpload'
import {
  completeCardSignature,
  declineSignature,
  fetchSignatureContext,
  fetchSignatureDocument,
  navigateTo,
  PortalApiError,
  prepareCardSignature,
  signDocument,
  uploadSignedDocument,
  type SignatureContext,
} from './api'

// TODO §72 — écran signataire externe. Tout refus serveur (404) donne le même écran neutre : la page
// ne distingue jamais lien invalide, expiré, révoqué ou demande close.
type State = 'loading' | 'denied' | 'ready' | 'signed' | 'declined' | 'unavailable'

const props = defineProps<{ token: string }>()

const { t, locale } = useI18n()
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
// Lot 3b — signature dans la page par un pont de carte ; vide : le comportement du lot 3 (téléversement) ou du clic.
const cardAvailable = computed(() => (context.value?.cardBridges.length ?? 0) > 0)
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

/** Message d'un refus du serveur (téléversement ou signature dans la page) ; un 404 bascule sur l'écran neutre. */
async function describeApiError(e: unknown): Promise<string> {
  if (e instanceof PortalApiError && e.status === 422) {
    if (e.code === 'STALE_DOCUMENT') await refreshAfterStale()
    return t(rejectionMessageKey('portal.signature.upload', e.code))
  }
  return handleError(e)
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
    error.value = await describeApiError(e)
  } finally {
    busy.value = false
  }
}

function bridgeMessage(message: { key: string; named?: { count: number } }): string {
  return t(message.key, message.named ?? {})
}

const card = useCardSignature({
  bridges: () => context.value?.cardBridges ?? [],
  lang: () => locale.value,
  prepare: (request) => prepareCardSignature(props.token, request),
  complete: (request) => completeCardSignature(props.token, request),
  onSigned: () => {
    state.value = 'signed'
  },
  onError: async (e) => {
    error.value = e instanceof CardBridgeError ? bridgeMessage(cardErrorMessage('portal.signature.card', e)) : await describeApiError(e)
  },
  onNoBridge: (e) => {
    error.value = bridgeMessage(noBridgeMessage('portal.signature.card', e))
  },
})
const { phase: cardPhase, pin: cardPin, pinValid: cardPinValid, active: cardActive, bridgeId: cardBridgeId, choices: cardChoices, selectedChoice: cardSelected } = card
const pinInput = ref<HTMLInputElement | null>(null)
const cardStatus = computed(() => {
  if (cardPhase.value === 'idle' || cardPhase.value === 'pin' || cardPhase.value === 'choosing') return ''
  // Web eID : son logiciel natif affiche le choix du certificat, la page le dit.
  const key = cardPhase.value === 'reading' && cardBridgeId.value === 'web-eid' ? 'readingWebEid' : cardPhase.value
  return t(`portal.signature.card.phases.${key}`)
})

async function onCardSign() {
  error.value = ''
  await card.start()
}

watch(cardPhase, async (value) => {
  if (value === 'pin') {
    await nextTick()
    pinInput.value?.focus()
  }
})

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
      <p v-if="!signedUpload || cardAvailable">
        <a v-if="documentUrl" :href="documentUrl" target="_blank" rel="noopener" data-testid="signature-document">
          {{ t('portal.signature.view') }}
        </a>
        <span v-else class="portal__muted">{{ t('portal.signature.documentLoading') }}</span>
      </p>
    </section>

    <section v-if="cardAvailable && !declining" class="portal__card" data-testid="signature-card" :aria-busy="cardActive">
      <h2>{{ t('portal.signature.card.title') }}</h2>
      <p>{{ t('portal.signature.card.intro') }}</p>
      <button
        type="button"
        class="portal__primary"
        data-testid="signature-card-sign"
        :disabled="busy || cardActive"
        @click="onCardSign"
      >
        {{ t('portal.signature.card.sign') }}
      </button>
      <p class="portal__muted" role="status" aria-live="polite" data-testid="signature-card-status">{{ cardStatus }}</p>
      <CardChoiceForm
        v-if="cardPhase === 'choosing'"
        v-model="cardSelected"
        :choices="cardChoices"
        prefix="portal.signature.card"
        id-base="portal-signature"
        portal
        @submit="card.submitChoice()"
        @cancel="card.cancelChoice()"
      />
      <form v-if="cardPhase === 'pin'" data-testid="signature-pin-form" @submit.prevent="card.submitPin()">
        <label for="signature-pin">{{ t('portal.signature.card.pin.label') }}</label>
        <input
          id="signature-pin"
          ref="pinInput"
          v-model="cardPin"
          type="password"
          autocomplete="off"
          minlength="4"
          maxlength="12"
          data-testid="signature-pin"
        />
        <p class="portal__muted">{{ t('portal.signature.card.pin.hint') }}</p>
        <button type="submit" class="portal__primary" data-testid="signature-pin-submit" :disabled="!cardPinValid">
          {{ t('portal.signature.card.pin.submit') }}
        </button>
        <button type="button" class="portal__secondary" data-testid="signature-pin-cancel" @click="card.cancelPin()">
          {{ t('portal.signature.card.pin.cancel') }}
        </button>
      </form>
    </section>

    <section v-if="signedUpload && !declining" class="portal__card" data-testid="signature-upload" :aria-busy="busy">
      <h2>{{ cardAvailable ? t('portal.signature.upload.stepsAlt') : t('portal.signature.upload.steps') }}</h2>
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
            :disabled="busy || cardActive"
            @change="onFileChange"
          />
          <button
            type="button"
            class="portal__primary"
            data-testid="signature-send"
            :disabled="busy || cardActive || !chosenFile"
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
        <button v-if="!hosted && !signedUpload && !cardAvailable" type="button" class="portal__primary" :disabled="busy" @click="onSign">
          {{ busy ? t('portal.submitting') : t('portal.signature.sign') }}
        </button>
        <button type="button" class="portal__secondary" :disabled="busy || cardActive" @click="declining = true">{{ t('portal.signature.decline') }}</button>
      </template>
      <template v-else>
        <button type="button" class="portal__primary" :disabled="busy" @click="onDecline">{{ t('portal.signature.confirmDecline') }}</button>
        <button type="button" class="portal__secondary" :disabled="busy" @click="declining = false">{{ t('portal.signature.cancel') }}</button>
      </template>
    </section>
  </template>
</template>
