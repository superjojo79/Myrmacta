<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
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

const state = ref<State>('loading')
const context = ref<SignatureContext | null>(null)
const documentUrl = ref<string | null>(null)
const busy = ref(false)
const declining = ref(false)
const reason = ref('')
const error = ref('')

const deadline = computed(() =>
  context.value?.deadlineAt
    ? new Date(context.value.deadlineAt).toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' })
    : '',
)

function handleError(e: unknown): string {
  if (e instanceof PortalApiError) {
    if (e.status === 404) {
      state.value = 'denied'
      return ''
    }
    if (e.status === 429) return 'Trop de tentatives. Patientez une minute puis réessayez.'
    return e.detail || 'La demande n’a pas pu être traitée.'
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
    <p>Chargement du document à signer…</p>
  </section>

  <section v-else-if="state === 'denied'" class="portal__card" data-testid="portal-denied">
    <h1>Lien invalide ou expiré</h1>
    <p>Ce lien n’est pas ou plus valide. Contactez la personne qui vous l’a envoyé.</p>
  </section>

  <section v-else-if="state === 'unavailable'" class="portal__card" data-testid="portal-unavailable">
    <h1>Service momentanément indisponible</h1>
    <p>Réessayez dans quelques instants en rouvrant le lien reçu par e-mail.</p>
  </section>

  <section v-else-if="state === 'signed'" class="portal__card" data-testid="signature-signed" aria-live="polite">
    <h1>Merci, votre signature a bien été enregistrée</h1>
    <p>Vous pouvez fermer cette page. Ce lien n’est plus utilisable.</p>
  </section>

  <section v-else-if="state === 'declined'" class="portal__card" data-testid="signature-declined" aria-live="polite">
    <h1>Votre refus a bien été enregistré</h1>
    <p>Vous pouvez fermer cette page. Ce lien n’est plus utilisable.</p>
  </section>

  <template v-else-if="context">
    <p v-if="context.simulated" class="portal__banner" role="status" data-testid="signature-simulation">
      SIMULATION — sans valeur juridique
    </p>
    <section class="portal__card">
      <h1>Signature de « {{ context.documentLabel }} »</h1>
      <p>Dossier {{ context.dossierReference }} — bonjour {{ context.signerName }}.</p>
      <p v-if="deadline" class="portal__expiry">À signer avant le {{ deadline }}.</p>
      <p v-if="!context.required" class="portal__muted">Votre signature est facultative.</p>
      <p>
        <a v-if="documentUrl" :href="documentUrl" target="_blank" rel="noopener" data-testid="signature-document">
          Consulter le document
        </a>
        <span v-else class="portal__muted">Document en cours de chargement…</span>
      </p>
    </section>

    <section v-if="declining" class="portal__card">
      <label for="signature-reason">Motif du refus (facultatif)</label>
      <textarea id="signature-reason" v-model="reason" maxlength="500" rows="3" :disabled="busy" />
    </section>

    <section class="portal__actions">
      <p v-if="error" class="portal__error" role="alert" data-testid="signature-error">{{ error }}</p>
      <template v-if="!declining">
        <button type="button" class="portal__primary" :disabled="busy" @click="onSign">
          {{ busy ? 'Envoi en cours…' : 'Signer' }}
        </button>
        <button type="button" class="portal__secondary" :disabled="busy" @click="declining = true">Refuser</button>
      </template>
      <template v-else>
        <button type="button" class="portal__primary" :disabled="busy" @click="onDecline">Confirmer le refus</button>
        <button type="button" class="portal__secondary" :disabled="busy" @click="declining = false">Annuler</button>
      </template>
    </section>
  </template>
</template>
