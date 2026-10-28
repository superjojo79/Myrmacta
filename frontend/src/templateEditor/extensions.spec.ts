import { readFileSync } from 'node:fs'
import { afterEach, describe, expect, it } from 'vitest'
import { Editor } from '@tiptap/core'
import { templateExtensions } from './extensions'
import { analyzeTemplateHtml } from './htmlSubset'
import { repoFile } from '../test/repoRoot'

let editor: Editor | null = null

function roundTrip(html: string): string {
  editor = new Editor({ extensions: templateExtensions(), content: html })
  return editor.getHTML()
}

afterEach(() => {
  editor?.destroy()
  editor = null
})

describe('template editor extensions (TODO §84, LC4b)', () => {
  it('writes a variable as a simple th:text span and reads it back', () => {
    const html = '<p>Bonjour <span th:text="${client.nom}">«client.nom»</span>,</p>'
    expect(roundTrip(html)).toBe(html)
  })

  it('round-trips conditional blocks in their three forms', () => {
    for (const html of [
      '<div th:if="${client.pro}"><p>Pro</p></div>',
      '<div th:unless="${client.pro}"><p>Particulier</p></div>',
      '<div th:if="${#strings.equals(client.type,\'PRO\')}"><p>Pro</p></div>',
    ]) {
      expect(roundTrip(html)).toBe(html)
    }
  })

  it('round-trips repeated blocks and repeatable table rows', () => {
    const block = '<div th:each="l : ${lignes}"><p><span th:text="${l.designation}">«l.designation»</span></p></div>'
    expect(roundTrip(block)).toBe(block)
    const table =
      '<table><tbody><tr><th><p>Désignation</p></th></tr><tr th:each="l : ${lignes}"><td><p><span th:text="${l.designation}">«l.designation»</span></p></td></tr></tbody></table>'
    expect(roundTrip(table)).toBe(table)
  })

  it('round-trips formatting, alignment, lists, rules and page breaks inside the supported subset', () => {
    const html =
      '<h1 style="text-align: center;">Titre</h1><p><strong>gras</strong> <em>italique</em> <u>souligné</u></p>' +
      '<ul><li><p>a</p></li><li><p>b</p></li></ul><ol><li><p>un</p></li></ol><hr>' +
      '<div style="page-break-after: always;"></div><p style="text-align: right;">droite</p>'
    expect(roundTrip(html)).toBe(html)
  })

  it('emits no service attribute, class or inline style beyond the subset (what the editor writes, it can read again)', () => {
    const html = roundTrip(
      '<table><tbody><tr><td colspan="2"><p>x</p></td></tr></tbody></table><p><span th:text="${a}">«a»</span></p>',
    )
    expect(html).not.toMatch(/class=|data-|min-width|colgroup/)
    expect(analyzeTemplateHtml(html).compatible).toBe(true)
  })

  it('does not turn a non-simple th:text into a variable (the analyzer keeps such templates in source mode)', () => {
    const advanced = '<p><span th:text="${#strings.upperCase(nom)}">x</span></p>'
    expect(analyzeTemplateHtml(advanced).compatible).toBe(false)
    expect(roundTrip(advanced)).not.toContain('th:text="${#strings')
  })

  it('has no link, code, quote or strike extension (outside the subset)', () => {
    editor = new Editor({ extensions: templateExtensions(), content: '<p>x</p>' })
    for (const name of ['link', 'code', 'codeBlock', 'blockquote', 'strike']) {
      expect(editor.extensionManager.extensions.map((e) => e.name), name).not.toContain(name)
    }
  })

  it('reads and rewrites identically the canonical output shared with the backend allowlist test', () => {
    // même fichier que TemplateEditorOutputRenderingTest : ce que l'éditeur écrit est accepté et rendu par le vrai moteur
    const fixture = readFileSync(repoFile('src/test/resources/template-editor/editor-output.html'), 'utf8').trim()
    expect(analyzeTemplateHtml(fixture)).toEqual({ compatible: true, reasons: [] })
    expect(roundTrip(fixture)).toBe(fixture)
  })
})
