<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { toProblemDetail } from '@/api/http'
import {
  deleteConversation,
  getAssistantStatus,
  getConversation,
  listConversations,
  sendAssistantMessage,
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

const status = ref<AssistantStatus | null>(null)
const conversations = ref<ConversationSummary[]>([])
const conversationId = ref<string | null>(null)
const turns = ref<Turn[]>([])
const draft = ref('')
const busy = ref(false)
const error = ref<string | null>(null)
const log = ref<HTMLElement | null>(null)

async function loadList() {
  try {
    conversations.value = await listConversations()
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? "Chargement de l'historique impossible"
  }
}

onMounted(async () => {
  try {
    status.value = await getAssistantStatus()
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? "État de l'assistant indisponible"
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
    else if (m.role === 'TOOL') pending.push({ tool: m.toolName ?? 'outil', ok: !m.content.startsWith('error:'), summary: m.content.slice(0, 300) })
    else {
      out.push({ role: 'ASSISTANT', text: m.content, activity: pending })
      pending = []
    }
  }
  return out
}

async function open(id: string) {
  error.value = null
  try {
    const detail = await getConversation(id)
    conversationId.value = id
    turns.value = fromMessages(detail.messages)
    await scroll()
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? 'Conversation introuvable'
  }
}

function fresh() {
  conversationId.value = null
  turns.value = []
  error.value = null
}

async function remove(id: string) {
  try {
    await deleteConversation(id)
    if (conversationId.value === id) fresh()
    await loadList()
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? 'Suppression impossible'
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
    await loadList()
  } catch (err) {
    error.value = toProblemDetail(err).detail ?? "L'assistant n'a pas pu répondre"
  } finally {
    busy.value = false
    await scroll()
  }
}
</script>

<template>
  <section class="assistant" data-testid="assistant-view">
    <h1>Assistant de conception</h1>
    <p v-if="error" class="error" role="alert" data-testid="assistant-error">{{ error }}</p>

    <p v-if="status && !status.enabled" class="assistant__disabled" data-testid="assistant-disabled">
      L'assistant n'est pas activé sur cette instance. Un administrateur technique doit configurer un modèle
      (<code>APP_ASSISTANT_ENABLED</code>, <code>APP_ASSISTANT_API_KEY</code>) — voir DEPLOIEMENT.md.
    </p>

    <div v-else-if="status" class="assistant__layout">
      <aside class="assistant__history" aria-label="Conversations">
        <button type="button" data-testid="assistant-new" @click="fresh">Nouvelle conversation</button>
        <ul>
          <li v-for="c in conversations" :key="c.id" :class="{ 'assistant__current': c.id === conversationId }">
            <button type="button" class="assistant__open" data-testid="assistant-open" @click="open(c.id)">{{ c.title }}</button>
            <button type="button" class="assistant__remove" :aria-label="`Supprimer « ${c.title} »`" @click="remove(c.id)">✕</button>
          </li>
        </ul>
      </aside>

      <div class="assistant__chat">
        <p class="assistant__hint">
          Décrivez le type de dossier, le processus, les formulaires ou les règles voulus. Ce que l'assistant écrit pour un processus ou
          une table de décision est un <strong>brouillon</strong> : vous le relisez avant publication. Il n'a accès à aucune donnée de
          dossier et n'écrit jamais de secret.
        </p>
        <div ref="log" class="assistant__log" role="log" aria-live="polite" data-testid="assistant-log">
          <p v-if="turns.length === 0" class="assistant__empty">Posez votre première question.</p>
          <article v-for="(t, i) in turns" :key="i" :class="['assistant__turn', `assistant__turn--${t.role.toLowerCase()}`]">
            <header>{{ t.role === 'USER' ? 'Vous' : 'Assistant' }}</header>
            <!-- Texte brut : la réponse d'un modèle n'est jamais interprétée comme du HTML. -->
            <p class="assistant__text">{{ t.text }}</p>
            <ul v-if="t.activity?.length" class="assistant__activity">
              <li v-for="(a, j) in t.activity" :key="j" :class="{ 'assistant__activity--ko': !a.ok }">
                <code>{{ a.tool }}</code> — {{ a.ok ? 'fait' : 'refusé' }}
              </li>
            </ul>
            <p v-if="t.drafts" class="assistant__drafts" data-testid="assistant-drafts">
              Des brouillons attendent votre relecture :
              <RouterLink to="/admin/drafts">Admin → Brouillons</RouterLink>
            </p>
          </article>
          <p v-if="busy" class="assistant__busy" data-testid="assistant-busy">L'assistant travaille…</p>
        </div>
        <form class="assistant__form" @submit.prevent="send">
          <label class="visually-hidden" for="assistant-input">Votre message</label>
          <textarea
            id="assistant-input"
            v-model="draft"
            rows="3"
            maxlength="8000"
            data-testid="assistant-input"
            placeholder="Ex. : Un type « Sinistre » avec numéro de contrat et montant, et un processus de validation à deux étapes"
            @keydown.ctrl.enter.prevent="send"
          />
          <button type="submit" :disabled="busy || !draft.trim()" data-testid="assistant-send">Envoyer</button>
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
