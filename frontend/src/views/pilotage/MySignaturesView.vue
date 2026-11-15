<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { toProblemDetail } from '@/api/http'
import { formatDate as formatLocalDate } from '@/i18n/format'
import {
  declineMine,
  fetchMySignatureDocument,
  completeMyCardSignature,
  fetchMySigningUrl,
  listMySignatures,
  openSigningPage,
  prepareMyCardSignature,
  signMine,
  uploadMySignedDocument,
  type MySignatureItem,
} from '@/api/signatures'
import CardChoiceForm from '@/components/CardChoiceForm.vue'
import { CardBridgeError } from '@/lib/cardBridge'
import { cardErrorMessage, noBridgeMessage, useCardSignature } from '@/lib/cardBridge/useCardSignature'
import { rejectionMessageKey, saveBlob, signedDocumentFilename } from '@/lib/signedUpload'

// TODO §72 US-10 — signatures internes qui me sont adressées. « À mon tour » (actives) puis
// « En attente » (séquentiel : un signataire précédent doit d'abord décider).
const { t, locale } = useI18n()
const items = ref<MySignatureItem[]>([])
const loading = ref(true)
const unavailable = ref(false)
const busyId = ref<string | null>(null)
const decliningId = ref<string | null>(null)
const linkMissingId = ref<string | null>(null)
const reason = ref('')
const error = ref('')
// Issue #10 — signature apportée : PDF signé choisi par signataire, résultat de l'envoi, erreur propre à la ligne.
const chosenFiles = ref<Record<string, File | undefined>>({})
const uploadingId = ref<string | null>(null)
const uploadErrorId = ref<string | null>(null)
const uploadError = ref('')
const uploadDone = ref(false)
// Change à chaque fichier abandonné : l'élément `input` est recréé, il n'affiche plus le fichier écarté.
const inputVersion = ref(0)

const myTurn = () => items.value.filter((i) => i.myTurn)
const waiting = () => items.value.filter((i) => !i.myTurn)

function formatDate(value: string | null): string {
  return formatLocalDate(value, { dateStyle: 'medium' })
}

async function load() {
  try {
    items.value = await listMySignatures()
  } catch {
    items.value = []
    unavailable.value = true
  } finally {
    loading.value = false
  }
}

