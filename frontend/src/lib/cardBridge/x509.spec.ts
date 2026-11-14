import { describe, expect, it } from 'vitest'
import { AUTH_CERT, SIGNING_CERT } from './fixtures'
import { summarizeCertificate } from './x509'

// Encodeur DER minimal pour fabriquer des certificats ciblés (le lecteur, lui, ne lit que ce dont il a besoin).
const bytes = (...parts: (number[] | Uint8Array)[]) => parts.flatMap((p) => Array.from(p))
function tlv(tag: number, ...content: number[][]): number[] {
  const body = bytes(...content)
  if (body.length < 0x80) return [tag, body.length, ...body]
  if (body.length < 0x100) return [tag, 0x81, body.length, ...body]
  return [tag, 0x82, body.length >> 8, body.length & 0xff, ...body]
}
const text = (tag: number, value: string) => tlv(tag, Array.from(new TextEncoder().encode(value)))
const oid = (...b: number[]) => tlv(0x06, b)
const attribute = (o: number[], value: number[]) => tlv(0x31, tlv(0x30, o, value))
const CN = oid(0x55, 0x04, 0x03)
const SN = oid(0x55, 0x04, 0x04)
const GN = oid(0x55, 0x04, 0x2a)
const SERIAL = oid(0x55, 0x04, 0x05)
const KEY_USAGE = oid(0x55, 0x1d, 0x0f)
const ALGORITHM = tlv(0x30, oid(0x2a, 0x86, 0x48, 0xce, 0x3d, 0x04, 0x03, 0x02))
const keyUsage = (firstByte: number, critical = true) =>
  tlv(0x30, KEY_USAGE, critical ? tlv(0x01, [0xff]) : [], tlv(0x04, tlv(0x03, [0x06, firstByte, 0x00])))

function certificate(opts: {
  subject?: number[][]
  notAfter?: number[]
  extensions?: number[][]
  version?: boolean
  uniqueIds?: boolean
} = {}): number[] {
  const subject = tlv(0x30, ...(opts.subject ?? [attribute(CN, text(0x0c, 'Alice Martin'))]))
  const notBefore = text(0x17, '260101000000Z')
  const notAfter = opts.notAfter ?? text(0x17, '361111130449Z')
  const extensions = opts.extensions ? tlv(0xa3, tlv(0x30, ...opts.extensions)) : []
  const tbs = tlv(
    0x30,
    opts.version === false ? [] : tlv(0xa0, tlv(0x02, [2])),
    tlv(0x02, [1]),
    ALGORITHM,
    tlv(0x30, attribute(CN, text(0x0c, 'Émetteur'))),
    tlv(0x30, notBefore, notAfter),
    subject,
    tlv(0x30, ALGORITHM, tlv(0x03, [0, 4, 1, 2])),
    opts.uniqueIds ? tlv(0xa1, [0, 1]) : [],
    extensions,
  )
  return tlv(0x30, tbs, ALGORITHM, tlv(0x03, [0, 1, 2, 3]))
}
const b64 = (der: number[]) => btoa(String.fromCharCode(...der))
const summary = (der: number[]) => summarizeCertificate(b64(der))

