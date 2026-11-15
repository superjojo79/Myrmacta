// Lecteur ASN.1/DER minimal et borné pour libeller un certificat dans la page (lot 3c) : titulaire, fin de validité,
// usage « non-répudiation ». Aucune validation de certificat : la confiance est l'affaire du serveur. Toute entrée
// anormale (trop longue, tronquée, longueur incohérente) fait échouer proprement : `null`, jamais d'exception.
//
// Le numéro de registre national figure dans l'attribut `serialNumber` du sujet : il n'est ni décodé, ni affiché, ni
// conservé (l'attribut est sauté sans lire sa valeur).

export interface CertificateSummary {
  /** `givenName` + `surname`, à défaut `commonName` ; `null` si le sujet n'en porte aucun. */
  holder: string | null
  /** Fin de validité, instant ISO-8601 UTC (`2036-11-11T13:04:49Z`). */
  notAfter: string | null
  /** Bit « non-répudiation » de l'extension keyUsage ; `null` quand le certificat n'a pas cette extension. */
  nonRepudiation: boolean | null
}

const MAX_BASE64_LENGTH = 24_000
const MAX_DER_LENGTH = 16_384
const MAX_ELEMENTS = 64
const MAX_TEXT_LENGTH = 200

class Malformed extends Error {}

interface Tlv {
  tag: number
  /** Début et fin du contenu. */
  start: number
  end: number
}

function readTlv(bytes: Uint8Array, pos: number, limit: number): Tlv {
  if (pos + 2 > limit) throw new Malformed()
  const tag = bytes[pos]!
  if ((tag & 0x1f) === 0x1f) throw new Malformed() // numéro d'étiquette long : absent d'un certificat usuel
  const first = bytes[pos + 1]!
  let cursor = pos + 2
  let length = first
  if (first >= 0x80) {
    const count = first & 0x7f
    if (count === 0 || count > 3) throw new Malformed() // longueur indéfinie ou démesurée
    if (cursor + count > limit) throw new Malformed()
    length = 0
    for (let i = 0; i < count; i++) length = length * 256 + bytes[cursor++]!
  }
  const end = cursor + length
  if (end > limit) throw new Malformed()
  return { tag, start: cursor, end }
}

function expect(tlv: Tlv, tag: number): Tlv {
  if (tlv.tag !== tag) throw new Malformed()
  return tlv
}

const OID_COMMON_NAME = [0x55, 0x04, 0x03]
const OID_SURNAME = [0x55, 0x04, 0x04]
const OID_GIVEN_NAME = [0x55, 0x04, 0x2a]
const OID_KEY_USAGE = [0x55, 0x1d, 0x0f]

function oidIs(bytes: Uint8Array, tlv: Tlv, oid: number[]): boolean {
  return tlv.end - tlv.start === oid.length && oid.every((b, i) => bytes[tlv.start + i] === b)
}

function decodeText(bytes: Uint8Array, tlv: Tlv): string | null {
  const content = bytes.subarray(tlv.start, tlv.end)
  let text: string
  switch (tlv.tag) {
    case 0x0c: // UTF8String
      text = new TextDecoder('utf-8', { fatal: true }).decode(content)
      break
    case 0x13: // PrintableString
    case 0x14: // T61String (lu comme latin-1)
    case 0x16: // IA5String
      text = Array.from(content, (b) => String.fromCharCode(b)).join('')
      break
    case 0x1e: // BMPString (UTF-16 gros-boutiste)
      if (content.length % 2 !== 0) throw new Malformed()
      text = Array.from({ length: content.length / 2 }, (_, i) => String.fromCharCode(content[2 * i]! * 256 + content[2 * i + 1]!)).join('')
      break
    default:
      return null
  }
  const clean = text.replace(/[\u0000-\u001f\u007f]/g, ' ').trim()
  return clean ? clean.slice(0, MAX_TEXT_LENGTH) : null
}

function parseTime(bytes: Uint8Array, tlv: Tlv): string {
  const text = Array.from(bytes.subarray(tlv.start, tlv.end), (b) => String.fromCharCode(b)).join('')
  let year: string
  let rest: string
  if (tlv.tag === 0x17 && /^\d{10}(\d{2})?Z$/.test(text)) {
    const yy = Number(text.slice(0, 2))
    year = String(yy >= 50 ? 1900 + yy : 2000 + yy)
    rest = text.slice(2)
  } else if (tlv.tag === 0x18 && /^\d{14}Z$/.test(text)) {
    year = text.slice(0, 4)
    rest = text.slice(4)
  } else {
    throw new Malformed()
  }
  const [month, day, hour, minute] = [rest.slice(0, 2), rest.slice(2, 4), rest.slice(4, 6), rest.slice(6, 8)]
  const second = /^\d{2}Z$/.test(rest.slice(8)) ? rest.slice(8, 10) : '00'
  const iso = `${year}-${month}-${day}T${hour}:${minute}:${second}Z`
  if (Number.isNaN(Date.parse(iso))) throw new Malformed()
  return iso
}

