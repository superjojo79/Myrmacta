import { describe, expect, it, vi } from 'vitest'
import { takeLinkFromLocation, takeTokenFromLocation } from './token'

const TOKEN = 'a'.repeat(43)

describe('takeTokenFromLocation (V-PORTAL-1)', () => {
  it('reads the token from the fragment and removes it from the address bar', () => {
    const replaceState = vi.fn()
    const token = takeTokenFromLocation({ hash: `#${TOKEN}`, pathname: '/tenant-a/portal/', search: '' }, { replaceState })

    expect(token).toBe(TOKEN)
    expect(replaceState).toHaveBeenCalledWith(null, '', '/tenant-a/portal/')
  })

  it('returns null without touching history when there is no fragment', () => {
    const replaceState = vi.fn()
    expect(takeTokenFromLocation({ hash: '', pathname: '/portal/', search: '' }, { replaceState })).toBeNull()
    expect(replaceState).not.toHaveBeenCalled()
  })

  it('still clears a malformed fragment but rejects it', () => {
    const replaceState = vi.fn()
    expect(takeTokenFromLocation({ hash: '#<script>', pathname: '/portal/', search: '' }, { replaceState })).toBeNull()
    expect(replaceState).toHaveBeenCalled()
  })
})

describe('takeLinkFromLocation (S-VT-1)', () => {
  it('recognises the signature link and clears the fragment', () => {
    const replaceState = vi.fn()
    expect(
      takeLinkFromLocation({ hash: `#signature:${TOKEN}`, pathname: '/portal/', search: '' }, { replaceState }),
    ).toEqual({ kind: 'signature', token: TOKEN })
    expect(replaceState).toHaveBeenCalledWith(null, '', '/portal/')
  })

  it('keeps the task link behaviour', () => {
    expect(
      takeLinkFromLocation({ hash: `#${TOKEN}`, pathname: '/portal/', search: '' }, { replaceState: vi.fn() }),
    ).toEqual({ kind: 'task', token: TOKEN })
  })

  it('rejects a malformed signature token', () => {
    expect(
      takeLinkFromLocation({ hash: '#signature:<x>', pathname: '/portal/', search: '' }, { replaceState: vi.fn() }),
    ).toBeNull()
  })
})
