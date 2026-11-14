import { describe, expect, it, vi } from 'vitest'
import { detectCardBridge, mostRelevantError, type BridgeFactories } from './index'
import { CardBridgeError, type CardBridge, type CardBridgeId } from './types'

function fake(id: CardBridgeId, detect: () => Promise<void>): CardBridge {
  return { id, needsPin: false, detect: vi.fn(detect), readCertificate: vi.fn(), sign: vi.fn() }
}

function factories(web: () => Promise<void>, beid: () => Promise<void>) {
  const created: CardBridge[] = []
  const make = (id: CardBridgeId, detect: () => Promise<void>) => (lang: string) => {
    expect(lang).toBe('fr')
    const bridge = fake(id, detect)
    created.push(bridge)
    return bridge
  }
  const all: BridgeFactories = { 'web-eid': make('web-eid', web), beidconnect: make('beidconnect', beid) }
  return { all, created }
}

describe('card bridge facade', () => {
  it('returns the first available bridge in the order given by the server', async () => {
    const { all, created } = factories(() => Promise.resolve(), () => Promise.resolve())
    const result = await detectCardBridge(['beidconnect', 'web-eid'], 'fr', all)
    expect(result.bridge?.id).toBe('beidconnect')
    expect(created.map((b) => b.id)).toEqual(['beidconnect'])
  })

  it('falls back to the next bridge and keeps the reason the first was dropped', async () => {
    const { all } = factories(() => Promise.reject(new CardBridgeError('unavailable')), () => Promise.resolve())
    const result = await detectCardBridge(['web-eid', 'beidconnect'], 'fr', all)
    expect(result.bridge?.id).toBe('beidconnect')
    expect(result.errors.map((e) => e.kind)).toEqual(['unavailable'])
  })

  it('returns none when every allowed bridge fails, or when the list is empty', async () => {
    const { all } = factories(
      () => Promise.reject(new CardBridgeError('update-required')),
      () => Promise.reject(new Error('boom')),
    )
    const result = await detectCardBridge(['web-eid', 'beidconnect'], 'fr', all)
    expect(result.bridge).toBeNull()
    expect(result.errors.map((e) => e.kind)).toEqual(['update-required', 'failed'])
    expect(await detectCardBridge([], 'fr', all)).toEqual({ bridge: null, errors: [] })
  })

  it('ignores identifiers it cannot drive and bridges the server did not allow', async () => {
    const { all, created } = factories(() => Promise.resolve(), () => Promise.resolve())
    const result = await detectCardBridge(['unknown-bridge', 'constructor', 'beidconnect'], 'fr', all)
    expect(result.bridge?.id).toBe('beidconnect')
    expect(created.map((b) => b.id)).toEqual(['beidconnect'])
    expect((await detectCardBridge(['unknown-bridge'], 'fr', all)).bridge).toBeNull()
  })

  it('picks the most useful error to show when nothing answers', () => {
    const e = (kind: ConstructorParameters<typeof CardBridgeError>[0]) => new CardBridgeError(kind)
    expect(mostRelevantError([e('unavailable'), e('update-required'), e('insecure-context')]).kind).toBe('insecure-context')
    expect(mostRelevantError([e('unavailable'), e('update-required')]).kind).toBe('update-required')
    expect(mostRelevantError([e('unavailable'), e('failed')]).kind).toBe('unavailable')
    expect(mostRelevantError([]).kind).toBe('unavailable')
  })
})