describe('summarizeCertificate (lecteur ASN.1 minimal)', () => {
  it('reads the holder, the end of validity and the key usage of a real signing certificate', () => {
    expect(summarizeCertificate(SIGNING_CERT)).toEqual({ holder: 'Alice Martin', notAfter: '2036-11-11T13:04:49Z', nonRepudiation: true })
  })

  it('sees that an authentication certificate has no non-repudiation', () => {
    expect(summarizeCertificate(AUTH_CERT)).toMatchObject({ holder: 'Alice Martin', nonRepudiation: false })
  })

  it('never exposes the serialNumber attribute (national number), in either direction', () => {
    for (const cert of [SIGNING_CERT, AUTH_CERT]) {
      expect(JSON.stringify(summarizeCertificate(cert))).not.toContain('85010112345')
    }
    const withSerial = summary(certificate({ subject: [attribute(SERIAL, text(0x13, '85010112345')), attribute(CN, text(0x0c, 'Bob Dupont'))] }))
    expect(withSerial?.holder).toBe('Bob Dupont')
    // Un sujet réduit au seul serialNumber ne donne aucun nom : jamais le numéro en repli.
    const onlySerial = summary(certificate({ subject: [attribute(SERIAL, text(0x13, '85010112345'))] }))
    expect(onlySerial?.holder).toBeNull()
    expect(JSON.stringify(onlySerial)).not.toContain('85010112345')
  })

  it('prefers givenName + surname and falls back on commonName', () => {
    expect(summary(certificate({ subject: [attribute(CN, text(0x0c, 'Alice Martin (Signature)')), attribute(GN, text(0x0c, 'Alice')), attribute(SN, text(0x0c, 'Martin'))] }))?.holder).toBe('Alice Martin')
    expect(summary(certificate({ subject: [attribute(CN, text(0x0c, 'Alice Martin (Signature)')), attribute(GN, text(0x0c, 'Alice'))] }))?.holder).toBe('Alice Martin (Signature)')
    expect(summary(certificate({ subject: [attribute(SN, text(0x0c, 'Martin'))] }))?.holder).toBeNull()
  })

  it('decodes UTF-8, PrintableString and BMPString names', () => {
    expect(summary(certificate({ subject: [attribute(CN, text(0x0c, 'Zoé Lefèvre'))] }))?.holder).toBe('Zoé Lefèvre')
    expect(summary(certificate({ subject: [attribute(CN, text(0x13, 'Plain Name'))] }))?.holder).toBe('Plain Name')
    expect(summary(certificate({ subject: [attribute(CN, tlv(0x1e, [0, 0x5a, 0, 0x6f, 0x00, 0xe9]))] }))?.holder).toBe('Zoé')
  })

  it('cleans control characters and bounds the length of a name', () => {
    expect(summary(certificate({ subject: [attribute(CN, text(0x0c, 'A\u0000B\nC'))] }))?.holder).toBe('A B C')
    expect(summary(certificate({ subject: [attribute(CN, text(0x0c, 'x'.repeat(300)))] }))?.holder).toHaveLength(200)
  })

  it('reads UTCTime (with the 1950 pivot) and GeneralizedTime', () => {
    expect(summary(certificate({ notAfter: text(0x17, '991231235959Z') }))?.notAfter).toBe('1999-12-31T23:59:59Z')
    expect(summary(certificate({ notAfter: text(0x17, '490101000000Z') }))?.notAfter).toBe('2049-01-01T00:00:00Z')
    expect(summary(certificate({ notAfter: text(0x18, '20501231235959Z') }))?.notAfter).toBe('2050-12-31T23:59:59Z')
  })

  it('reads the key usage with or without a « critical » flag and ignores a certificate without that extension', () => {
    expect(summary(certificate({ extensions: [keyUsage(0x40)] }))?.nonRepudiation).toBe(true)
    expect(summary(certificate({ extensions: [keyUsage(0x40, false)] }))?.nonRepudiation).toBe(true)
    expect(summary(certificate({ extensions: [keyUsage(0xc0)] }))?.nonRepudiation).toBe(true)
    expect(summary(certificate({ extensions: [keyUsage(0x80)] }))?.nonRepudiation).toBe(false)
    expect(summary(certificate({ extensions: [tlv(0x30, oid(0x55, 0x1d, 0x13), tlv(0x04, tlv(0x30)))] }))?.nonRepudiation).toBeNull()
    expect(summary(certificate())?.nonRepudiation).toBeNull()
  })

  it('accepts a certificate without version and one with unique identifiers', () => {
    expect(summary(certificate({ version: false }))?.holder).toBe('Alice Martin')
    expect(summary(certificate({ uniqueIds: true, extensions: [keyUsage(0x40)] }))?.nonRepudiation).toBe(true)
  })

  it('fails cleanly (null, never an exception) on anything abnormal', () => {
    const good = certificate()
    expect(summarizeCertificate('')).toBeNull()
    expect(summarizeCertificate('not base64 !!!')).toBeNull()
    expect(summarizeCertificate(btoa('hello'))).toBeNull()
    expect(summarizeCertificate(null as unknown as string)).toBeNull()
    // Tronqué à tous les points.
    for (let cut = 0; cut < good.length; cut += 7) expect(summary(good.slice(0, cut))).toBeNull()
    // Longueur déclarée plus grande que le contenu, longueur indéfinie, longueur démesurée.
    expect(summary([0x30, 0x82, 0x7f, 0xff, 0x30, 0x00])).toBeNull()
    expect(summary([0x30, 0x80, 0x30, 0x00, 0x00, 0x00])).toBeNull()
    expect(summary([0x30, 0x84, 0xff, 0xff, 0xff, 0xff])).toBeNull()
    // Mauvaise structure : une séquence là où l'on attend un entier, étiquette longue.
    expect(summary(tlv(0x30, tlv(0x30, tlv(0x30))))).toBeNull()
    expect(summary([0x30, 0x03, 0x1f, 0x01, 0x00])).toBeNull()
    // Date invalide.
    expect(summary(certificate({ notAfter: text(0x17, '991399250000Z') }))).toBeNull()
    expect(summary(certificate({ notAfter: text(0x0c, 'demain') }))).toBeNull()
  })

  it('refuses an oversized input without decoding it', () => {
    expect(summarizeCertificate('A'.repeat(30_000))).toBeNull()
    expect(summary(certificate({ subject: [attribute(CN, text(0x0c, 'x'.repeat(20_000)))] }))).toBeNull()
  })

  it('bounds the number of attributes and extensions it walks through', () => {
    const many = Array.from({ length: 80 }, () => attribute(CN, text(0x0c, 'x')))
    expect(summary(certificate({ subject: many }))).toBeNull()
    const manyExtensions = Array.from({ length: 80 }, () => tlv(0x30, oid(0x55, 0x1d, 0x13), tlv(0x04, tlv(0x30))))
    expect(summary(certificate({ extensions: manyExtensions }))).toBeNull()
  })
})
