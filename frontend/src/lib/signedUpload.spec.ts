import { afterEach, describe, expect, it, vi } from 'vitest'
import { rejectionMessageKey, saveBlob, SIGNED_UPLOAD_REJECTIONS, signedDocumentFilename } from './signedUpload'

describe('signedUpload helpers (issue #10)', () => {
  it('maps every known rejection code to its own key and anything else to « unknown »', () => {
    for (const code of SIGNED_UPLOAD_REJECTIONS) {
      expect(rejectionMessageKey('p', code)).toBe(`p.rejections.${code}`)
    }
    expect(rejectionMessageKey('p', 'SOMETHING_NEW')).toBe('p.rejections.unknown')
    expect(rejectionMessageKey('p', undefined)).toBe('p.rejections.unknown')
    expect(rejectionMessageKey('p', null)).toBe('p.rejections.unknown')
  })

  it('derives a .pdf filename from the document label', () => {
    expect(signedDocumentFilename('Contrat')).toBe('Contrat.pdf')
    expect(signedDocumentFilename('Contrat.PDF')).toBe('Contrat.pdf')
    expect(signedDocumentFilename('Avenant n°2 / 2026: « final »')).toBe('Avenant n°2 - 2026- « final ».pdf')
    expect(signedDocumentFilename('  ..  ')).toBe('document.pdf')
    expect(signedDocumentFilename('')).toBe('document.pdf')
  })

  describe('saveBlob', () => {
    afterEach(() => {
      vi.restoreAllMocks()
      vi.useRealTimers()
    })

    it('clicks a temporary download link and releases the object URL later', () => {
      vi.useFakeTimers()
      URL.createObjectURL = vi.fn(() => 'blob:x')
      URL.revokeObjectURL = vi.fn()
      const attributes: Array<[string, string | null]> = []
      vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {
        const link = document.querySelector('a[download]')
        attributes.push(['download', link?.getAttribute('download') ?? null], ['href', link?.getAttribute('href') ?? null])
      })

      saveBlob(new Blob(['pdf']), 'Contrat.pdf')

      expect(attributes).toEqual([['download', 'Contrat.pdf'], ['href', 'blob:x']])
      expect(document.querySelector('a[download]')).toBeNull()
      vi.advanceTimersByTime(60_000)
      expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:x')
    })
  })
})
