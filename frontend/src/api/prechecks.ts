import axios from 'axios'

// TODO §106 (issue #211, lot C) — pré-vérifications IA d'un dossier : lire (droit de lecture), relancer et décider
// (EDIT), exporter le journal (TENANT_ADMIN). L'API n'existe que si la fonction est activée sur l'instance.

export type PrecheckStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'
export type PrecheckTrigger = 'WORKFLOW' | 'MANUAL' | 'MCP'
export type FindingKind = 'MISSING' | 'INCONSISTENT' | 'TO_CHECK' | 'OK'
export type FindingOrigin = 'DETERMINISTIC' | 'MODEL'
export type FindingSourceType = 'FIELD' | 'PIECE' | 'RULE' | 'QUESTION'
export type FindingDecision = 'ACCEPTED' | 'DISMISSED'

export interface PrecheckFinding {
  id: string
  kind: FindingKind
  origin: FindingOrigin
  sourceType: FindingSourceType
  sourceRef: string
  message: string
  confidence: number | null
  decision: FindingDecision | null
  decidedBy: string | null
  decidedAt: string | null
}

export interface PrecheckRun {
  id: string
  trigger: PrecheckTrigger
  requestedBy: string
  status: PrecheckStatus
  failure: string | null
  provider: string | null
  model: string | null
  promptVersion: string | null
  summary: string | null
  tokensIn: number
  tokensOut: number
  discarded: number
  createdAt: string
  finishedAt: string | null
  findings: PrecheckFinding[]
}

function base(dossierId: string): string {
  return `/api/dossiers/${encodeURIComponent(dossierId)}/prechecks`
}

/** Les dernières exécutions, la plus récente d'abord ; `null` si la fonction est désactivée (404). */
export async function listPrechecks(dossierId: string): Promise<PrecheckRun[] | null> {
  const response = await axios.get<PrecheckRun[]>(base(dossierId), {
    validateStatus: (status) => status === 200 || status === 404,
  })
  return response.status === 404 ? null : response.data
}

/** Relance avec la spécification de la dernière exécution ; traitée en tâche de fond (202, `PENDING`). */
export async function rerunPrecheck(dossierId: string): Promise<PrecheckRun> {
  const { data } = await axios.post<PrecheckRun>(base(dossierId))
  return data
}

/** `null` annule la décision précédente. */
export async function decidePrecheckFinding(
  dossierId: string,
  runId: string,
  findingId: string,
  decision: FindingDecision | null,
): Promise<PrecheckFinding> {
  const { data } = await axios.put<PrecheckFinding>(
    `${base(dossierId)}/${encodeURIComponent(runId)}/findings/${encodeURIComponent(findingId)}/decision`,
    { decision },
  )
  return data
}

/** Journal complet d'une exécution (TENANT_ADMIN), enregistré en JSON. */
export async function downloadPrecheckExport(dossierId: string, runId: string): Promise<void> {
  const { data } = await axios.get<unknown>(`${base(dossierId)}/${encodeURIComponent(runId)}/export`)
  const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }))
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `pre-verification-${runId}.json`
  anchor.click()
  URL.revokeObjectURL(url)
}
