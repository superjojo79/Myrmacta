// Issue #10 — signature « apportée » : le signataire télécharge le PDF, le signe hors de l'application (carte eID) puis
// le téléverse. Ce module réunit ce que le portail et « Mes signatures » partagent : les motifs de refus du serveur
// (propriété `code` d'un 422 `application/problem+json`) et le nom du fichier téléchargé.

/** Valeurs de `SignedUploadRejection` côté serveur : une clé i18n par valeur dans chaque catalogue. */
export const SIGNED_UPLOAD_REJECTIONS = [
  'NOT_A_PDF',
  'DOCUMENT_MISMATCH',
  'STALE_DOCUMENT',
  'NO_NEW_SIGNATURE',
  'SEVERAL_SIGNATURES',
  'CONTENT_MODIFIED',
  'SIGNATURE_INVALID',
  'CERTIFICATE_UNTRUSTED',
  'CERTIFICATE_NOT_QUALIFIED',
  'CERTIFICATE_REVOKED',
  'REVOCATION_UNAVAILABLE',
  'DUPLICATE_SIGNER',
] as const

export type SignedUploadRejection = (typeof SIGNED_UPLOAD_REJECTIONS)[number]

/** Clé du message d'un refus sous `prefix` : le message du code, ou `unknown` pour un code absent ou inconnu. */
export function rejectionMessageKey(prefix: string, code: string | null | undefined): string {
  const known = SIGNED_UPLOAD_REJECTIONS.find((c) => c === code)
  return known ? `${prefix}.rejections.${known}` : `${prefix}.rejections.unknown`
}

/** `Contrat` → `Contrat.pdf` : le libellé du document sans caractère interdit dans un nom de fichier. */
export function signedDocumentFilename(label: string): string {
  const base = label
    .replace(/[\\/:*?"<>|\u0000-\u001f]+/g, '-')
    .replace(/\.pdf$/i, '')
    .trim()
    .replace(/^[-.\s]+|[-.\s]+$/g, '')
  return `${base || 'document'}.pdf`
}

/** Enregistre un Blob sous ce nom (lien éphémère à attribut `download`). */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 60_000)
}
