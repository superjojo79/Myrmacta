import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { CardBridgeError, type CardChoice } from './types'
import {
  createWebEidBridge,
  WEB_EID_ACK_TIMEOUT_MS,
  WEB_EID_NATIVE_TIMEOUT_MS,
  WEB_EID_USER_TIMEOUT_MS,
  windowTransport,
  type WebEidTransport,
} from './webEid'

type Message = Record<string, unknown>

/** Simulateur de l'extension : répond à chaque requête par les messages que `respond` rend (aucun : extension muette). */
function simulator(respond: (request: Message) => Message[], secure = true) {
  const handlers = new Set<(data: Message) => void>()
  const sent: Message[] = []
  const transport: WebEidTransport = {
    send(message) {
      sent.push(message)
      // Comme `window.postMessage`, la requête repasse par les écouteurs de la page.
      queueMicrotask(() => {
        handlers.forEach((h) => h(message))
        for (const reply of respond(message)) handlers.forEach((h) => h(reply))
      })
    },
    listen(handler) {
      handlers.add(handler)
      return () => handlers.delete(handler)
    },
    isSecureContext: () => secure,
  }
  return { transport, sent, listeners: () => handlers.size }
}

const choiceOf = (certificate: string): CardChoice => ({ id: '0:0', certificate: { certificate, certificateChain: [] }, readerName: null, holder: null, notAfter: null, needsPin: false })

const ack = (action: string): Message => ({ action: `web-eid:${action}-ack` })
const failure = (action: string, code: string): Message => ({ action: `web-eid:${action}-failure`, error: { code, message: 'x' } })

async function rejection(promise: Promise<unknown>): Promise<CardBridgeError> {
  const e = await promise.then(
    () => null,
    (err: unknown) => err,
  )
  expect(e).toBeInstanceOf(CardBridgeError)
  return e as CardBridgeError
}

