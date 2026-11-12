import { describe, expect, it } from 'vitest'
import { DMN_11_NAMESPACE, DMN_13_NAMESPACE, toDmn13 } from './dmnXmlVersions'

describe('toDmn13', () => {
  it('replaces the DMN 1.1 xmlns with DMN 1.3', () => {
    const xml = `<definitions xmlns="${DMN_11_NAMESPACE}" id="d"><decision id="x" name="X"/></definitions>`

    expect(toDmn13(xml)).toBe(
      `<definitions xmlns="${DMN_13_NAMESPACE}" id="d"><decision id="x" name="X"/></definitions>`,
    )
  })

  it('leaves already-1.3 XML without a leading comment unchanged', () => {
    const xml = `<definitions xmlns="${DMN_13_NAMESPACE}" id="d"/>`
    expect(toDmn13(xml)).toBe(xml)
  })

  it('passes through empty input', () => {
    expect(toDmn13('')).toBe('')
  })

  it('strips leading XML comments when converting 1.1', () => {
    const xml = `<!-- note -->\n<definitions xmlns="${DMN_11_NAMESPACE}" id="d"/>`
    expect(toDmn13(xml)).toBe(`\n<definitions xmlns="${DMN_13_NAMESPACE}" id="d"/>`)
  })

  it('does not rewrite the 1.1 URI inside a cell', () => {
    const xml = `<definitions xmlns="${DMN_11_NAMESPACE}" id="d"><decision id="x"><decisionTable id="t"><input id="i"><inputExpression id="e" typeRef="string"><text>v</text></inputExpression></input><output id="o" name="y" typeRef="string"/><rule id="r"><inputEntry id="ri"><text>"${DMN_11_NAMESPACE}"</text></inputEntry><outputEntry id="ro"><text>"ok"</text></outputEntry></rule></decisionTable></decision></definitions>`
    expect(toDmn13(xml)).toContain(`"${DMN_11_NAMESPACE}"`)
    expect(toDmn13(xml)).toContain(`xmlns="${DMN_13_NAMESPACE}"`)
  })

  it('handles many unclosed comment openers without hanging', () => {
    const hostile = `${'<!--'.repeat(20_000)}<definitions xmlns="${DMN_11_NAMESPACE}" id="d"/>`
    const start = performance.now()
    const result = toDmn13(hostile)
    expect(performance.now() - start).toBeLessThan(500)
    expect(result).toContain('definitions')
  })
})