function decodeBase64(base64: string): Uint8Array {
  const binary = atob(base64)
  if (binary.length > MAX_DER_LENGTH) throw new Malformed()
  return Uint8Array.from(binary, (c) => c.charCodeAt(0))
}

function parse(bytes: Uint8Array): CertificateSummary {
  const certificate = expect(readTlv(bytes, 0, bytes.length), 0x30)
  const tbs = expect(readTlv(bytes, certificate.start, certificate.end), 0x30)
  let pos = tbs.start
  const next = (): Tlv => {
    const tlv = readTlv(bytes, pos, tbs.end)
    pos = tlv.end
    return tlv
  }
  let element = next()
  if (element.tag === 0xa0) element = next() // version [0]
  expect(element, 0x02) // numéro de série
  expect(next(), 0x30) // algorithme de signature
  expect(next(), 0x30) // émetteur
  const validity = expect(next(), 0x30)
  const notBefore = readTlv(bytes, validity.start, validity.end)
  const notAfterTlv = readTlv(bytes, notBefore.end, validity.end)
  parseTime(bytes, notBefore)
  const notAfter = parseTime(bytes, notAfterTlv)

  // Sujet : seuls commonName, givenName et surname sont décodés ; tout autre attribut (dont serialNumber) est sauté.
  const subject = expect(next(), 0x30)
  let commonName: string | null = null
  let givenName: string | null = null
  let surname: string | null = null
  let rdnPos = subject.start
  for (let n = 0; rdnPos < subject.end; n++) {
    if (n >= MAX_ELEMENTS) throw new Malformed()
    const rdn = expect(readTlv(bytes, rdnPos, subject.end), 0x31)
    rdnPos = rdn.end
    let atvPos = rdn.start
    for (let m = 0; atvPos < rdn.end; m++) {
      if (m >= MAX_ELEMENTS) throw new Malformed()
      const atv = expect(readTlv(bytes, atvPos, rdn.end), 0x30)
      atvPos = atv.end
      const oid = expect(readTlv(bytes, atv.start, atv.end), 0x06)
      const wanted = oidIs(bytes, oid, OID_COMMON_NAME) ? 'cn' : oidIs(bytes, oid, OID_GIVEN_NAME) ? 'gn' : oidIs(bytes, oid, OID_SURNAME) ? 'sn' : null
      if (!wanted) continue
      const text = decodeText(bytes, readTlv(bytes, oid.end, atv.end))
      if (wanted === 'cn') commonName ??= text
      else if (wanted === 'gn') givenName ??= text
      else surname ??= text
    }
  }
  const holder = givenName && surname ? `${givenName} ${surname}`.slice(0, MAX_TEXT_LENGTH) : commonName

  expect(next(), 0x30) // clé publique
  let nonRepudiation: boolean | null = null
  while (pos < tbs.end) {
    const optional = next()
    if (optional.tag !== 0xa3) continue // identifiants uniques [1] et [2]
    const extensions = expect(readTlv(bytes, optional.start, optional.end), 0x30)
    let extPos = extensions.start
    for (let n = 0; extPos < extensions.end; n++) {
      if (n >= MAX_ELEMENTS) throw new Malformed()
      const extension = expect(readTlv(bytes, extPos, extensions.end), 0x30)
      extPos = extension.end
      const oid = expect(readTlv(bytes, extension.start, extension.end), 0x06)
      if (!oidIs(bytes, oid, OID_KEY_USAGE)) continue
      let value = readTlv(bytes, oid.end, extension.end)
      if (value.tag === 0x01) value = readTlv(bytes, value.end, extension.end) // « critical »
      const octets = expect(value, 0x04)
      const bits = expect(readTlv(bytes, octets.start, octets.end), 0x03)
      if (bits.end - bits.start < 2) throw new Malformed()
      // keyUsage : bit 0 digitalSignature, bit 1 nonRepudiation (second bit de poids fort du premier octet).
      nonRepudiation = (bytes[bits.start + 1]! & 0x40) !== 0
    }
  }
  return { holder, notAfter, nonRepudiation }
}

/** Résumé d'un certificat DER en base64 ; `null` si la lecture échoue pour quelque raison que ce soit. */
export function summarizeCertificate(base64: string): CertificateSummary | null {
  try {
    if (typeof base64 !== 'string' || base64.length === 0 || base64.length > MAX_BASE64_LENGTH) return null
    return parse(decodeBase64(base64))
  } catch {
    return null
  }
}
