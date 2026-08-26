import { describe, expect, it } from 'vitest'
import { toSlaPeriodParam } from './slaPeriod'

describe('toSlaPeriodParam', () => {
  it('omits blank values so the API applies the 30-day default', () => {
    expect(toSlaPeriodParam('')).toBeUndefined()
    expect(toSlaPeriodParam('   ')).toBeUndefined()
    expect(toSlaPeriodParam(undefined)).toBeUndefined()
  })

  it('keeps civil YYYY-MM-DD for exclusive UTC midnight parsing', () => {
    expect(toSlaPeriodParam('2026-03-01')).toBe('2026-03-01')
  })

  it('converts datetime-local to a UTC Instant (seconds + Z)', () => {
    expect(toSlaPeriodParam('2026-03-01T00:00')).toBe('2026-03-01T00:00:00Z')
    expect(toSlaPeriodParam('2026-03-15T23:59')).toBe('2026-03-15T23:59:00Z')
    expect(toSlaPeriodParam('2026-03-15T23:59:01')).toBe('2026-03-15T23:59:01Z')
  })

  it('passes Instant ISO through', () => {
    expect(toSlaPeriodParam('2026-03-01T00:00:00Z')).toBe('2026-03-01T00:00:00Z')
  })
})
