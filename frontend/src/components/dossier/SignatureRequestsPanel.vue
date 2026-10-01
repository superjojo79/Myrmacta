<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { formatDateTime } from '@/i18n/format'
import { computed, onMounted, ref, watch } from 'vue'
import { toProblemDetail } from '@/api/http'
import {
  cancelSignatureRequest,
  listSignatureRequests,
  resendSignatureInvite,
  type SignatureRequestView,
  type SignerView,
} from '@/api/signatures'

// TODO §72 US-4 — demandes de signature du dossier. Masqué tant qu'aucune demande n'existe ou si
// l'API est indisponible (fonction désactivée). Les documents signés et preuves sont des pièces du
// dossier (liste des éléments).
const props = defineProps<{ dossierId: string }>()
const { t, te } = useI18n()

function label(group: 'status' | 'signerStatus', code: string): string {
  const key = `dossier.signatures.${group}.${code}`
  return te(key) ? t(key) : code
}

const requests = ref<SignatureRequestView[]>([])
const canManage = ref(false)
const loaded = ref(false)
const busy = ref(false)
const error = ref('')

const hasSimulation = computed(() => requests.value.some((r) => r.simulated))

function formatDate(value: string | null): string {
  return formatDateTime(value)
}

function canResend(request: SignatureRequestView, signer: SignerView): boolean {
  return canManage.value && request.status === 'PENDING' && signer.status === 'PENDING'
}

async function load() {
  try {
    const response = await listSignatureRequests(props.dossierId)
    requests.value = response.requests
    canManage.value = response.canManage
  } catch {
    requests.value = []
  } finally {
    loaded.value = true
  }
}

async function run(action: () => Promise<unknown>) {
  busy.value = true
  error.value = ''
  try {
    await action()
    await load()
  } catch (e) {
    error.value = toProblemDetail(e).detail ?? t('dossier.signatures.errors.action')
  } finally {
    busy.value = false
  }
}

onMounted(load)
watch(() => props.dossierId, load)
</script>

<template>
  <section v-if="loaded && requests.length" class="signature-requests" data-testid="signature-requests-panel">
    <header>
      <h2>{{ t('dossier.signatures.title') }}</h2>
    </header>
    <p v-if="hasSimulation" class="signature-requests__banner" role="status" data-testid="signature-simulation-banner">
      {{ t('dossier.signatures.simulation') }}
    </p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <article v-for="request in requests" :key="request.id" :data-status="request.status" class="signature-requests__item">
      <h3>
        {{ label('status', request.status) }}
        <span class="muted">— {{ request.mode === 'SEQUENTIAL' ? t('dossier.signatures.sequential') : t('dossier.signatures.parallel') }}</span>
      </h3>
      <p class="muted">{{ t('dossier.signatures.created', { created: formatDate(request.createdAt), deadline: formatDate(request.deadlineAt) }) }}</p>
      <table>
        <thead>
          <tr>
            <th>{{ t('dossier.signatures.signer') }}</th>
            <th>{{ t('dossier.signatures.contact') }}</th>
            <th>{{ t('dossier.signatures.statusHeader') }}</th>
            <th>{{ t('dossier.signatures.decision') }}</th>
            <th v-if="canManage"><span class="visually-hidden">{{ t('common.actions') }}</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="signer in request.signers" :key="signer.id">
            <td>{{ signer.displayName }}<span v-if="!signer.required" class="muted"> {{ t('dossier.signatures.optional') }}</span></td>
            <td>{{ signer.contact ?? '—' }}</td>
            <td>{{ label('signerStatus', signer.status) }}</td>
            <td>
              {{ formatDate(signer.decidedAt) }}
              <span v-if="signer.declineReason" class="muted">— {{ signer.declineReason }}</span>
            </td>
            <td v-if="canManage">
              <button
                v-if="canResend(request, signer)"
                type="button"
                :disabled="busy"
                @click="run(() => resendSignatureInvite(request.id, signer.id))"
              >
                {{ t('dossier.signatures.resend') }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <button
        v-if="canManage && request.status === 'PENDING'"
        type="button"
        :disabled="busy"
        data-testid="signature-cancel"
        @click="run(() => cancelSignatureRequest(request.id))"
      >
        {{ t('dossier.signatures.cancelRequest') }}
      </button>
    </article>
  </section>
</template>

<style scoped>
.signature-requests table {
  width: 100%;
  border-collapse: collapse;
}
.signature-requests th,
.signature-requests td {
  text-align: left;
  padding: 0.45rem 0.35rem;
  border-bottom: 1px solid #ddd;
  font-size: 0.9rem;
}
.signature-requests__banner {
  background: #fff4d6;
  border: 1px solid #e0b84a;
  border-radius: 6px;
  padding: 0.4rem 0.8rem;
  font-weight: 700;
}
.signature-requests__item {
  margin-bottom: 1rem;
}
.muted {
  color: #667;
  font-weight: normal;
}
.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
</style>
