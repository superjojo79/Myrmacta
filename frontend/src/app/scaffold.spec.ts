import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

describe('frontend scaffold', () => {
  it('uses relative Vite base for embedded static packaging', () => {
    const config = readFileSync(join(process.cwd(), 'vite.config.ts'), 'utf8')
    expect(config).toMatch(/base:\s*['"]\.\/['"]/)
  })
})