async function openDocument(item: MySignatureItem) {
  error.value = ''
  try {
    const url = URL.createObjectURL(await fetchMySignatureDocument(item.signerId))
    window.open(url, '_blank', 'noopener')
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch (e) {
    error.value = toProblemDetail(e).detail ?? t('mySignatures.errors.document')
  }
}

function onFileChange(item: MySignatureItem, event: Event) {
  chosenFiles.value = { ...chosenFiles.value, [item.signerId]: (event.target as HTMLInputElement).files?.[0] }
}

async function downloadDocument(item: MySignatureItem) {
  error.value = ''
  try {
    saveBlob(await fetchMySignatureDocument(item.signerId), signedDocumentFilename(item.documentLabel))
  } catch (e) {
    error.value = toProblemDetail(e).detail ?? t('mySignatures.errors.document')
  }
}

/** Message d'un refus du serveur (téléversement ou signature dans la page), propre à la ligne du signataire. */
async function reportUploadError(item: MySignatureItem, e: unknown) {
  uploadErrorId.value = item.signerId
  const problem = toProblemDetail(e)
  if (problem.status === 409 && problem.code === 'SIGNED_UPLOAD_DISABLED') {
    uploadError.value = t('mySignatures.upload.disabled')
  } else if (problem.status === 422) {
    uploadError.value = t(rejectionMessageKey('mySignatures.upload', problem.code))
  } else if (problem.status === 413) {
    uploadError.value = t('mySignatures.errors.tooLarge')
  } else {
    uploadError.value = problem.detail ?? t('mySignatures.errors.action')
  }
  if (problem.code === 'STALE_DOCUMENT') {
    // Le document a changé : le fichier choisi ne vaut plus, la liste est relue (le téléchargement relit le document).
    chosenFiles.value = { ...chosenFiles.value, [item.signerId]: undefined }
    inputVersion.value++
    await load()
  }
}

async function onUpload(item: MySignatureItem) {
  const file = chosenFiles.value[item.signerId]
  if (!file) return
  uploadingId.value = item.signerId
  uploadErrorId.value = null
  uploadError.value = ''
  uploadDone.value = false
  error.value = ''
  try {
    await uploadMySignedDocument(item.signerId, file)
    chosenFiles.value = { ...chosenFiles.value, [item.signerId]: undefined }
    inputVersion.value++
    uploadDone.value = true
    await load()
  } catch (e) {
    await reportUploadError(item, e)
  } finally {
    uploadingId.value = null
  }
}

function bridgeMessage(message: { key: string; named?: { count: number } }): string {
  return t(message.key, message.named ?? {})
}

// Lot 3b — signature dans la page par un pont de carte : une seule à la fois, pour la ligne `cardItem`.
const cardItem = ref<MySignatureItem | null>(null)
const card = useCardSignature({
  bridges: () => cardItem.value?.cardBridges ?? [],
  lang: () => locale.value,
  prepare: (request) => prepareMyCardSignature(cardItem.value!.signerId, request),
  complete: (request) => completeMyCardSignature(cardItem.value!.signerId, request),
  onSigned: async () => {
    uploadDone.value = true
    await load()
  },
  onError: async (e) => {
    const item = cardItem.value!
    if (e instanceof CardBridgeError) {
      uploadErrorId.value = item.signerId
      uploadError.value = bridgeMessage(cardErrorMessage('mySignatures.card', e, !item.signedUpload))
    } else {
      await reportUploadError(item, e)
    }
  },
  onNoBridge: (e) => {
    uploadErrorId.value = cardItem.value!.signerId
    uploadError.value = bridgeMessage(noBridgeMessage('mySignatures.card', e, !cardItem.value!.signedUpload))
  },
})
const { phase: cardPhase, pin: cardPin, pinValid: cardPinValid, active: cardActive, bridgeId: cardBridgeId, choices: cardChoices, selectedChoice: cardSelected } = card
const pinInput = ref<HTMLInputElement | HTMLInputElement[] | null>(null)
const cardStatus = computed(() => {
  if (cardPhase.value === 'idle' || cardPhase.value === 'pin' || cardPhase.value === 'choosing') return ''
  // Web eID : son logiciel natif affiche le choix du certificat, la page le dit.
  const key = cardPhase.value === 'reading' && cardBridgeId.value === 'web-eid' ? 'readingWebEid' : cardPhase.value
  return t(`mySignatures.card.phases.${key}`)
})

async function onCardSign(item: MySignatureItem) {
  cardItem.value = item
  uploadErrorId.value = null
  uploadError.value = ''
  uploadDone.value = false
  error.value = ''
  await card.start()
}

watch(cardPhase, async (value) => {
  if (value === 'pin') {
    await nextTick()
    const input = pinInput.value
    ;(Array.isArray(input) ? input[0] : input)?.focus()
  }
})

async function onSign(item: MySignatureItem) {
  if (item.hosted) {
    busyId.value = item.signerId
    error.value = ''
    try {
      const url = await fetchMySigningUrl(item.signerId)
      if (!url) {
        linkMissingId.value = item.signerId
        return
      }
      openSigningPage(url)
    } catch (e) {
      error.value = toProblemDetail(e).detail ?? t('mySignatures.errors.action')
    } finally {
      busyId.value = null
    }
    return
  }
  void decide(item, () => signMine(item.signerId))
}

async function decide(item: MySignatureItem, action: () => Promise<unknown>) {
  busyId.value = item.signerId
  error.value = ''
  try {
    await action()
    decliningId.value = null
    reason.value = ''
    await load()
  } catch (e) {
    error.value = toProblemDetail(e).detail ?? t('mySignatures.errors.action')
    await load()
  } finally {
    busyId.value = null
  }
}

onMounted(load)
</script>

<template>
  <section class="my-signatures">
    <h1>{{ t('mySignatures.title') }}</h1>
    <p v-if="loading">{{ t('common.loading') }}</p>
    <p v-else-if="unavailable" class="empty" data-testid="signatures-unavailable">
      {{ t('mySignatures.unavailable') }}
    </p>
    <template v-else>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <p v-if="uploadDone" class="my-signatures__done" role="status" data-testid="signature-upload-done">
        {{ t('mySignatures.upload.done') }}
      </p>
      <p v-if="!items.length" class="empty" data-testid="signatures-empty">{{ t('mySignatures.empty') }}</p>

      <template v-for="group in [{ title: t('mySignatures.myTurn'), list: myTurn(), active: true }, { title: t('mySignatures.waiting'), list: waiting(), active: false }]" :key="group.title">
        <section v-if="group.list.length" :data-testid="group.active ? 'signatures-active' : 'signatures-waiting'">
          <h2>{{ group.title }}</h2>
          <ul class="my-signatures__list">
            <li v-for="item in group.list" :key="item.signerId">
              <div>
                <strong>{{ item.documentLabel }}</strong>
                <span>
                  {{ t('mySignatures.dossierDue', { reference: item.dossierReference, date: formatDate(item.deadlineAt) }) }}
                  <em v-if="!item.required">{{ t('mySignatures.optional') }}</em>
                  <em v-if="item.simulated" class="my-signatures__simulation">{{ t('mySignatures.simulation') }}</em>
                </span>
              </div>
              <div class="my-signatures__actions">
                <RouterLink :to="`/dossiers/${item.dossierId}`">{{ t('mySignatures.dossier') }}</RouterLink>
                <button type="button" @click="openDocument(item)">{{ t('mySignatures.view') }}</button>
                <template v-if="group.active">
                  <template v-if="decliningId !== item.signerId">
                    <p v-if="linkMissingId === item.signerId" data-testid="signature-waiting">{{ t('mySignatures.waitingLink') }}</p>
                    <button
                      v-if="item.hosted"
                      type="button"
                      data-testid="signature-provider"
                      :disabled="busyId === item.signerId"
                      @click="onSign(item)"
                    >
                      {{ t('mySignatures.signAtProvider') }}
                    </button>
                    <button v-else-if="!item.signedUpload && item.cardBridges.length === 0" type="button" :disabled="busyId === item.signerId" @click="onSign(item)">
                      {{ t('mySignatures.sign') }}
                    </button>
                    <button type="button" :disabled="busyId === item.signerId || cardActive" @click="decliningId = item.signerId">
                      {{ t('mySignatures.decline') }}
                    </button>
                  </template>
                  <template v-else>
                    <input v-model="reason" type="text" maxlength="500" :placeholder="t('mySignatures.reasonPlaceholder')" :aria-label="t('mySignatures.reasonLabel')" />
                    <button
                      type="button"
                      :disabled="busyId === item.signerId"
                      @click="decide(item, () => declineMine(item.signerId, reason))"
                    >
                      {{ t('mySignatures.confirmDecline') }}
                    </button>
                    <button type="button" @click="decliningId = null">{{ t('common.cancel') }}</button>
                  </template>
                </template>
              </div>
              <div
                v-if="group.active && item.cardBridges.length > 0 && decliningId !== item.signerId"
                class="my-signatures__upload"
                data-testid="signature-card"
                :aria-busy="cardActive && cardItem?.signerId === item.signerId"
              >
                <p>{{ t('mySignatures.card.intro') }}</p>
                <button
                  type="button"
                  data-testid="signature-card-sign"
                  :disabled="cardActive || busyId === item.signerId"
                  @click="onCardSign(item)"
                >
                  {{ t('mySignatures.card.sign') }}
                </button>
                <p
                  v-if="cardItem?.signerId === item.signerId"
                  role="status"
                  aria-live="polite"
                  data-testid="signature-card-status"
                >{{ cardStatus }}</p>
                <CardChoiceForm
                  v-if="cardPhase === 'choosing' && cardItem?.signerId === item.signerId"
                  v-model="cardSelected"
                  :choices="cardChoices"
                  prefix="mySignatures.card"
                  :id-base="`signature-${item.signerId}`"
                  @submit="card.submitChoice()"
                  @cancel="card.cancelChoice()"
                />
                <form
                  v-if="cardPhase === 'pin' && cardItem?.signerId === item.signerId"
                  data-testid="signature-pin-form"
                  @submit.prevent="card.submitPin()"
                >
                  <label :for="`signature-pin-${item.signerId}`">{{ t('mySignatures.card.pin.label') }}</label>
                  <input
                    :id="`signature-pin-${item.signerId}`"
                    ref="pinInput"
                    v-model="cardPin"
                    type="password"
                    autocomplete="off"
                    minlength="4"
                    maxlength="12"
                    data-testid="signature-pin"
                  />
                  <p>{{ t('mySignatures.card.pin.hint') }}</p>
                  <button type="submit" data-testid="signature-pin-submit" :disabled="!cardPinValid">
                    {{ t('mySignatures.card.pin.submit') }}
                  </button>
                  <button type="button" data-testid="signature-pin-cancel" @click="card.cancelPin()">
                    {{ t('mySignatures.card.pin.cancel') }}
                  </button>
                </form>
                <p v-if="uploadErrorId === item.signerId" class="error" role="alert" data-testid="signature-error">{{ uploadError }}</p>
                <p v-if="item.signedUpload" class="my-signatures__alt">{{ t('mySignatures.upload.stepsAlt') }}</p>
              </div>
              <div
                v-if="group.active && item.signedUpload && decliningId !== item.signerId"
                class="my-signatures__upload"
                data-testid="signature-upload"
                :aria-busy="uploadingId === item.signerId"
              >
                <ol :aria-label="t('mySignatures.upload.steps', { document: item.documentLabel })">
                  <li>
                    <button type="button" data-testid="signature-download" @click="downloadDocument(item)">
                      {{ t('mySignatures.upload.download') }}
                    </button>
                  </li>
                  <li>{{ t('mySignatures.upload.signStep') }}</li>
                  <li>
                    {{ t('mySignatures.upload.sendStep') }}
                    <label :for="`signed-file-${item.signerId}`">{{ t('mySignatures.upload.file') }}</label>
                    <input
                      :id="`signed-file-${item.signerId}`"
                      :key="`${item.signerId}-${inputVersion}`"
                      type="file"
                      accept="application/pdf,.pdf"
                      data-testid="signature-file"
                      :disabled="uploadingId === item.signerId || cardActive"
                      @change="onFileChange(item, $event)"
                    />
                    <button
                      type="button"
                      data-testid="signature-send"
                      :disabled="uploadingId === item.signerId || cardActive || !chosenFiles[item.signerId]"
                      @click="onUpload(item)"
                    >
                      {{ uploadingId === item.signerId ? t('mySignatures.upload.sending') : t('mySignatures.upload.send') }}
                    </button>
                  </li>
                </ol>
                <p v-if="uploadErrorId === item.signerId && item.cardBridges.length === 0" class="error" role="alert" data-testid="signature-error">{{ uploadError }}</p>
              </div>
            </li>
          </ul>
        </section>
      </template>
    </template>
  </section>
</template>

<style scoped>
.my-signatures__list {
  list-style: none;
  padding: 0;
}
.my-signatures__list li {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 1rem;
  padding: 0.6rem 0;
  border-bottom: 1px solid #ddd;
}
.my-signatures__list span {
  display: block;
  font-size: 0.9rem;
  color: #556;
}
.my-signatures__actions {
  display: flex;
  gap: 0.4rem;
  align-items: center;
}
.my-signatures__upload {
  flex-basis: 100%;
}
.my-signatures__alt {
  margin: 0.5rem 0 0;
  font-weight: 600;
}
.my-signatures__upload ol {
  margin: 0;
  padding-left: 1.25rem;
  display: grid;
  gap: 0.5rem;
}
.my-signatures__upload input[type='file'] {
  display: block;
  margin: 0.25rem 0;
}
.my-signatures__simulation {
  margin-left: 0.5rem;
  font-weight: 700;
  color: #8a5a00;
}
</style>
