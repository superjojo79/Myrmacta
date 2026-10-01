<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { toProblemDetail } from '@/api/http'
import {
  discardDraft,
  listPendingDrafts,
  listPublications,
  publishDraft,
  type ConfigPublication,
  type PendingDraft,
  type Provenance,
} from '@/api/drafts'

const drafts = ref<PendingDraft[]>([])
const publications = ref<ConfigPublication[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
const busyKey = ref<string | null>(null)

const PROVENANCE_LABELS: Record<Provenance, string> = {
  UI: 'Éditeur',
  MCP: 'Agent (MCP)',
  ASSISTANT: 'Assistant',
}

function rowId(d: PendingDraft): string {
  return `${d.type}:${d.key}`
}

function editorLink(d: PendingDraft): string {
  return d.type === 'BPMN'
    ? `/admin/bpmn-editor?processDefinitionKey=${encodeURIComponent(d.key)}`
    : `/admin/dmn-editor?decisionKey=${encodeURIComponent(d.key)}`
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('fr-FR')
}

async function load(keepMessages = false) {
  loading.value = true
  if (!keepMessages) error.value = null
  try {
    ;[drafts.value, publications.value] = await Promise.all([listPendingDrafts(), listPublications(10)])
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? 'Impossible de charger les brouillons'
  } finally {
    loading.value = false
  }
}

async function publish(d: PendingDraft) {
  const effect = d.hasProposedConfig ? ' (la configuration de workflow proposée sera aussi appliquée)' : ''
  if (!window.confirm(`Publier ${d.type} « ${d.key} »${effect} ? Il passera en ligne immédiatement.\n\nAvez-vous relu le diagramme${d.hasProposedConfig ? ' ET la configuration de workflow affichée' : ''} ?`)) return
  busyKey.value = rowId(d)
  error.value = null
  notice.value = null
  try {
    // On publie exactement ce qui est affiché : un brouillon modifié entre-temps est refusé (409).
    const publication = await publishDraft(d.type, d.key, d.updatedAt)
    notice.value = `« ${d.key} » publié${publication.version ? ` (version ${publication.version})` : ''}.`
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? 'Publication impossible'
  } finally {
    busyKey.value = null
    await load(true)
  }
}

async function discard(d: PendingDraft) {
  if (!window.confirm(`Écarter le brouillon ${d.type} « ${d.key} » ? Cette action est définitive.`)) return
  busyKey.value = rowId(d)
  error.value = null
  notice.value = null
  try {
    await discardDraft(d.type, d.key, d.updatedAt)
    notice.value = `Brouillon « ${d.key} » écarté.`
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? 'Impossible d’écarter le brouillon'
  } finally {
    busyKey.value = null
    await load(true)
  }
}

onMounted(() => load())
</script>

<template>
  <section class="drafts-admin">
    <h1>Brouillons à publier</h1>
    <p class="drafts-admin__intro">
      Les configurations proposées par un agent ou l’assistant attendent ici une relecture humaine : rien n’est
      déployé tant que vous n’avez pas publié. Les brouillons de l’éditeur apparaissent aussi.
    </p>
    <p v-if="notice" class="drafts-admin__notice" role="status" data-testid="drafts-notice">{{ notice }}</p>
    <p v-if="error" class="drafts-admin__error" role="alert" data-testid="drafts-error">{{ error }}</p>
    <p v-if="loading">Chargement…</p>
    <template v-else>
      <p v-if="drafts.length === 0" data-testid="drafts-empty">Aucun brouillon en attente.</p>
      <table v-else class="drafts-admin__table" data-testid="drafts-table">
        <thead>
          <tr>
            <th>Type</th>
            <th>Clé</th>
            <th>Origine</th>
            <th>Auteur</th>
            <th>Modifié</th>
            <th>Écart avec la version en ligne</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="d in drafts" :key="rowId(d)" :data-testid="`draft-row-${d.key}`">
            <td>{{ d.type }}</td>
            <td>
              <RouterLink :to="editorLink(d)">{{ d.key }}</RouterLink>
              <small v-if="d.note" class="drafts-admin__note"> — {{ d.note }}</small>
              <small v-if="d.hasProposedConfig" class="drafts-admin__note"> · configuration de workflow jointe</small>
              <details v-if="d.proposedConfig" class="drafts-admin__config" :data-testid="`draft-config-${d.key}`">
                <summary>Configuration de workflow qui sera appliquée</summary>
                <p v-if="d.configProvenance" class="drafts-admin__note">
                  Rédigée par {{ d.configAuthor }} ({{ PROVENANCE_LABELS[d.configProvenance] }})
                  <template v-if="d.configProvenance !== d.provenance"> — le diagramme a été repris depuis</template>
                </p>
                <dl>
                  <dt>Libellé</dt>
                  <dd>{{ d.proposedConfig.label }}</dd>
                  <dt>Statuts de workflow</dt>
                  <dd>
                    <span v-for="s in d.proposedConfig.statusDefinitions" :key="s.code" class="drafts-admin__chip">
                      {{ s.code }}<template v-if="s.initial"> (initial)</template>
                      <template v-if="s.outcome !== 'NONE'"> → {{ s.outcome }}</template>
                    </span>
                  </dd>
                  <dt>Statuts de dossier autorisés</dt>
                  <dd>{{ d.proposedConfig.allowedDossierStatuses.join(', ') }}</dd>
                  <dt>Statut au démarrage</dt>
                  <dd>{{ d.proposedConfig.onStartDossierStatus ?? '—' }}</dd>
                  <dt>Observateurs notifiés</dt>
                  <dd>{{ d.proposedConfig.observerUserIds.length ? d.proposedConfig.observerUserIds.join(', ') : 'aucun' }}</dd>
                </dl>
              </details>
            </td>
            <td>
              <span :class="['drafts-admin__badge', `drafts-admin__badge--${d.provenance.toLowerCase()}`]">
                {{ PROVENANCE_LABELS[d.provenance] }}
              </span>
            </td>
            <td>{{ d.author }}</td>
            <td>{{ formatDate(d.updatedAt) }}</td>
            <td>
              <template v-if="d.publishedVersion != null">
                v{{ d.publishedVersion }} : +{{ d.addedLines }} / −{{ d.removedLines }} lignes
              </template>
              <template v-else>jamais publié</template>
            </td>
            <td class="drafts-admin__actions">
              <button type="button" :disabled="busyKey === rowId(d)" data-testid="draft-publish" @click="publish(d)">
                Publier
              </button>
              <button type="button" :disabled="busyKey === rowId(d)" data-testid="draft-discard" @click="discard(d)">
                Écarter
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <h2>Dernières publications</h2>
      <p v-if="publications.length === 0">Aucune publication enregistrée.</p>
      <ul v-else class="drafts-admin__log" data-testid="publications-log">
        <li v-for="p in publications" :key="p.id">
          {{ formatDate(p.publishedAt) }} — {{ p.artifactType }} « {{ p.artifactKey }} »
          <template v-if="p.version != null">v{{ p.version }}</template>
          rédigé par {{ p.draftAuthor }} ({{ PROVENANCE_LABELS[p.draftProvenance] }}), publié par {{ p.publishedBy }}
          <em v-if="p.mode === 'DIRECT'">(publication directe, sans relecture)</em>
        </li>
      </ul>
    </template>
  </section>
</template>

<style scoped>
.drafts-admin__intro {
  max-width: 60rem;
}
.drafts-admin__error {
  color: #c92a2a;
}
.drafts-admin__notice {
  color: #2b8a3e;
}
.drafts-admin__table {
  border-collapse: collapse;
  width: 100%;
}
.drafts-admin__table th,
.drafts-admin__table td {
  border-bottom: 1px solid #ddd;
  padding: 0.4rem 0.6rem;
  text-align: left;
  vertical-align: top;
}
.drafts-admin__note {
  color: #666;
}
.drafts-admin__chip {
  display: inline-block;
  margin-right: 0.4rem;
  padding: 0 0.4rem;
  background: #f1f3f5;
  border-radius: 0.3rem;
}
.drafts-admin__badge {
  border-radius: 0.75rem;
  padding: 0.1rem 0.5rem;
  font-size: 0.85em;
  background: #e9ecef;
}
.drafts-admin__badge--mcp,
.drafts-admin__badge--assistant {
  background: #fff3bf;
}
.drafts-admin__actions {
  display: flex;
  gap: 0.4rem;
}
</style>
