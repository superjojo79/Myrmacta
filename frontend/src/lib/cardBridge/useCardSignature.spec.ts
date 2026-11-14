import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h, watch } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { cardErrorMessage, noBridgeMessage, useCardSignature, type CardPhase, type CardSignatureOptions } from './useCardSignature'
import { CardBridgeError, type BridgeFactories, type CardBridge, type CardPrepareResponse } from './types'

const PREPARED: CardPrepareResponse = { digest: 'RElH', hashFunction: 'SHA-256', signingTime: '2026-11-14T10:00:00Z', documentDigest: 'abc' }

function setup(overrides: Partial<CardSignatureOptions> = {}, bridge: Partial<CardBridge> = {}) {
  const calls: string[] = []
  const fake: CardBridge = {
    id: 'beidconnect',
    needsPin: true,
    detect: () => Promise.resolve(),
    readCertificate: vi.fn(() => {
      calls.push('read')
      return Promise.resolve({ certificate: 'Q0VSVA==', certificateChain: ['SU5U'] })
    }),
    sign: vi.fn(() => {
      calls.push('sign')
      return Promise.resolve('U0lH')
    }),
    ...bridge,
  }
  const factories: BridgeFactories = { 'web-eid': () => fake, beidconnect: () => fake }
  const phases: CardPhase[] = []
  const options: CardSignatureOptions = {
    bridges: () => ['beidconnect'],
    lang: () => 'fr-BE',
    prepare: vi.fn(() => {
      calls.push('prepare')
      return Promise.resolve(PREPARED)
    }),
    complete: vi.fn(() => {
      calls.push('complete')
      return Promise.resolve({ status: 'SIGNED' })
    }),
    onSigned: vi.fn(() => {
      calls.push('signed')
    }),
    onError: vi.fn(),
    onNoBridge: vi.fn(),
    factories,
    ...overrides,
  }
  let card!: ReturnType<typeof useCardSignature>
  mount(
    defineComponent({
      setup() {
        card = useCardSignature(options)
        return () => h('div')
      },
    }),
  )
  return { card, options, fake, calls, phases }
}

