import axios from 'axios'

/** TODO §113 (D-MAILROOM-DEPOSIT) — point de dépôt de l'écran « Déposer ». */
export interface DepositPoint {
  id: string
  name: string
  dossierTypeCode: string
}

/** Sort d'un fichier déposé : le courrier créé, ou la raison du refus. */
export interface DepositResult {
  filename: string
  dossierId: string | null
  reference: string | null
  refusal: string | null
}

export const MAX_DEPOSIT_FILES = 20

export async function listDepositPoints(): Promise<DepositPoint[]> {
  const { data } = await axios.get<DepositPoint[]>('/api/mail-deposit-points')
  return data
}

export async function depositMail(
  pointId: string,
  files: File[],
  fields: { expediteur?: string; objet?: string },
): Promise<DepositResult[]> {
  const form = new FormData()
  for (const file of files) form.append('files', file)
  if (fields.expediteur?.trim()) form.append('expediteur', fields.expediteur.trim())
  if (fields.objet?.trim()) form.append('objet', fields.objet.trim())
  const { data } = await axios.post<{ results: DepositResult[] }>(
    `/api/mail-deposit-points/${encodeURIComponent(pointId)}/deposits`,
    form,
  )
  return data.results
}
