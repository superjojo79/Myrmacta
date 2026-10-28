import { existsSync, mkdirSync, mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { repoFile, repoRoot } from './repoRoot'

/** TODO §108 — fixtures du backend lues depuis la racine du dépôt, y compris dans le bac à sable de Stryker. */
describe('repoRoot', () => {
  it('finds the repository root, the first parent holding pom.xml, from the frontend', () => {
    const root = repoRoot()
    expect(existsSync(join(root, 'pom.xml'))).toBe(true)
    expect(existsSync(join(root, 'frontend', 'package.json'))).toBe(true)
  })

  it('finds the same root from a nested directory such as a Stryker sandbox', () => {
    expect(repoRoot(join(repoRoot(), 'frontend', '.stryker-tmp', 'sandbox-1'))).toBe(repoRoot())
  })

  it('resolves a backend fixture shared with the frontend tests', () => {
    expect(existsSync(repoFile('src/test/resources/template-editor/editor-output.html'))).toBe(true)
  })

  it('fails with an explicit message when no parent holds pom.xml', () => {
    const outside = mkdtempSync(join(tmpdir(), 'no-repo-'))
    mkdirSync(join(outside, 'nested'))
    expect(() => repoRoot(join(outside, 'nested'))).toThrow(/pom\.xml/)
  })
})
