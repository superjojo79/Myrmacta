<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PortalQuestionForm from './PortalQuestionForm.vue'
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

const props = defineProps<{ token: string | null }>()

const state = ref<State>(props.token ? 'loading' : 'no-token')
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
  task.value
    ? new Date(task.value.expiresAt).toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' })
    : '',
)

function handleError(error: unknown): string {
  if (error instanceof PortalApiError) {
    if (error.status === 404) {
      state.value = 'denied'
      return ''
    }
    if (error.status === 429) {
      return 'Trop de tentatives. Patientez une minute puis réessayez.'
    }
    if (error.status === 413) {
      return 'Le fichier dépasse la taille autorisée.'
    }
    return error.detail || 'La demande n’a pas pu être traitée.'
  }
  state.value = 'unavailable'
  return ''
}

onMounted(async () => {
  if (!props.token) return
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
    uploadError.value = 'Choisissez un fichier.'
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
      <span v-if="task" class="portal__reference">Dossier {{ task.dossierReference }}</span>
    </header>

    <section v-if="state === 'loading'" class="portal__card" aria-live="polite">
      <p>Chargement de votre demande…</p>
    </section>

    <section v-else-if="state === 'no-token'" class="portal__card" data-testid="portal-no-token">
      <h1>Ouvrez le lien reçu par e-mail</h1>
      <p>
        Pour votre sécurité, le lien d’accès n’est pas conservé par cette page. Rouvrez-le depuis l’e-mail
        que vous avez reçu.
      </p>
    </section>

    <section v-else-if="state === 'denied'" class="portal__card" data-testid="portal-denied">
      <h1>Lien invalide ou expiré</h1>
      <p>Ce lien n’est pas ou plus valide. Contactez la personne qui vous l’a envoyé.</p>
    </section>

    <section v-else-if="state === 'unavailable'" class="portal__card" data-testid="portal-unavailable">
      <h1>Service momentanément indisponible</h1>
      <p>Réessayez dans quelques instants en rouvrant le lien reçu par e-mail.</p>
    </section>

    <section v-else-if="state === 'done'" class="portal__card" data-testid="portal-done" aria-live="polite">
      <h1>Merci, votre réponse a bien été envoyée</h1>
      <p>Vous pouvez fermer cette page. Ce lien n’est plus utilisable.</p>
    </section>

    <template v-else-if="task">
      <section class="portal__card">
        <h1>{{ task.taskName || 'Demande d’informations' }}</h1>
        <p v-if="task.instructions" class="portal__instructions">{{ task.instructions }}</p>
        <p class="portal__expiry">Lien valable jusqu’au {{ expiresOn }}.</p>
      </section>

      <section class="portal__card">
        <h2>{{ task.form.label }}</h2>
        <PortalQuestionForm v-model="answers" :fields="task.form.fields" :disabled="submitting" />
      </section>

      <section class="portal__card">
        <h2>Pièces jointes</h2>
        <ul v-if="attachments.length" class="portal__attachments" data-testid="portal-attachments">
          <li v-for="attachment in attachments" :key="attachment.id">
            {{ attachment.label }} <span class="portal__muted">({{ attachment.filename }})</span>
          </li>
        </ul>
        <p v-else class="portal__muted">Aucune pièce déposée pour l’instant.</p>
        <div class="portal__upload">
          <label for="portal-file">Fichier</label>
          <input id="portal-file" ref="fileInput" type="file" :disabled="uploading || submitting" />
          <label for="portal-file-label">Intitulé (facultatif)</label>
          <input id="portal-file-label" v-model="uploadLabel" type="text" maxlength="200" :disabled="uploading" />
          <button type="button" class="portal__secondary" :disabled="uploading || submitting" @click="onUpload">
            {{ uploading ? 'Envoi…' : 'Déposer le fichier' }}
          </button>
          <p v-if="uploadError" class="portal__error" role="alert">{{ uploadError }}</p>
        </div>
      </section>

      <section class="portal__actions">
        <p v-if="formError" class="portal__error" role="alert" data-testid="portal-form-error">{{ formError }}</p>
        <button type="button" class="portal__primary" :disabled="submitting" @click="onSubmit">
          {{ submitting ? 'Envoi en cours…' : 'Envoyer ma réponse' }}
        </button>
      </section>
    </template>
  </main>
</template>
