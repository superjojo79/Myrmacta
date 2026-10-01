import { readFileSync } from 'node:fs'
import { afterEach, describe, expect, it } from 'vitest'
import { Editor } from '@tiptap/core'
import { templateExtensions } from './extensions'
import { isVariablePath } from './variableCatalog'
import { analyzeTemplateHtml, conditionAttributes, eachAttribute, isSafeLiteral, parseCondition, parseEach, parseVariableExpression } from './htmlSubset'

let probe: Editor | null = null
/** Ce que l'éditeur écrirait pour ce HTML (même chemin que le composant). */
function viaEditor(html: string): string {
  probe = new Editor({ extensions: templateExtensions(), content: html })
  return probe.getHTML()
}
afterEach(() => {
  probe?.destroy()
  probe = null
})

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
  // ---- Revue adversariale LC4b : un template jugé compatible doit être restitué sans perte ----
  it('refuses combinations of th:if / th:unless / th:each / style on one element (High)', () => {
    for (const html of [
      '<div th:if="${a}" th:each="x : ${l}"><p>t</p></div>',
      '<div th:if="${a}" th:unless="${b}"><p>t</p></div>',
      '<div th:if="${a}" style="page-break-after: always"><p>t</p></div>',
    ]) {
      expect(analyzeTemplateHtml(html, viaEditor).compatible, html).toBe(false)
    }
  })

  it('refuses what the editor would flatten or drop: thead, aligned li (Medium)', () => {
    for (const html of [
      '<table><thead><tr><th>A</th></tr></thead><tbody><tr><td>x</td></tr></tbody></table>',
      '<ul><li style="text-align:center">a</li></ul>',
    ]) {
      expect(analyzeTemplateHtml(html, viaEditor).compatible, html).toBe(false)
    }
  })

  it('accepts what the editor rewrites without any loss (bare td, plain div/span, top-level text)', () => {
    for (const html of ['<table><tbody><tr><td>a</td></tr></tbody></table>', '<div><span>a</span></div>', 'Texte nu']) {
      expect(analyzeTemplateHtml(html, viaEditor), html).toEqual({ compatible: true, reasons: [] })
    }
  })

  it('looks for inline expressions in the decoded text, not the raw HTML (Medium)', () => {
    for (const html of [
      '<p>&#91;&#91;${a}&#93;&#93;</p>',
      "<p>l'article ${x}</p>",
      '<p>[( ${a} )]</p>',
    ]) {
      expect(analyzeTemplateHtml(html).compatible, html).toBe(false)
    }
  })

  it('the shared fixture is faithfully restituted by the editor itself', () => {
    const fixture = readFileSync('../src/test/resources/template-editor/editor-output.html', 'utf-8').trim()
    expect(analyzeTemplateHtml(fixture, viaEditor)).toEqual({ compatible: true, reasons: [] })
  })

  it('accepts accented identifiers like the engine, and refuses the properties the engine blocks (Low)', () => {
    expect(isVariablePath('client.prénom')).toBe(true)
    expect(parseVariableExpression('${client.prénom}')).toBe('client.prénom')
    expect(isVariablePath('client.class')).toBe(false)
    expect(isVariablePath('client.getClass')).toBe(false)
    expect(parseEach('ligne : ${lignes}')).toEqual({ item: 'ligne', path: 'lignes' })
    expect(parseEach('élément : ${lignes}')).toEqual({ item: 'élément', path: 'lignes' })
    expect(parseEach('a b : ${lignes}')).toBeNull()
  })

  // ---- Revue adversariale LC4b, passe 2 ----
  it('opens a template indented over several lines (whitespace between blocks is not a loss)', () => {
    for (const html of ['<p>a</p>\n<p>b</p>', '<h1>T</h1>\n  <p>b</p>\n', '<p><strong>a</strong> <em>b</em></p>']) {
      expect(analyzeTemplateHtml(html, viaEditor), html).toEqual({ compatible: true, reasons: [] })
    }
  })

  it('refuses a table row or cell outside a table: the browser would drop it with its th:each (Medium)', () => {
    for (const html of ['<tr th:each="l : ${a}"><td>x</td></tr>', '<td th:each="l : ${a}">x</td>', '<td style="text-align:right">x</td>']) {
      expect(analyzeTemplateHtml(html, viaEditor).compatible, html).toBe(false)
    }
    expect(analyzeTemplateHtml('<table><tbody><tr th:each="l : ${a}"><td>x</td></tr></tbody></table>', viaEditor).compatible).toBe(true)
  })

  it('finds an inline expression split by a plain span (Low)', () => {
    expect(analyzeTemplateHtml('<p>[<span>[</span>x]]</p>').compatible).toBe(false)
  })
})
