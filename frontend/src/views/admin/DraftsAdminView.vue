<script setup lang="ts">
import { labelOr } from '@/i18n'
import { useI18n } from 'vue-i18n'
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { toProblemDetail } from '@/api/http'
import { formatDateTime } from '@/i18n/format'
import {
  discardDraft,
  listPendingDrafts,
  listPublications,
  publishDraft,
  type ConfigPublication,
  type PendingDraft,
  type Provenance,
} from '@/api/drafts'

const { t } = useI18n()
const drafts = ref<PendingDraft[]>([])
const publications = ref<ConfigPublication[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
const busyKey = ref<string | null>(null)

const origin = (p: Provenance): string => labelOr(`admin.drafts.provenance.${p}`, p)

function rowId(d: PendingDraft): string {
  return `${d.type}:${d.key}`
}

function editorLink(d: PendingDraft): string {
  return d.type === 'BPMN'
    ? `/admin/bpmn-editor?processDefinitionKey=${encodeURIComponent(d.key)}`
    : `/admin/dmn-editor?decisionKey=${encodeURIComponent(d.key)}`
}

function formatDate(iso: string): string {
  return formatDateTime(iso, {})
}

async function load(keepMessages = false) {
  loading.value = true
  if (!keepMessages) error.value = null
  try {
    ;[drafts.value, publications.value] = await Promise.all([listPendingDrafts(), listPublications(10)])
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.drafts.errors.load')
  } finally {
    loading.value = false
  }
}

async function publish(d: PendingDraft) {
  const effect = d.hasProposedConfig ? t('admin.drafts.confirmEffect') : ''
  const withConfig = d.hasProposedConfig ? t('admin.drafts.confirmWithConfig') : ''
  if (!window.confirm(t('admin.drafts.confirmPublish', { type: d.type, key: d.key, effect, withConfig }))) return
  busyKey.value = rowId(d)
  error.value = null
  notice.value = null
  try {
    // On publie exactement ce qui est affiché : un brouillon modifié entre-temps est refusé (409).
    const publication = await publishDraft(d.type, d.key, d.updatedAt)
    notice.value = t('admin.drafts.published', {
      key: d.key,
      version: publication.version ? t('admin.drafts.publishedVersion', { version: publication.version }) : '',
    })
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.drafts.errors.publish')
  } finally {
    busyKey.value = null
    await load(true)
  }
}

async function discard(d: PendingDraft) {
  if (!window.confirm(t('admin.drafts.confirmDiscard', { type: d.type, key: d.key }))) return
  busyKey.value = rowId(d)
  error.value = null
  notice.value = null
  try {
    await discardDraft(d.type, d.key, d.updatedAt)
    notice.value = t('admin.drafts.discarded', { key: d.key })
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.drafts.errors.discard')
  } finally {
    busyKey.value = null
    await load(true)
  }
}

onMounted(() => load())
</script>

<template>
  <section class="drafts-admin">
    <h1>{{ t('admin.drafts.title') }}</h1>
    <p class="drafts-admin__intro">
      {{ t('admin.drafts.intro') }}
    </p>
    <p v-if="notice" class="drafts-admin__notice" role="status" data-testid="drafts-notice">{{ notice }}</p>
    <p v-if="error" class="drafts-admin__error" role="alert" data-testid="drafts-error">{{ error }}</p>
    <p v-if="loading">{{ t('common.loading') }}</p>
    <template v-else>
      <p v-if="drafts.length === 0" data-testid="drafts-empty">{{ t('admin.drafts.empty') }}</p>
      <table v-else class="drafts-admin__table" data-testid="drafts-table">
        <thead>
          <tr>
            <th>{{ t('admin.drafts.type') }}</th>
            <th>{{ t('admin.drafts.key') }}</th>
            <th>{{ t('admin.drafts.origin') }}</th>
            <th>{{ t('admin.drafts.author') }}</th>
            <th>{{ t('admin.drafts.modified') }}</th>
            <th>{{ t('admin.drafts.diff') }}</th>
            <th>{{ t('admin.drafts.actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="d in drafts" :key="rowId(d)" :data-testid="`draft-row-${d.key}`">
            <td>{{ d.type }}</td>
            <td>
              <RouterLink :to="editorLink(d)">{{ d.key }}</RouterLink>
              <small v-if="d.note" class="drafts-admin__note"> — {{ d.note }}</small>
              <small v-if="d.hasProposedConfig" class="drafts-admin__note"> · {{ t('admin.drafts.configAttached') }}</small>
              <details v-if="d.proposedConfig" class="drafts-admin__config" :data-testid="`draft-config-${d.key}`">
                <summary>{{ t('admin.drafts.configSummary') }}</summary>
                <p v-if="d.configProvenance" class="drafts-admin__note">
                  {{ t('admin.drafts.writtenBy', { author: d.configAuthor, origin: origin(d.configProvenance) }) }}
                  <template v-if="d.configProvenance !== d.provenance"> — {{ t('admin.drafts.diagramReused') }}</template>
                </p>
                <dl>
                  <dt>{{ t('admin.drafts.label') }}</dt>
                  <dd>{{ d.proposedConfig.label }}</dd>
                  <dt>{{ t('admin.drafts.workflowStatuses') }}</dt>
                  <dd>
                    <span v-for="s in d.proposedConfig.statusDefinitions" :key="s.code" class="drafts-admin__chip">
                      {{ s.code }}<template v-if="s.initial">{{ ' ' + t('admin.drafts.initial') }}</template>
                      <template v-if="s.outcome !== 'NONE'"> → {{ s.outcome }}</template>
                    </span>
                  </dd>
                  <dt>{{ t('admin.drafts.allowedStatuses') }}</dt>
                  <dd>{{ d.proposedConfig.allowedDossierStatuses.join(', ') }}</dd>
                  <dt>{{ t('admin.drafts.startStatus') }}</dt>
                  <dd>{{ d.proposedConfig.onStartDossierStatus ?? '—' }}</dd>
                  <dt>{{ t('admin.drafts.observers') }}</dt>
                  <dd>{{ d.proposedConfig.observerUserIds.length ? d.proposedConfig.observerUserIds.join(', ') : t('admin.drafts.none') }}</dd>
                </dl>
              </details>
            </td>
            <td>
              <span :class="['drafts-admin__badge', `drafts-admin__badge--${d.provenance.toLowerCase()}`]">
                {{ origin(d.provenance) }}
              </span>
            </td>
            <td>{{ d.author }}</td>
            <td>{{ formatDate(d.updatedAt) }}</td>
            <td>
              <template v-if="d.publishedVersion != null">
                {{ t('admin.drafts.linesDiff', { version: d.publishedVersion, added: d.addedLines, removed: d.removedLines }) }}
              </template>
              <template v-else>{{ t('admin.drafts.neverPublished') }}</template>
            </td>
            <td class="drafts-admin__actions">
              <button type="button" :disabled="busyKey === rowId(d)" data-testid="draft-publish" @click="publish(d)">
                {{ t('admin.drafts.publish') }}
              </button>
              <button type="button" :disabled="busyKey === rowId(d)" data-testid="draft-discard" @click="discard(d)">
                {{ t('admin.drafts.discard') }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <h2>{{ t('admin.drafts.latest') }}</h2>
      <p v-if="publications.length === 0">{{ t('admin.drafts.noPublication') }}</p>
      <ul v-else class="drafts-admin__log" data-testid="publications-log">
        <li v-for="p in publications" :key="p.id">
          {{ t('admin.drafts.logLine', { date: formatDate(p.publishedAt), type: p.artifactType, key: p.artifactKey, version: p.version != null ? `v${p.version} ` : '', author: p.draftAuthor, origin: origin(p.draftProvenance), by: p.publishedBy }) }}
          <em v-if="p.mode === 'DIRECT'">{{ t('admin.drafts.direct') }}</em>
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
.drafts-admin__badge--assistant,
.drafts-admin__badge--import {
  background: #fff3bf;
}
.drafts-admin__actions {
  display: flex;
  gap: 0.4rem;
}
</style>
