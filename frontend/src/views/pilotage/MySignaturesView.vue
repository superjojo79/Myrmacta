<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { toProblemDetail } from '@/api/http'
import { formatDate as formatLocalDate } from '@/i18n/format'
import {
  declineMine,
  fetchMySignatureDocument,
  fetchMySigningUrl,
  listMySignatures,
  openSigningPage,
  signMine,
  uploadMySignedDocument,
  type MySignatureItem,
} from '@/api/signatures'
import { rejectionMessageKey, saveBlob, signedDocumentFilename } from '@/lib/signedUpload'

// TODO §72 US-10 — signatures internes qui me sont adressées. « À mon tour » (actives) puis
// « En attente » (séquentiel : un signataire précédent doit d'abord décider).
const { t } = useI18n()
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
    const problem = toProblemDetail(e)
    uploadErrorId.value = item.signerId
    if (problem.status === 422) {
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
  } finally {
    uploadingId.value = null
  }
}

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
                    <button v-else-if="!item.signedUpload" type="button" :disabled="busyId === item.signerId" @click="onSign(item)">
                      {{ t('mySignatures.sign') }}
                    </button>
                    <button type="button" :disabled="busyId === item.signerId" @click="decliningId = item.signerId">
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
                      :disabled="uploadingId === item.signerId"
                      @change="onFileChange(item, $event)"
                    />
                    <button
                      type="button"
                      data-testid="signature-send"
                      :disabled="uploadingId === item.signerId || !chosenFiles[item.signerId]"
                      @click="onUpload(item)"
                    >
                      {{ uploadingId === item.signerId ? t('mySignatures.upload.sending') : t('mySignatures.upload.send') }}
                    </button>
                  </li>
                </ol>
                <p v-if="uploadErrorId === item.signerId" class="error" role="alert" data-testid="signature-error">{{ uploadError }}</p>
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
