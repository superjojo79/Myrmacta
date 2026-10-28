import { existsSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'

/**
 * TODO §108 — racine du dépôt : le premier parent qui contient `pom.xml`. Un chemin relatif au répertoire courant
 * (`../src/…`) casse quand les tests tournent ailleurs, comme dans le bac à sable de Stryker (`.stryker-tmp/sandbox-*`).
 */
export function repoRoot(start: string = process.cwd()): string {
  for (let dir = resolve(start); ; dir = dirname(dir)) {
    if (existsSync(join(dir, 'pom.xml'))) {
      return dir
    }
    if (dirname(dir) === dir) {
      throw new Error(`no parent of ${start} holds pom.xml: run the tests from inside the repository`)
    }
  }
}

/** Fichier du dépôt désigné depuis sa racine, par exemple une fixture du backend partagée avec le frontend. */
export function repoFile(pathFromRoot: string): string {
  return join(repoRoot(), pathFromRoot)
}
