<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { nextTick, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { toProblemDetail } from '@/api/http'
import {
  applyAction,
  deleteConversation,
  dismissAction,
  getAssistantStatus,
  getConversation,
  listActions,
  listConversations,
  sendAssistantMessage,
  type AssistantAction,
  type AssistantMessage,
  type AssistantStatus,
  type ConversationSummary,
  type ToolActivity,
} from '@/api/assistant'

/**
 * TODO §86 (LC3) — assistant de conception : on décrit son besoin en langage naturel ; l'assistant propose en BROUILLON (types,
 * formulaires, processus, tables de décision, templates) que l'on relit dans les éditeurs et dans Admin → Brouillons. Il n'a
 * accès à aucune donnée de dossier, ne publie jamais et n'écrit jamais de secret.
 */
interface Turn {
  role: 'USER' | 'ASSISTANT'
  text: string
  activity?: ToolActivity[]
  drafts?: boolean
}

const { t, te } = useI18n()
const status = ref<AssistantStatus | null>(null)
const conversations = ref<ConversationSummary[]>([])
const conversationId = ref<string | null>(null)
const turns = ref<Turn[]>([])
const draft = ref('')
const busy = ref(false)
const error = ref<string | null>(null)
const log = ref<HTMLElement | null>(null)
const actions = ref<AssistantAction[]>([])
const actionBusy = ref<string | null>(null)

const statusLabel = (status: AssistantAction['status']): string => t(`admin.assistant.status.${status}`)
const toolLabel = (tool: string): string => (te(`admin.assistant.tools.${tool}`) ? t(`admin.assistant.tools.${tool}`) : tool)

function pretty(json: string): string {
  try {
    return JSON.stringify(JSON.parse(json), null, 2)
  } catch {
    return json
  }
}

async function loadActions() {
  if (!conversationId.value) {
    actions.value = []
    return
  }
  try {
    actions.value = await listActions(conversationId.value)
  } catch {
    actions.value = []
  }
}

async function resolve(action: AssistantAction, apply: boolean) {
  if (!conversationId.value || actionBusy.value) return
  error.value = null
  actionBusy.value = action.id
  try {
    const updated = await (apply ? applyAction : dismissAction)(conversationId.value, action.id)
    actions.value = actions.value.map((a) => (a.id === updated.id ? updated : a))
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.assistant.errors.action')
    await loadActions()
  } finally {
    actionBusy.value = null
  }
}

async function loadList() {
  try {
    conversations.value = await listConversations()
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.assistant.errors.history')
  }
}

onMounted(async () => {
  try {
    status.value = await getAssistantStatus()
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.assistant.errors.state')
    return
  }
  if (status.value.enabled) await loadList()
})

function fromMessages(messages: AssistantMessage[]): Turn[] {
  // Les résultats d'outils (rôle TOOL) se replient dans le tour de l'assistant qui suit.
  const out: Turn[] = []
  let pending: ToolActivity[] = []
  for (const m of messages) {
    if (m.role === 'USER') out.push({ role: 'USER', text: m.content })
    else if (m.role === 'TOOL') pending.push({ tool: m.toolName ?? t('admin.assistant.tool'), ok: !m.content.startsWith('error:'), summary: m.content.slice(0, 300) })
    else {
      out.push({ role: 'ASSISTANT', text: m.content, activity: pending })
      pending = []
    }
  }
  return out
}

async function open(id: string) {
  if (busy.value) return // une réponse en cours s'afficherait dans la mauvaise conversation
  error.value = null
  try {
    const detail = await getConversation(id)
    conversationId.value = id
    turns.value = fromMessages(detail.messages)
    await loadActions()
    await scroll()
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.assistant.errors.notFound')
  }
}

function fresh() {
  if (busy.value) return
  conversationId.value = null
  turns.value = []
  actions.value = []
  error.value = null
}

async function remove(id: string) {
  if (busy.value) return
  if (!window.confirm(t('admin.assistant.confirmDelete'))) return
  try {
    await deleteConversation(id)
    if (conversationId.value === id) fresh()
    await loadList()
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? t('admin.assistant.errors.remove')
  }
}

async function scroll() {
  await nextTick()
  if (log.value) log.value.scrollTop = log.value.scrollHeight
}

async function send() {
  const text = draft.value.trim()
  if (!text || busy.value) return
  error.value = null
  busy.value = true
  turns.value.push({ role: 'USER', text })
  draft.value = ''
  await scroll()
  try {
    const reply = await sendAssistantMessage(text, conversationId.value)
    conversationId.value = reply.conversationId
    turns.value.push({ role: 'ASSISTANT', text: reply.reply, activity: reply.activity, drafts: reply.producedDrafts })
    await loadActions()
    await loadList()
  } catch (err) {
    // Le texte n'est pas perdu : on le remet dans la zone de saisie pour le renvoyer.
    draft.value = text
    turns.value.pop()
    error.value = toProblemDetail(err).detail ?? t('admin.assistant.errors.reply')
  } finally {
    busy.value = false
    await scroll()
  }
}
</script>

<template>
  <section class="assistant" data-testid="assistant-view">
    <h1>{{ t('admin.assistant.title') }}</h1>
    <p v-if="error" class="error" role="alert" data-testid="assistant-error">{{ error }}</p>

    <i18n-t v-if="status && !status.enabled" keypath="admin.assistant.disabled" tag="p" class="assistant__disabled" data-testid="assistant-disabled">
      <template #enabled><code>APP_ASSISTANT_ENABLED</code></template>
      <template #key><code>APP_ASSISTANT_API_KEY</code></template>
    </i18n-t>

    <div v-else-if="status" class="assistant__layout">
      <aside class="assistant__history" :aria-label="t('admin.assistant.conversations')">
        <button type="button" :disabled="busy" data-testid="assistant-new" @click="fresh">{{ t('admin.assistant.newConversation') }}</button>
        <ul>
          <li v-for="c in conversations" :key="c.id" :class="{ 'assistant__current': c.id === conversationId }">
            <button type="button" class="assistant__open" :disabled="busy" data-testid="assistant-open" @click="open(c.id)">{{ c.title }}</button>
            <button type="button" class="assistant__remove" :disabled="busy" :aria-label="t('admin.assistant.removeConversation', { title: c.title })" @click="remove(c.id)">✕</button>
          </li>
        </ul>
      </aside>

      <div class="assistant__chat">
        <i18n-t keypath="admin.assistant.hint" tag="p" class="assistant__hint">
          <template #draft><strong>{{ t('admin.assistant.hintDraft') }}</strong></template>
        </i18n-t>
        <div ref="log" class="assistant__log" role="log" aria-live="polite" data-testid="assistant-log">
          <p v-if="turns.length === 0" class="assistant__empty">{{ t('admin.assistant.empty') }}</p>
          <article v-for="(turn, i) in turns" :key="i" :class="['assistant__turn', `assistant__turn--${turn.role.toLowerCase()}`]">
            <header>{{ turn.role === 'USER' ? t('admin.assistant.you') : t('admin.assistant.assistant') }}</header>
            <!-- Texte brut : la réponse d'un modèle n'est jamais interprétée comme du HTML. -->
            <p class="assistant__text">{{ turn.text }}</p>
            <ul v-if="turn.activity?.length" class="assistant__activity">
              <li v-for="(a, j) in turn.activity" :key="j" :class="{ 'assistant__activity--ko': !a.ok }">
                <code>{{ a.tool }}</code> — {{ a.ok ? t('admin.assistant.done') : t('admin.assistant.refused') }}
                <details v-if="a.arguments"><summary>{{ t('admin.assistant.arguments') }}</summary><pre class="assistant__args">{{ a.arguments }}</pre></details>
              </li>
            </ul>
            <p v-if="turn.drafts" class="assistant__drafts" data-testid="assistant-drafts">
              {{ t('admin.assistant.draftsWaiting') }}
              <RouterLink to="/admin/drafts">{{ t('admin.assistant.draftsLink') }}</RouterLink>
            </p>
          </article>
          <p v-if="busy" class="assistant__busy" data-testid="assistant-busy">{{ t('admin.assistant.working') }}</p>
        </div>
        <section v-if="actions.length" class="assistant__actions" :aria-label="t('admin.assistant.proposedAria')" data-testid="assistant-actions">
          <h2>{{ t('admin.assistant.proposed') }}</h2>
          <i18n-t keypath="admin.assistant.proposedHint" tag="p" class="assistant__hint">
            <template #not><strong>{{ t('admin.assistant.proposedNot') }}</strong></template>
          </i18n-t>
          <article v-for="a in actions" :key="a.id" class="assistant__action" :data-status="a.status" data-testid="assistant-action">
            <header>
              <strong>{{ toolLabel(a.tool) }}</strong>
              <span class="assistant__status">{{ statusLabel(a.status) }}</span>
            </header>
            <!-- Arguments en texte brut : c'est ce que l'on s'apprête à écrire. -->
            <pre class="assistant__args" data-testid="assistant-action-args">{{ pretty(a.arguments) }}</pre>
            <p v-if="a.outcome" class="assistant__outcome">{{ a.outcome }}</p>
            <div v-if="a.status === 'PENDING'" class="assistant__action-buttons">
              <button type="button" :disabled="!!actionBusy" data-testid="assistant-apply" @click="resolve(a, true)">{{ t('admin.assistant.apply') }}</button>
              <button type="button" :disabled="!!actionBusy" data-testid="assistant-dismiss" @click="resolve(a, false)">{{ t('admin.assistant.dismiss') }}</button>
            </div>
          </article>
        </section>
        <form class="assistant__form" @submit.prevent="send">
          <label class="visually-hidden" for="assistant-input">{{ t('admin.assistant.yourMessage') }}</label>
          <textarea
            id="assistant-input"
            v-model="draft"
            rows="3"
            maxlength="8000"
            data-testid="assistant-input"
            :placeholder="t('admin.assistant.placeholder')"
            @keydown.ctrl.enter.prevent="send"
          />
          <button type="submit" :disabled="busy || !draft.trim()" data-testid="assistant-send">{{ t('admin.assistant.send') }}</button>
        </form>
      </div>
    </div>
  </section>
</template>

<style scoped>
.assistant__layout {
  display: grid;
  grid-template-columns: minmax(12rem, 18rem) minmax(0, 1fr);
  gap: 1rem;
  align-items: start;
}
.assistant__history ul {
  list-style: none;
  padding: 0;
  margin: 0.5rem 0 0;
}
.assistant__history li {
  display: flex;
  gap: 0.25rem;
}
.assistant__open {
  flex: 1;
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.assistant__current .assistant__open {
  font-weight: 600;
}
.assistant__log {
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 0.75rem;
  min-height: 16rem;
  max-height: 60vh;
  overflow-y: auto;
  background: #fafafb;
}
.assistant__turn {
  margin-bottom: 0.75rem;
}
.assistant__turn header {
  font-size: 0.8rem;
  color: #6b6b70;
}
.assistant__text {
  white-space: pre-wrap;
  margin: 0.15rem 0;
}
.assistant__turn--user .assistant__text {
  background: #eef3ff;
  padding: 0.4rem 0.6rem;
  border-radius: 4px;
}
.assistant__activity {
  font-size: 0.85rem;
  color: #55555a;
  margin: 0.25rem 0;
}
.assistant__activity--ko {
  color: #a4262c;
}
.assistant__args {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  max-height: 14rem;
  overflow: auto;
  background: #f4f4f6;
  padding: 0.4rem 0.6rem;
  border-radius: 4px;
  font-size: 0.8rem;
}
.assistant__action {
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 0.5rem 0.75rem;
  margin-bottom: 0.5rem;
}
.assistant__action header {
  display: flex;
  justify-content: space-between;
}
.assistant__action[data-status='FAILED'] .assistant__outcome {
  color: #a4262c;
}
.assistant__action-buttons {
  display: flex;
  gap: 0.5rem;
}
.assistant__form {
  display: grid;
  gap: 0.5rem;
  margin-top: 0.5rem;
}
.assistant__hint,
.assistant__empty,
.assistant__busy {
  color: #6b6b70;
}
.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
@media (max-width: 800px) {
  .assistant__layout {
    grid-template-columns: 1fr;
  }
}
</style>
