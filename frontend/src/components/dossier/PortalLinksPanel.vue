<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { formatDateTime } from '@/i18n/format'
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
const { t, te } = useI18n()

/** Libellé d'un code serveur : traduit s'il est connu, sinon le code tel quel (un nouveau code serveur ne casse pas l'écran). */
function label(group: 'status' | 'reason' | 'identityResult' | 'identityLevel' | 'identityProvider', code: string): string {
  const key = `dossier.portalLinks.${group}.${code}`
  return te(key) ? t(key) : code
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
  return formatDateTime(value)
}

function statusText(link: PortalLinkView): string {
  const status = label('status', link.status)
  const reason = link.statusReason ? label('reason', link.statusReason) : ''
  return reason ? `${status} — ${reason}` : status
}

/** TODO §104 (T8) — « Identité vérifiée (FAS, élevé) : Alice Martin — premier enregistrement du NRN ». */
function identityText(link: PortalLinkView): string {
  const identity = link.identity
  if (!identity) return ''
  const name = [identity.givenName, identity.familyName].filter(Boolean).join(' ')
  return t('dossier.portalLinks.identity', {
    provider: label('identityProvider', identity.provider),
    level: label('identityLevel', identity.level),
    name: name || '—',
    result: label('identityResult', identity.result),
  })
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
      error.value = t('dossier.portalLinks.notSent', {
        reason: result.statusReason && te(`dossier.portalLinks.reason.${result.statusReason}`)
          ? label('reason', result.statusReason)
          : t('dossier.portalLinks.unknownReason'),
      })
    }
    await load()
  } catch (e) {
    error.value = toProblemDetail(e).detail ?? t('dossier.portalLinks.errors.action')
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
      <h2>{{ t('dossier.portalLinks.title') }}</h2>
    </header>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <table>
      <thead>
        <tr>
          <th>{{ t('dossier.portalLinks.recipient') }}</th>
          <th>{{ t('dossier.portalLinks.statusHeader') }}</th>
          <th>{{ t('dossier.portalLinks.sent') }}</th>
          <th>{{ t('dossier.portalLinks.expires') }}</th>
          <th>{{ t('dossier.portalLinks.lastAccess') }}</th>
          <th v-if="canManage"><span class="visually-hidden">{{ t('common.actions') }}</span></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="link in links" :key="link.id" :data-status="link.status">
          <td>{{ link.recipient ?? '—' }}</td>
          <td>
            {{ statusText(link) }}
            <span v-if="link.identity" class="portal-links__identity" data-testid="portal-link-identity">
              {{ identityText(link) }}
            </span>
          </td>
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
              {{ link.status === 'NOT_SENT' ? t('dossier.portalLinks.send') : t('dossier.portalLinks.resend') }}
            </button>
            <button
              v-if="liveStatuses.includes(link.status)"
              type="button"
              :disabled="busy"
              @click="onRevoke(link.id)"
            >
              {{ t('dossier.portalLinks.revoke') }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>

<style scoped>
.portal-links__identity {
  display: block;
  font-size: 0.85em;
  color: var(--text-muted, #5b6773);
}

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
