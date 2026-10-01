import axios from 'axios'

/** TODO §86 (LC3) — assistant de conception : conversation, historique, état d'activation. */
export interface AssistantStatus {
  enabled: boolean
  model?: string | null
}

export interface ToolActivity {
  tool: string
  ok: boolean
  /** Arguments de l'appel (tronqués) : ce que l'assistant a demandé, visible de l'utilisateur. */
  arguments?: string
  summary: string
}

/** Écriture directe proposée par l'assistant : rien n'est écrit tant que l'utilisateur ne l'a pas appliquée. */
export interface AssistantAction {
  id: string
  tool: string
  arguments: string
  status: 'PENDING' | 'APPLYING' | 'APPLIED' | 'DISMISSED' | 'FAILED'
  outcome?: string | null
  createdAt: string
  resolvedAt?: string | null
}

export interface AssistantReply {
  conversationId: string
  reply: string
  activity: ToolActivity[]
  /** Au moins un brouillon à relire dans Admin → Brouillons. */
  producedDrafts: boolean
  proposals: AssistantAction[]
}

export interface ConversationSummary {
  id: string
  title: string
  createdAt: string
  updatedAt: string
  turns: number
}

export interface AssistantMessage {
  seq: number
  role: 'USER' | 'ASSISTANT' | 'TOOL'
  content: string
  toolName?: string | null
  createdAt: string
}

export interface ConversationDetail {
  conversation: ConversationSummary
  messages: AssistantMessage[]
}

export async function getAssistantStatus(): Promise<AssistantStatus> {
  const { data } = await axios.get<AssistantStatus>('/api/assistant/status')
  return data
}

export async function sendAssistantMessage(message: string, conversationId?: string | null): Promise<AssistantReply> {
  const { data } = await axios.post<AssistantReply>('/api/assistant/messages', { message, conversationId: conversationId ?? null })
  return data
}

export async function listConversations(): Promise<ConversationSummary[]> {
  const { data } = await axios.get<ConversationSummary[]>('/api/assistant/conversations')
  return data
}

export async function getConversation(id: string): Promise<ConversationDetail> {
  const { data } = await axios.get<ConversationDetail>(`/api/assistant/conversations/${id}`)
  return data
}

export async function deleteConversation(id: string): Promise<void> {
  await axios.delete(`/api/assistant/conversations/${id}`)
}

export async function listActions(conversationId: string): Promise<AssistantAction[]> {
  const { data } = await axios.get<AssistantAction[]>(`/api/assistant/conversations/${conversationId}/actions`)
  return data
}

export async function applyAction(conversationId: string, actionId: string): Promise<AssistantAction> {
  const { data } = await axios.post<AssistantAction>(`/api/assistant/conversations/${conversationId}/actions/${actionId}/apply`)
  return data
}

export async function dismissAction(conversationId: string, actionId: string): Promise<AssistantAction> {
  const { data } = await axios.post<AssistantAction>(`/api/assistant/conversations/${conversationId}/actions/${actionId}/dismiss`)
  return data
}
