<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { toProblemDetail } from '@/api/http'
import {
  cancelSignatureRequest,
  listSignatureRequests,
  resendSignatureInvite,
  type SignatureRequestView,
  type SignatureStatus,
  type SignerStatus,
  type SignerView,
} from '@/api/signatures'

// TODO §72 US-4 — demandes de signature du dossier. Masqué tant qu'aucune demande n'existe ou si
// l'API est indisponible (fonction désactivée). Les documents signés et preuves sont des pièces du
// dossier (liste des éléments).
const props = defineProps<{ dossierId: string }>()

const STATUS_LABELS: Record<SignatureStatus, string> = {
  PENDING: 'En cours',
  SIGNED: 'Signée',
  DECLINED: 'Refusée',
  EXPIRED: 'Expirée',
  FAILED: 'Échec',
  CANCELLED: 'Annulée',
}

const SIGNER_LABELS: Record<SignerStatus, string> = {
  WAITING: 'En attente de son tour',
  PENDING: 'À signer',
  SIGNED: 'Signé',
  DECLINED: 'Refusé',
  EXPIRED: 'Expiré',
  REVOKED: 'Révoqué',
}

const requests = ref<SignatureRequestView[]>([])
const canManage = ref(false)
const loaded = ref(false)
const busy = ref(false)
const error = ref('')

const hasSimulation = computed(() => requests.value.some((r) => r.simulated))

function formatDate(value: string | null): string {
  return value ? new Date(value).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' }) : '—'
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
    error.value = toProblemDetail(e).detail ?? 'Action impossible.'
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
      <h2>Signatures</h2>
    </header>
    <p v-if="hasSimulation" class="signature-requests__banner" role="status" data-testid="signature-simulation-banner">
      SIMULATION — sans valeur juridique
    </p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <article v-for="request in requests" :key="request.id" :data-status="request.status" class="signature-requests__item">
      <h3>
        {{ STATUS_LABELS[request.status] ?? request.status }}
        <span class="muted">— {{ request.mode === 'SEQUENTIAL' ? 'séquentielle' : 'parallèle' }}</span>
      </h3>
      <p class="muted">Créée {{ formatDate(request.createdAt) }} · échéance {{ formatDate(request.deadlineAt) }}</p>
      <table>
        <thead>
          <tr>
            <th>Signataire</th>
            <th>Contact</th>
            <th>Statut</th>
            <th>Décision</th>
            <th v-if="canManage"><span class="visually-hidden">Actions</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="signer in request.signers" :key="signer.id">
            <td>{{ signer.displayName }}<span v-if="!signer.required" class="muted"> (facultatif)</span></td>
            <td>{{ signer.contact ?? '—' }}</td>
            <td>{{ SIGNER_LABELS[signer.status] ?? signer.status }}</td>
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
                Renvoyer
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
        Annuler la demande
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