describe('useCardSignature', () => {
  it('chains detection, certificate, prepare, PIN, signature and complete, and keeps the PIN out of the server exchanges', async () => {
    const { card, options, fake, calls } = setup()

    const running = card.start()
    await flushPromises()
    expect(card.phase.value).toBe('pin')
    expect(calls).toEqual(['read', 'prepare'])

    card.pin.value = '12'
    card.submitPin()
    expect(card.phase.value).toBe('pin') // trop court : rien ne part

    card.pin.value = '123456'
    card.submitPin()
    expect(card.pin.value).toBe('')
    await running

    expect(calls).toEqual(['read', 'prepare', 'sign', 'complete', 'signed'])
    expect(options.prepare).toHaveBeenCalledWith({ certificate: 'Q0VSVA==', certificateChain: ['SU5U'] })
    expect(fake.sign).toHaveBeenCalledWith({ certificate: 'Q0VSVA==', certificateChain: ['SU5U'] }, 'RElH', 'SHA-256', '123456')
    expect(options.complete).toHaveBeenCalledWith({
      certificate: 'Q0VSVA==',
      certificateChain: ['SU5U'],
      signingTime: PREPARED.signingTime,
      documentDigest: 'abc',
      signature: 'U0lH',
    })
    expect(JSON.stringify(vi.mocked(options.prepare).mock.calls)).not.toContain('123456')
    expect(JSON.stringify(vi.mocked(options.complete).mock.calls)).not.toContain('123456')
    expect(options.onError).not.toHaveBeenCalled()
    expect(card.phase.value).toBe('idle')
    expect(card.pin.value).toBe('')
  })

  it('goes through the visible phases in order', async () => {
    const seen: CardPhase[] = []
    let resolveRead!: () => void
    const { card } = setup(
      {},
      {
        needsPin: false,
        readCertificate: () => new Promise((r) => (resolveRead = () => r({ certificate: 'c', certificateChain: [] }))),
        sign: () => new Promise((r) => setTimeout(() => r('S'), 0)),
      },
    )
    watch(card.phase, (value) => seen.push(value), { flush: 'sync' })
    const running = card.start()
    await flushPromises()
    expect(card.phase.value).toBe('reading')
    resolveRead()
    await running
    expect(seen).toEqual(['detecting', 'reading', 'preparing', 'signing', 'completing', 'idle'])
  })

  it('does not ask for a PIN when the bridge does not need one', async () => {
    const { card, fake } = setup({}, { needsPin: false })
    await card.start()
    expect(fake.sign).toHaveBeenCalledWith(expect.anything(), 'RElH', 'SHA-256', null)
  })

  it('stops with « cancelled » when the PIN prompt is cancelled, without signing', async () => {
    const { card, options, fake } = setup()
    const running = card.start()
    await flushPromises()
    card.pin.value = '1234'
    card.cancelPin()
    await running

    expect(card.pin.value).toBe('')
    expect(fake.sign).not.toHaveBeenCalled()
    expect(vi.mocked(options.onError).mock.calls[0]![0]).toMatchObject({ kind: 'cancelled' })
    expect(card.phase.value).toBe('idle')
  })

  it('clears the PIN and reports the error when the signature fails', async () => {
    const { card, options } = setup({}, { sign: () => Promise.reject(new CardBridgeError('pin-incorrect', { attemptsLeft: 2 })) })
    const running = card.start()
    await flushPromises()
    card.pin.value = '9999'
    card.submitPin()
    await running

    expect(card.pin.value).toBe('')
    expect(options.complete).not.toHaveBeenCalled()
    expect(vi.mocked(options.onError).mock.calls[0]![0]).toMatchObject({ kind: 'pin-incorrect', attemptsLeft: 2 })
    expect(JSON.stringify(vi.mocked(options.onError).mock.calls)).not.toContain('9999')
  })

  it('can start again after a failure', async () => {
    let fail = true
    const { card, options } = setup({}, {
      needsPin: false,
      readCertificate: () => (fail ? Promise.reject(new CardBridgeError('no-card')) : Promise.resolve({ certificate: 'c', certificateChain: [] })),
    })
    await card.start()
    expect(options.onSigned).not.toHaveBeenCalled()
    fail = false
    await card.start()
    expect(options.onSigned).toHaveBeenCalledTimes(1)
  })

  it('reports no bridge, with the most relevant reason, and goes no further', async () => {
    const factories: BridgeFactories = {
      'web-eid': () => ({ id: 'web-eid', needsPin: false, detect: () => Promise.reject(new CardBridgeError('insecure-context')), readCertificate: vi.fn(), sign: vi.fn() }),
      beidconnect: () => ({ id: 'beidconnect', needsPin: false, detect: () => Promise.reject(new CardBridgeError('unavailable')), readCertificate: vi.fn(), sign: vi.fn() }),
    }
    const { card, options } = setup({ factories, bridges: () => ['web-eid', 'beidconnect'] })
    await card.start()

    expect(vi.mocked(options.onNoBridge).mock.calls[0]![0].kind).toBe('insecure-context')
    expect(options.prepare).not.toHaveBeenCalled()
    expect(options.onError).not.toHaveBeenCalled()
  })

  it('hands API errors to the view and does not sign after a refused preparation', async () => {
    const apiError = { response: { status: 422, data: { code: 'STALE_DOCUMENT' } } }
    const { card, options, fake } = setup({ prepare: vi.fn(() => Promise.reject(apiError)) })
    await card.start()
    expect(options.onError).toHaveBeenCalledWith(apiError)
    expect(fake.sign).not.toHaveBeenCalled()
  })

  it('fails cleanly on a hash function it cannot sign', async () => {
    const { card, options, fake } = setup({ prepare: vi.fn(() => Promise.resolve({ ...PREPARED, hashFunction: 'SHA-1' as never })) })
    await card.start()
    expect(vi.mocked(options.onError).mock.calls[0]![0]).toMatchObject({ kind: 'failed' })
    expect(fake.sign).not.toHaveBeenCalled()
  })

  it('ignores a second start while one is running and releases a pending PIN prompt on unmount', async () => {
    const { card, options } = setup()
    const first = card.start()
    await flushPromises()
    await card.start()
    expect(options.prepare).toHaveBeenCalledTimes(1)
    card.cancelPin()
    await first
  })
})

describe('card error messages', () => {
  it('gives one message key per category, with the remaining attempts when known', () => {
    const key = (kind: ConstructorParameters<typeof CardBridgeError>[0], attemptsLeft?: number) =>
      cardErrorMessage('p.card', new CardBridgeError(kind, { attemptsLeft }))
    expect(key('unavailable')).toEqual({ key: 'p.card.errors.unavailable' })
    expect(key('update-required')).toEqual({ key: 'p.card.errors.updateRequired' })
    expect(key('no-reader')).toEqual({ key: 'p.card.errors.noReader' })
    expect(key('no-card')).toEqual({ key: 'p.card.errors.noCard' })
    expect(key('cancelled')).toEqual({ key: 'p.card.errors.cancelled' })
    expect(key('timeout')).toEqual({ key: 'p.card.errors.timeout' })
    expect(key('card-blocked')).toEqual({ key: 'p.card.errors.cardBlocked' })
    expect(key('insecure-context')).toEqual({ key: 'p.card.errors.insecureContext' })
    expect(key('failed')).toEqual({ key: 'p.card.errors.failed' })
    expect(key('pin-incorrect')).toEqual({ key: 'p.card.errors.pinIncorrect' })
    expect(key('pin-incorrect', 1)).toEqual({ key: 'p.card.errors.pinIncorrectLeft', named: { count: 1 } })
  })

  it('says « no software detected » only when nothing answered', () => {
    expect(noBridgeMessage('p.card', new CardBridgeError('unavailable'))).toEqual({ key: 'p.card.noBridge' })
    expect(noBridgeMessage('p.card', new CardBridgeError('update-required'))).toEqual({ key: 'p.card.errors.updateRequired' })
  })
})
