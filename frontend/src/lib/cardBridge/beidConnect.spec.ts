import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { BEID_MAC, BEID_PINPAD_SIGN_TIMEOUT_MS, BEID_TIMEOUT_MS, createBeidConnectBridge, isBeidConnectPresent } from './beidConnect'
import { AUTH_CERT, SIGNING_CERT } from './fixtures'
import { CardBridgeError, type CardBridge, type CardChoice } from './types'

type Fn = (...args: unknown[]) => Promise<unknown>

/** Faux `window.EIDChromeExt` : chaque méthode est un espion à promesse. */
function fakeWindow(methods: Partial<Record<'getVersion' | 'getUserCertificates' | 'getUserCertificateChain' | 'sign', Fn>> = {}) {
  const spies = {
    getVersion: vi.fn(methods.getVersion ?? (() => Promise.resolve({ result: 'OK', version: '2.12' }))),
    getUserCertificates: vi.fn(
      methods.getUserCertificates ??
        (() =>
          Promise.resolve({
            result: 'OK',
            Readers: [{ ReaderName: 'R', ReaderType: 'standard', cardType: 'BE', certificates: ['TEVBRg=='] }],
          })),
    ),
    getUserCertificateChain: vi.fn(
      methods.getUserCertificateChain ??
        (() => Promise.resolve({ result: 'OK', certificateChain: { rootCA: 'Uk9PVA==', subCA: ['U1VC', 'U1VC Mg=='] } })),
    ),
    sign: vi.fn(methods.sign ?? (() => Promise.resolve({ result: 'OK', signature: 'U0lH' }))),
  }
  let constructed = 0
  class Ext {
    constructor() {
      constructed++
    }
    getVersion = spies.getVersion
    getUserCertificates = spies.getUserCertificates
    getUserCertificateChain = spies.getUserCertificateChain
    sign = spies.sign
  }
  return { win: { EIDChromeExt: Ext } as unknown as Window, spies, constructed: () => constructed }
}

async function rejection(promise: Promise<unknown>): Promise<CardBridgeError> {
  const e = await promise.then(
    () => null,
    (err: unknown) => err,
  )
  expect(e).toBeInstanceOf(CardBridgeError)
  return e as CardBridgeError
}

async function firstChoice(bridge: CardBridge): Promise<CardChoice> {
  return (await bridge.listChoices())[0]!
}


