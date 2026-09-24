<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { toProblemDetail } from '@/api/http'
import {
  listPortalLinks,
  revokePortalLink,
  sendPortalLink,
  type PortalLinkStatus,
  type PortalLinkView,
} from '@/api/portalLinks'

// TODO §68 (portail-externe) US-5 — liens envoyés aux tiers externes pour ce dossier. Masqué tant
// qu'aucun lien n'existe (dossier sans tâche externe) ou si l'API est indisponible.
const props = defineProps<{ dossierId: string }>()

const STATUS_LABELS: Record<PortalLinkStatus, string> = {
  PENDING_SEND: 'Envoi en cours',
  ACTIVE: 'En attente du tiers',
  NOT_SENT: 'Non envoyé',
  COMPLETED: 'Répondu',
  EXPIRED: 'Expiré',
  REVOKED: 'Révoqué',
}

const REASON_LABELS: Record<string, string> = {
  NO_ADDRESS: 'aucune adresse e-mail renseignée',
  INVALID_ADDRESS: 'adresse e-mail invalide',
  SCHEMA_MISSING: 'formulaire introuvable',
  SEND_FAILED: 'échec d’envoi de l’e-mail',
  TASK_GONE: 'tâche terminée',
  RESENT: 'remplacé par un nouvel envoi',
  REVOKED_BY_USER: 'révoqué manuellement',
}

const links = ref<PortalLinkView[]>([])
const canManage = ref(false)
const loaded = ref(false)
const busy = ref(false)
const error = ref('')

const liveStatuses: PortalLinkStatus[] = ['PENDING_SEND', 'ACTIVE', 'NOT_SENT']
/**
 * (R)envoi proposé sur le lien le plus récent de chaque tâche (liste triée du plus récent au plus
 * ancien par l'API), quand il est vivant ou expiré — pas sur un lien répondu, ni sur l'historique.
 */
const resendableLinkIds = computed(() => {
  const seenTasks = new Set<string>()
  const ids = new Set<string>()
  for (const link of links.value) {
    if (seenTasks.has(link.taskId)) continue
    seenTasks.add(link.taskId)
    if (liveStatuses.includes(link.status) || link.status === 'EXPIRED') ids.add(link.id)
  }
  return ids
})

function formatDate(value: string | null): string {
  return value ? new Date(value).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' }) : '—'
}

function statusText(link: PortalLinkView): string {
  const label = STATUS_LABELS[link.status] ?? link.status
  const reason = link.statusReason ? REASON_LABELS[link.statusReason] ?? link.statusReason : ''
  return reason ? `${label} — ${reason}` : label
}

async function load() {
  try {
    const response = await listPortalLinks(props.dossierId)
    links.value = response.links
    canManage.value = response.canManage
  } catch {
    links.value = []
  } finally {
    loaded.value = true
  }
}

async function run(action: () => Promise<PortalLinkView>) {
  busy.value = true
  error.value = ''
  try {
    const result = await action()
    if (result.status === 'NOT_SENT') {
      error.value = `Lien non envoyé : ${REASON_LABELS[result.statusReason ?? ''] ?? 'erreur'}. Corrigez puis réessayez.`
    }
    await load()
  } catch (e) {
    error.value = toProblemDetail(e).detail ?? 'Action impossible.'
  } finally {
    busy.value = false
  }
}

function onSend(taskId: string) {
  return run(() => sendPortalLink(props.dossierId, taskId))
}

function onRevoke(linkId: string) {
  return run(() => revokePortalLink(props.dossierId, linkId))
}

onMounted(load)
watch(() => props.dossierId, load)
</script>

<template>
  <section v-if="loaded && links.length" class="portal-links" data-testid="portal-links-panel">
    <header class="portal-links__header">
      <h2>Tiers externes</h2>
    </header>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <table>
      <thead>
        <tr>
          <th>Destinataire</th>
          <th>Statut</th>
          <th>Envoyé</th>
          <th>Expire</th>
          <th>Dernier accès</th>
          <th v-if="canManage"><span class="visually-hidden">Actions</span></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="link in links" :key="link.id" :data-status="link.status">
          <td>{{ link.recipient ?? '—' }}</td>
          <td>{{ statusText(link) }}</td>
          <td>{{ formatDate(link.sentAt) }}</td>
          <td>{{ formatDate(link.expiresAt) }}</td>
          <td>{{ formatDate(link.lastAccessedAt) }}</td>
          <td v-if="canManage" class="portal-links__actions">
            <button
              v-if="resendableLinkIds.has(link.id)"
              type="button"
              :disabled="busy"
              @click="onSend(link.taskId)"
            >
              {{ link.status === 'NOT_SENT' ? 'Envoyer' : 'Renvoyer' }}
            </button>
            <button
              v-if="liveStatuses.includes(link.status)"
              type="button"
              :disabled="busy"
              @click="onRevoke(link.id)"
            >
              Révoquer
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>

<style scoped>
.portal-links table {
  width: 100%;
  border-collapse: collapse;
}
.portal-links th,
.portal-links td {
  text-align: left;
  padding: 0.45rem 0.35rem;
  border-bottom: 1px solid #ddd;
  font-size: 0.9rem;
}
.portal-links__actions {
  display: flex;
  gap: 0.35rem;
}
.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
</style>