describe('Web eID bridge (protocole web-eid.js 2.1.0)', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('checks the extension with a status request carrying the library version', async () => {
    const sim = simulator((r) => [ack('status'), { action: 'web-eid:status-success', library: '2.1.0', extension: '2.1.0', nativeApp: '2.1.0' }].filter(() => r.action === 'web-eid:status'))
    await createWebEidBridge('fr', sim.transport).detect()

    expect(sim.sent).toEqual([{ action: 'web-eid:status', libraryVersion: '2.1.0' }])
    expect(sim.listeners()).toBe(0)
  })

  it('reads the signing certificate (leaf only, no chain) with a two-letter language', async () => {
    const sim = simulator(() => [
      ack('get-signing-certificate'),
      { action: 'web-eid:get-signing-certificate-success', certificate: 'TEVBRg==', supportedSignatureAlgorithms: [] },
    ])
    const bridge = createWebEidBridge('FR-BE', sim.transport)

    // Un seul choix : le logiciel natif de Web eID affiche lui-même le choix du lecteur et du certificat.
    await expect(bridge.listChoices()).resolves.toEqual([choiceOf('TEVBRg==')])

    expect(sim.sent[0]).toEqual({ action: 'web-eid:get-signing-certificate', libraryVersion: '2.1.0', options: { lang: 'fr' } })
  })

  it('signs with the exact certificate string, the base64 hash and the hyphenated hash function', async () => {
    const sim = simulator(() => [ack('sign'), { action: 'web-eid:sign-success', signature: 'U0lH', signatureAlgorithm: {} }])
    const bridge = createWebEidBridge('en', sim.transport)

    await expect(bridge.sign(choiceOf('TEVBRg=='), 'RElHRVNU', 'SHA-384', null)).resolves.toBe('U0lH')

    expect(sim.sent[0]).toEqual({
      action: 'web-eid:sign',
      libraryVersion: '2.1.0',
      certificate: 'TEVBRg==',
      hash: 'RElHRVNU',
      hashFunction: 'SHA-384',
      options: { lang: 'en' },
    })
  })

  it('ignores its own request and answers of other actions', async () => {
    const sim = simulator(() => [
      { action: 'web-eid:sign-success', signature: 'WRONG' },
      ack('status'),
      { action: 'web-eid:status-success' },
    ])
    await expect(createWebEidBridge('fr', sim.transport).detect()).resolves.toBeUndefined()
  })

  it.each([
    ['ERR_WEBEID_EXTENSION_UNAVAILABLE', 'unavailable'],
    ['ERR_WEBEID_NATIVE_UNAVAILABLE', 'unavailable'],
    ['ERR_WEBEID_VERSION_MISMATCH', 'update-required'],
    ['ERR_WEBEID_VERSION_INVALID', 'update-required'],
    ['ERR_WEBEID_USER_CANCELLED', 'cancelled'],
    ['ERR_WEBEID_USER_TIMEOUT', 'timeout'],
    ['ERR_WEBEID_ACTION_TIMEOUT', 'timeout'],
    ['ERR_WEBEID_CONTEXT_INSECURE', 'insecure-context'],
    ['ERR_WEBEID_NATIVE_INVALID_ARGUMENT', 'failed'],
    ['ERR_WEBEID_NATIVE_FATAL', 'failed'],
    ['ERR_WEBEID_ACTION_PENDING', 'failed'],
    ['ERR_WEBEID_MISSING_PARAMETER', 'failed'],
    ['ERR_WEBEID_UNKNOWN_ERROR', 'failed'],
    ['ERR_SOMETHING_NEW', 'failed'],
  ])('maps %s to « %s »', async (code, kind) => {
    const sim = simulator(() => [ack('sign'), failure('sign', code)])
    const error = await rejection(createWebEidBridge('fr', sim.transport).sign(choiceOf('c'), 'h', 'SHA-256', null))
    expect(error.kind).toBe(kind)
    expect(error.bridgeCode).toBe(code)
  })

  it('refuses an insecure context without sending anything', async () => {
    const sim = simulator(() => [], false)
    const error = await rejection(createWebEidBridge('fr', sim.transport).detect())
    expect(error.kind).toBe('insecure-context')
    expect(sim.sent).toHaveLength(0)
  })

  it('reports an absent extension when no acknowledgement arrives within one second', async () => {
    const sim = simulator(() => [])
    const outcome = rejection(createWebEidBridge('fr', sim.transport).detect())
    await vi.advanceTimersByTimeAsync(WEB_EID_ACK_TIMEOUT_MS)
    expect((await outcome).kind).toBe('unavailable')
    expect(sim.listeners()).toBe(0)
  })

  it('treats a silent native application as unavailable after five seconds for the status request', async () => {
    const sim = simulator(() => [ack('status')])
    const outcome = rejection(createWebEidBridge('fr', sim.transport).detect())
    await vi.advanceTimersByTimeAsync(WEB_EID_NATIVE_TIMEOUT_MS - 1)
    await vi.advanceTimersByTimeAsync(1)
    expect((await outcome).kind).toBe('unavailable')
  })

  it('waits two minutes for the user before a timeout on signing and certificate reading', async () => {
    const sim = simulator((r) => [ack(String(r.action).replace('web-eid:', ''))])
    const bridge = createWebEidBridge('fr', sim.transport)
    const reading = rejection(bridge.listChoices())
    await vi.advanceTimersByTimeAsync(WEB_EID_USER_TIMEOUT_MS - 1)
    let settled = false
    void reading.then(() => (settled = true))
    await vi.advanceTimersByTimeAsync(0)
    expect(settled).toBe(false)
    await vi.advanceTimersByTimeAsync(1)
    expect((await reading).kind).toBe('timeout')

    const signing = rejection(bridge.sign(choiceOf('c'), 'h', 'SHA-256', null))
    await vi.advanceTimersByTimeAsync(WEB_EID_USER_TIMEOUT_MS)
    expect((await signing).kind).toBe('timeout')
  })

  it('allows only one action of the same type at a time', async () => {
    const sim = simulator((r) => (r.action === 'web-eid:sign' ? [ack('sign')] : []))
    const bridge = createWebEidBridge('fr', sim.transport)
    const first = rejection(bridge.sign(choiceOf('c'), 'h', 'SHA-256', null))
    await vi.advanceTimersByTimeAsync(0)

    const second = await rejection(bridge.sign(choiceOf('c'), 'h', 'SHA-256', null))
    expect(second).toMatchObject({ kind: 'failed', bridgeCode: 'ERR_WEBEID_ACTION_PENDING' })

    await vi.advanceTimersByTimeAsync(WEB_EID_USER_TIMEOUT_MS)
    await first
  })

  it('fails cleanly on a success without certificate or signature', async () => {
    const certificate = simulator(() => [ack('get-signing-certificate'), { action: 'web-eid:get-signing-certificate-success' }])
    expect((await rejection(createWebEidBridge('fr', certificate.transport).listChoices())).kind).toBe('failed')
    const signature = simulator(() => [ack('sign'), { action: 'web-eid:sign-success' }])
    expect((await rejection(createWebEidBridge('fr', signature.transport).sign(choiceOf('c'), 'h', 'SHA-256', null))).kind).toBe('failed')
  })
})

describe('windowTransport', () => {
  it('only accepts web-eid messages posted by the page itself', () => {
    const received: unknown[] = []
    const transport = windowTransport(window)
    const stop = transport.listen((data) => received.push(data))
    const post = (data: unknown, source: MessageEventSource | null) =>
      window.dispatchEvent(new MessageEvent('message', { data, source }))

    post({ action: 'web-eid:status-ack' }, window)
    post({ action: 'web-eid:status-ack', from: 'iframe' }, null)
    post({ action: 'something-else' }, window)
    post('web-eid:status-ack', window)
    stop()
    post({ action: 'web-eid:status-success' }, window)

    expect(received).toEqual([{ action: 'web-eid:status-ack' }])
  })

  it('posts to the page with any target origin, as the reference library does', () => {
    const spy = vi.spyOn(window, 'postMessage').mockImplementation(() => {})
    windowTransport(window).send({ action: 'web-eid:status' })
    expect(spy).toHaveBeenCalledWith({ action: 'web-eid:status' }, '*')
    spy.mockRestore()
  })
})