describe('BeIDConnect bridge', () => {
  it('is detected by the EIDChromeExt constructor only', () => {
    expect(isBeidConnectPresent({} as Window)).toBe(false)
    expect(isBeidConnectPresent({ EIDChromeExt: {} } as unknown as Window)).toBe(false)
    expect(isBeidConnectPresent(fakeWindow().win)).toBe(true)
  })

  it('detects the extension through getVersion and reports it unavailable when absent', async () => {
    const { win, spies } = fakeWindow()
    await createBeidConnectBridge('fr', win).detect()
    expect(spies.getVersion).toHaveBeenCalledTimes(1)

    expect((await rejection(createBeidConnectBridge('fr', {} as Window).detect())).kind).toBe('unavailable')
  })

  it('lists the choices without the chain, then reads the chain of the chosen one (intermediates first, root last)', async () => {
    const { win, spies } = fakeWindow()
    const bridge = createBeidConnectBridge('FR-BE', win)

    const choices = await bridge.listChoices()
    expect(choices).toEqual([
      { id: '0:0', certificate: { certificate: 'TEVBRg==', certificateChain: [] }, readerName: 'R', holder: null, notAfter: null, needsPin: true },
    ])
    expect(spies.getUserCertificates).toHaveBeenCalledWith('fr', BEID_MAC, 'NONREPUDIATION')
    expect(spies.getUserCertificateChain).not.toHaveBeenCalled()

    await expect(bridge.certificateOf(choices[0]!)).resolves.toEqual({
      certificate: 'TEVBRg==',
      certificateChain: ['U1VC', 'U1VC Mg==', 'Uk9PVA=='],
    })
    expect(spies.getUserCertificateChain).toHaveBeenCalledWith('fr', BEID_MAC, 'TEVBRg==')
    expect(BEID_MAC).toBe('0123456789ABCDEF0123456789ABCDEF')
  })

  it('asks the page for the PIN on a standard reader and never on a pinpad reader', async () => {
    expect((await firstChoice(createBeidConnectBridge('fr', fakeWindow().win))).needsPin).toBe(true)

    const pinpad = fakeWindow({
      getUserCertificates: () =>
        Promise.resolve({ result: 'OK', Readers: [{ ReaderType: 'pinpad', certificates: ['TEVBRg=='] }] }),
    })
    const bridge = createBeidConnectBridge('fr', pinpad.win)
    const choice = await firstChoice(bridge)
    expect(choice.needsPin).toBe(false)
    await bridge.sign(choice, 'RElH', 'SHA-256', 'must-not-be-forwarded')
    expect(pinpad.spies.sign).toHaveBeenCalledWith('fr', BEID_MAC, 'TEVBRg==', 'SHA256', 'RElH', null)
  })

  it('asks for the PIN when the reader type is unknown', async () => {
    const unknown = fakeWindow({
      getUserCertificates: () => Promise.resolve({ result: 'OK', Readers: [{ certificates: ['TEVBRg=='] }] }),
    })
    expect((await firstChoice(createBeidConnectBridge('fr', unknown.win))).needsPin).toBe(true)
  })

  it('offers one choice per reader and certificate, each with the reader type of its own reader', async () => {
    const { win } = fakeWindow({
      getUserCertificates: () =>
        Promise.resolve({
          result: 'OK',
          Readers: [
            { ReaderName: 'Lecteur A', ReaderType: 'standard', certificates: ['QUFB'] },
            { ReaderName: 'Lecteur B', ReaderType: 'pinpad', certificates: ['QkJC', 'Q0ND'] },
            { ReaderName: 'Lecteur vide', ReaderType: 'standard', certificates: [] },
          ],
        }),
    })
    const choices = await createBeidConnectBridge('fr', win).listChoices()

    expect(choices.map((c) => [c.id, c.readerName, c.certificate.certificate, c.needsPin])).toEqual([
      ['0:0', 'Lecteur A', 'QUFB', true],
      ['1:0', 'Lecteur B', 'QkJC', false],
      ['1:1', 'Lecteur B', 'Q0ND', false],
    ])
  })

  it('labels each choice from its certificate and never exposes the national number', async () => {
    const { win } = fakeWindow({
      getUserCertificates: () => Promise.resolve({ result: 'OK', Readers: [{ ReaderName: 'R', ReaderType: 'standard', certificates: [SIGNING_CERT] }] }),
    })
    const [choice] = await createBeidConnectBridge('fr', win).listChoices()
    expect(choice).toMatchObject({ holder: 'Alice Martin', notAfter: '2036-11-11T13:04:49Z' })
    expect(JSON.stringify({ ...choice, certificate: undefined })).not.toContain('85010112345')
  })

  it('only keeps signing certificates when the key usage is readable, and lets the server decide otherwise', async () => {
    const mixed = fakeWindow({
      getUserCertificates: () => Promise.resolve({ result: 'OK', Readers: [{ ReaderType: 'standard', certificates: [AUTH_CERT, SIGNING_CERT, 'QUFB'] }] }),
    })
    const kept = await createBeidConnectBridge('fr', mixed.win).listChoices()
    // Le certificat d'authentification (sans non-répudiation) est écarté ; l'illisible est gardé.
    expect(kept.map((c) => c.certificate.certificate)).toEqual([SIGNING_CERT, 'QUFB'])

    const onlyAuth = fakeWindow({
      getUserCertificates: () => Promise.resolve({ result: 'OK', Readers: [{ ReaderType: 'standard', certificates: [AUTH_CERT] }] }),
    })
    expect((await createBeidConnectBridge('fr', onlyAuth.win).listChoices()).map((c) => c.certificate.certificate)).toEqual([AUTH_CERT])
  })

  it('signs with the converted algorithm, the digest and the PIN of a standard reader', async () => {
    const { win, spies } = fakeWindow()
    const bridge = createBeidConnectBridge('en', win)
    const choice = await firstChoice(bridge)

    await expect(bridge.sign(choice, 'RElH', 'SHA-384', '1234')).resolves.toBe('U0lH')

    expect(spies.sign).toHaveBeenCalledWith('en', BEID_MAC, 'TEVBRg==', 'SHA384', 'RElH', '1234')
  })

  it('refuses to sign on a standard reader without a PIN, and never signs an unknown hash function', async () => {
    const { win, spies } = fakeWindow()
    const bridge = createBeidConnectBridge('fr', win)
    const choice = await firstChoice(bridge)
    expect((await rejection(bridge.sign(choice, 'RElH', 'SHA-256', null))).kind).toBe('failed')
    expect((await rejection(bridge.sign(choice, 'RElH', 'SHA-512' as never, '1234'))).kind).toBe('failed')
    expect(spies.sign).not.toHaveBeenCalled()
  })

  it('reports no reader and no card from the shape of the answer', async () => {
    const noReader = fakeWindow({ getUserCertificates: () => Promise.resolve({ result: 'OK', Readers: [] }) })
    expect((await rejection(createBeidConnectBridge('fr', noReader.win).listChoices())).kind).toBe('no-reader')
    const noCard = fakeWindow({
      getUserCertificates: () => Promise.resolve({ result: 'OK', Readers: [{ ReaderType: 'standard', certificates: [] }] }),
    })
    expect((await rejection(createBeidConnectBridge('fr', noCard.win).listChoices())).kind).toBe('no-card')
  })

  it.each([
    ['no_reader', 'no-reader', undefined],
    ['no_card', 'no-card', undefined],
    ['pin_incorrect', 'pin-incorrect', undefined],
    ['pin_too_short', 'pin-incorrect', undefined],
    ['pin_too_long', 'pin-incorrect', undefined],
    ['pin_length', 'pin-incorrect', undefined],
    ['pin_3_attempts_left', 'pin-incorrect', 3],
    ['pin_2_attempts_left', 'pin-incorrect', 2],
    ['pin_1_attempt_left', 'pin-incorrect', 1],
    ['card_blocked', 'card-blocked', undefined],
    ['pin_timeout', 'timeout', undefined],
    ['cancel', 'cancelled', undefined],
    ['timeout', 'unavailable', undefined],
    ['busy', 'failed', undefined],
    ['general_error', 'failed', undefined],
    ['card_error', 'failed', undefined],
    ['unsupported_reader', 'failed', undefined],
    ['signature_failed', 'failed', undefined],
    ['technical_error', 'failed', undefined],
    ['something_new', 'failed', undefined],
  ])('maps the rejection « %s » to « %s »', async (code, kind, attemptsLeft) => {
    const { win } = fakeWindow({ sign: () => Promise.reject({ message: code, result: code }) })
    const bridge = createBeidConnectBridge('fr', win)
    const choice = await firstChoice(bridge)
    const error = await rejection(bridge.sign(choice, 'RElH', 'SHA-256', '1234'))
    expect(error.kind).toBe(kind)
    expect(error.attemptsLeft).toBe(attemptsLeft)
    expect(error.bridgeCode).toBe(code)
    // Aucun PIN dans l'erreur normalisée.
    expect(JSON.stringify(error)).not.toContain('1234')
    expect(error.message).not.toContain('1234')
  })

  it('reads the code from `result` when the rejection carries no message, and a non-OK result as an error', async () => {
    const rejected = fakeWindow({ getVersion: () => Promise.reject({ result: 'no_reader' }) })
    expect((await rejection(createBeidConnectBridge('fr', rejected.win).detect())).kind).toBe('no-reader')
    const notOk = fakeWindow({ getVersion: () => Promise.resolve({ result: 'card_blocked' }) })
    expect((await rejection(createBeidConnectBridge('fr', notOk.win).detect())).kind).toBe('card-blocked')
  })

  it('fails cleanly on a success without signature', async () => {
    const { win } = fakeWindow({ sign: () => Promise.resolve({ result: 'OK' }) })
    const bridge = createBeidConnectBridge('fr', win)
    const choice = await firstChoice(bridge)
    expect((await rejection(bridge.sign(choice, 'RElH', 'SHA-256', '1234'))).kind).toBe('failed')
  })

  describe('delays (the extension has none)', () => {
    beforeEach(() => {
      vi.useFakeTimers()
    })
    afterEach(() => {
      vi.useRealTimers()
    })

    it('gives up after 20 seconds on a call that never answers', async () => {
      const { win } = fakeWindow({ getUserCertificates: () => new Promise(() => {}) })
      const outcome = rejection(createBeidConnectBridge('fr', win).listChoices())
      await vi.advanceTimersByTimeAsync(BEID_TIMEOUT_MS)
      expect((await outcome).kind).toBe('timeout')
    })

    it('waits 60 seconds for a signature on a pinpad reader, 20 seconds otherwise', async () => {
      const never = () => new Promise<never>(() => {})
      const pad = fakeWindow({
        getUserCertificates: () => Promise.resolve({ result: 'OK', Readers: [{ ReaderType: 'pinpad', certificates: ['TEVBRg=='] }] }),
        sign: never,
      })
      const padBridge = createBeidConnectBridge('fr', pad.win)
      const padChoice = await firstChoice(padBridge)
      const padSign = rejection(padBridge.sign(padChoice, 'RElH', 'SHA-256', null))
      await vi.advanceTimersByTimeAsync(BEID_TIMEOUT_MS)
      let settled = false
      void padSign.then(() => (settled = true))
      await vi.advanceTimersByTimeAsync(0)
      expect(settled).toBe(false)
      await vi.advanceTimersByTimeAsync(BEID_PINPAD_SIGN_TIMEOUT_MS - BEID_TIMEOUT_MS)
      expect((await padSign).kind).toBe('timeout')

      const standard = fakeWindow({ sign: never })
      const bridge = createBeidConnectBridge('fr', standard.win)
      const choice = await firstChoice(bridge)
      const sign = rejection(bridge.sign(choice, 'RElH', 'SHA-256', '1234'))
      await vi.advanceTimersByTimeAsync(BEID_TIMEOUT_MS)
      expect((await sign).kind).toBe('timeout')
    })
  })
})
