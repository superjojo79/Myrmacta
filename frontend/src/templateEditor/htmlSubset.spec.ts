import { describe, expect, it } from 'vitest'
import { analyzeTemplateHtml, conditionAttributes, eachAttribute, isSafeLiteral, parseCondition, parseEach, parseVariableExpression } from './htmlSubset'

describe('htmlSubset (TODO §84, LC4b)', () => {
  it('accepts the editor subset: text, formatting, tables, simple Thymeleaf', () => {
    for (const html of [
      '',
      '<p>Contenu</p>',
      '<h2 style="text-align: center;">Titre</h2><p><strong>a</strong> <em>b</em> <u>c</u></p><hr>',
      '<table><tbody><tr><td colspan="2"><p>x</p></td></tr></tbody></table>',
      '<p>Bonjour <span th:text="${client.nom}">x</span></p>',
      '<div th:if="${a.b}"><p>x</p></div><div th:unless="${a}"><p>y</p></div>',
      '<div th:if="${#strings.equals(client.type,\'PRO\')}"><p>x</p></div>',
      '<div th:each="l : ${lignes}"><p>x</p></div><table><tr th:each="l : ${lignes}"><td>x</td></tr></table>',
      '<div style="page-break-after: always"></div>',
    ]) {
      expect(analyzeTemplateHtml(html), html).toEqual({ compatible: true, reasons: [] })
    }
  })

  it('keeps anything the editor cannot write without loss in source mode, with the reasons', () => {
    const cases: Array<[string, string]> = [
      ['<style>p{color:red}</style><p>x</p>', 'balise <style>'],
      ['<p class="x">a</p>', 'attribut class'],
      ['<p style="color: red">a</p>', 'style'],
      ['<img src="logo.png">', 'balise <img>'],
      ['<a href="x">l</a>', 'balise <a>'],
      ['<!-- note --><p>x</p>', 'commentaire'],
      ['<p>[[${x}]]</p>', 'expression en ligne'],
      ['<p>Total ${x}</p>', 'expression ${…} dans le texte'],
      ['<span th:utext="${x}">a</span>', 'attribut th:utext'],
      ['<span th:text="${#strings.upperCase(x)}">a</span>', 'th:text'],
      ['<span th:text="${a} + 1">a</span>', 'th:text'],
      ['<div th:if="${a == 1}">x</div>', 'th:if'],
      ['<div th:if="${#strings.equals(a,\'x\' + b)}">x</div>', 'th:if'],
      ['<div th:each="x : ${a.b()}">x</div>', 'th:each'],
      ['<p th:if="${a}">x</p>', 'th:if'],
      ['<th:block th:each="x : ${y}">z</th:block>', 'balise <th:block>'],
      ['<html><body><p>x</p></body></html>', 'document complet'],
      ['<script>alert(1)</script>', 'balise <script>'],
      ['<p onclick="x()">a</p>', 'attribut onclick'],
    ]
    for (const [html, reason] of cases) {
      const result = analyzeTemplateHtml(html)
      expect(result.compatible, html).toBe(false)
      expect(result.reasons.join(' | '), html).toContain(reason)
    }
  })

  it('reads and writes the Thymeleaf constructions symmetrically', () => {
    expect(parseVariableExpression('${client.nom}')).toBe('client.nom')
    expect(parseVariableExpression('${a b}')).toBeNull()
    expect(parseVariableExpression('${#vars.x}')).toBeNull()
    expect(parseVariableExpression('client.nom')).toBeNull()
    expect(parseEach('l : ${lignes}')).toEqual({ item: 'l', path: 'lignes' })
    expect(parseEach('l:${lignes}')).toEqual({ item: 'l', path: 'lignes' })
    expect(parseEach('l : ${lignes.sorted()}')).toBeNull()
    expect(eachAttribute('l', 'lignes')).toBe('l : ${lignes}')
    for (const c of [
      { mode: 'if', path: 'a.b' } as const,
      { mode: 'unless', path: 'a' } as const,
      { mode: 'equals', path: 'client.type', value: 'PRO' } as const,
    ]) {
      const [attr, value] = Object.entries(conditionAttributes(c))[0]!
      expect(parseCondition(attr as 'th:if' | 'th:unless', value)).toEqual(c)
    }
    expect(parseCondition('th:unless', "${#strings.equals(a,'x')}")).toBeNull()
  })

  it('only accepts literals that need no escaping in an expression', () => {
    expect(isSafeLiteral('PRO')).toBe(true)
    expect(isSafeLiteral('Société Générale')).toBe(true)
    for (const bad of ["l'a", 'a\\b', 'a${b}', 'a{b}', 'a\nb', 'x'.repeat(201)]) {
      expect(isSafeLiteral(bad), bad).toBe(false)
    }
  })
})
